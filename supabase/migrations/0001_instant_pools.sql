-- carpoolr: instant pooling schema + matching.
-- A rider says "pickup -> drop, now". find_pool() matches them with riders who requested
-- in the last 15 minutes, start within 1.5 km, and whose trips run along the same line
-- (the shorter trip's drop lies within 2 km of the longer trip's straight line).
-- Straight lines stand in for roads in v1.

create extension if not exists postgis with schema extensions;

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  created_at timestamptz not null default now()
);

create type public.request_status as enum ('searching', 'matched', 'cancelled', 'expired');
create type public.pool_status as enum ('forming', 'booked', 'done', 'cancelled');

create table public.pools (
  id uuid primary key default gen_random_uuid(),
  status public.pool_status not null default 'forming',
  provider text check (provider in ('uber', 'ola', 'rapido')),
  fare_rupees integer check (fare_rupees >= 0),
  created_at timestamptz not null default now()
);

create table public.pool_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  pickup extensions.geography(point, 4326) not null,
  pickup_label text not null,
  dropoff extensions.geography(point, 4326) not null,
  dropoff_label text not null,
  trip_km double precision generated always as (extensions.st_distance(pickup, dropoff) / 1000.0) stored,
  status public.request_status not null default 'searching',
  pool_id uuid references public.pools (id) on delete set null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '15 minutes'
);

create index pool_requests_searching_idx on public.pool_requests using gist (pickup) where status = 'searching';
create index pool_requests_user_idx on public.pool_requests (user_id, created_at desc);
create index pool_requests_pool_idx on public.pool_requests (pool_id);

-- ---------- access ----------
alter table public.profiles enable row level security;
alter table public.pools enable row level security;
alter table public.pool_requests enable row level security;

create or replace function public.is_pool_member(p_pool uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from pool_requests where pool_id = p_pool and user_id = auth.uid());
$$;

create policy "own profile" on public.profiles for all
  using (id = auth.uid()) with check (id = auth.uid());
create policy "pool members see each other's profile" on public.profiles for select
  using (exists (select 1 from pool_requests r where r.user_id = profiles.id and r.pool_id is not null and public.is_pool_member(r.pool_id)));

create policy "own requests" on public.pool_requests for select using (user_id = auth.uid());
create policy "pool members see the pool's requests" on public.pool_requests for select
  using (pool_id is not null and public.is_pool_member(pool_id));
create policy "cancel own request" on public.pool_requests for update
  using (user_id = auth.uid()) with check (user_id = auth.uid() and status = 'cancelled');

create policy "members see their pool" on public.pools for select using (public.is_pool_member(id));

-- ---------- matching ----------
create or replace function public.find_pool(
  p_pickup_lat double precision, p_pickup_lng double precision, p_pickup_label text,
  p_drop_lat double precision, p_drop_lng double precision, p_drop_label text
)
returns table (request_id uuid, pool_id uuid, status public.request_status)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
  me uuid := auth.uid();
  my_req pool_requests;
  cand pool_requests;
  target_pool uuid;
  seats_taken int;
begin
  if me is null then raise exception 'sign in first'; end if;

  update pool_requests set status = 'expired'
   where status = 'searching' and expires_at < now();
  -- one live search per rider
  update pool_requests set status = 'cancelled'
   where user_id = me and status = 'searching';

  insert into pool_requests (user_id, pickup, pickup_label, dropoff, dropoff_label)
  values (me,
          st_setsrid(st_makepoint(p_pickup_lng, p_pickup_lat), 4326)::geography, p_pickup_label,
          st_setsrid(st_makepoint(p_drop_lng, p_drop_lat), 4326)::geography, p_drop_label)
  returning * into my_req;

  -- best candidate: someone already searching (or in a forming pool with a free seat)
  select r.* into cand
    from pool_requests r
    left join pools p on p.id = r.pool_id
   where r.user_id <> me
     and r.created_at > now() - interval '15 minutes'
     and (r.status = 'searching' or (r.status = 'matched' and p.status = 'forming'))
     and st_dwithin(r.pickup, my_req.pickup, 1500)
     and (
       -- my drop lies along their trip, or theirs along mine
       (my_req.trip_km <= r.trip_km and st_dwithin(my_req.dropoff, st_makeline(r.pickup::geometry, r.dropoff::geometry)::geography, 2000))
       or
       (r.trip_km < my_req.trip_km and st_dwithin(r.dropoff, st_makeline(my_req.pickup::geometry, my_req.dropoff::geometry)::geography, 2000))
     )
   order by st_distance(r.pickup, my_req.pickup) + st_distance(r.dropoff, my_req.dropoff)
   limit 1
   for update of r skip locked;

  if not found then
    return query select my_req.id, null::uuid, my_req.status;
    return;
  end if;

  target_pool := cand.pool_id;
  if target_pool is not null then
    select count(*) into seats_taken from pool_requests where pool_requests.pool_id = target_pool and pool_requests.status = 'matched';
    if seats_taken >= 4 then target_pool := null; end if;
  end if;

  if target_pool is null then
    insert into pools default values returning id into target_pool;
    update pool_requests set status = 'matched', pool_id = target_pool where id = cand.id;
  end if;

  update pool_requests set status = 'matched', pool_id = target_pool where id = my_req.id;
  return query select my_req.id, target_pool, 'matched'::public.request_status;
end;
$$;

revoke all on function public.find_pool from public, anon;
grant execute on function public.find_pool to authenticated;
revoke all on function public.is_pool_member from public, anon;
grant execute on function public.is_pool_member to authenticated;
