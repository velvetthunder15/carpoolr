-- carpoolr: booker + fare, exact distance split inputs, UPI pay-back, chat, ride history.
alter type public.request_status add value if not exists 'done';
commit;
alter table public.profiles add column if not exists upi_id text;
alter table public.pools add column if not exists booker_id uuid references auth.users (id);
alter table public.pools add column if not exists booked_at timestamptz;
alter table public.pools add column if not exists done_at timestamptz;
alter table public.pool_requests add column if not exists paid boolean not null default false;

create table if not exists public.messages (
  id bigint generated always as identity primary key,
  pool_id uuid not null references public.pools (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  body text not null check (length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);
create index if not exists messages_pool_idx on public.messages (pool_id, id);
alter table public.messages enable row level security;
drop policy if exists "members read chat" on public.messages;
create policy "members read chat" on public.messages for select using (public.is_pool_member(pool_id));
drop policy if exists "members write chat" on public.messages;
create policy "members write chat" on public.messages for insert
  with check (user_id = auth.uid() and public.is_pool_member(pool_id));

-- the rider with the longest trip books (their route covers everyone's)
create or replace function public.pool_booker(p_pool uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select coalesce((select booker_id from pools where id = p_pool),
    (select user_id from pool_requests where pool_id = p_pool and status in ('matched','done')
      order by trip_km desc, created_at limit 1));
$$;

drop function if exists public.my_pool();
create function public.my_pool()
returns table (
  request_id uuid, pool_id uuid, status public.request_status, expires_at timestamptz,
  rider_id uuid, is_me boolean, display_name text,
  pickup_label text, dropoff_label text,
  pickup_lat double precision, pickup_lng double precision,
  drop_lat double precision, drop_lng double precision, trip_km double precision,
  board_km double precision, alight_km double precision,
  is_booker boolean, upi_id text, paid boolean,
  pool_status public.pool_status, fare_rupees integer, provider text
)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
  me uuid := auth.uid();
  mine pool_requests;
  cand pool_requests;
  target uuid;
  booker uuid;
  route geometry;
  route_km double precision;
begin
  if me is null then raise exception 'sign in first'; end if;
  update pool_requests set status = 'expired' where status = 'searching' and expires_at < now();

  select * into mine from pool_requests r
   where r.user_id = me and r.status in ('searching', 'matched')
   order by r.created_at desc limit 1;
  if not found then return; end if;

  if mine.status = 'searching' then
    select r.* into cand
      from pool_requests r left join pools p on p.id = r.pool_id
     where r.user_id <> me and r.id <> mine.id
       and r.created_at > now() - interval '15 minutes'
       and (r.status = 'searching' or (r.status = 'matched' and p.status = 'forming'
            and (select count(*) from pool_requests x where x.pool_id = r.pool_id and x.status = 'matched') < 4))
       and public.trips_match(mine.pickup, mine.dropoff, mine.trip_km, r.pickup, r.dropoff, r.trip_km)
     order by st_distance(r.pickup, mine.pickup) + st_distance(r.dropoff, mine.dropoff)
     limit 1 for update of r skip locked;
    if found then
      target := cand.pool_id;
      if target is null then
        insert into pools default values returning id into target;
        update pool_requests set status = 'matched', pool_id = target where id = cand.id;
      end if;
      update pool_requests set status = 'matched', pool_id = target where id = mine.id returning * into mine;
    end if;
  end if;

  if mine.pool_id is null then
    return query select mine.id, null::uuid, mine.status, mine.expires_at, me, true,
      (select display_name from profiles where id = me), mine.pickup_label, mine.dropoff_label,
      st_y(mine.pickup::geometry), st_x(mine.pickup::geometry),
      st_y(mine.dropoff::geometry), st_x(mine.dropoff::geometry), mine.trip_km,
      0::double precision, mine.trip_km, false, null::text, false,
      null::public.pool_status, null::integer, null::text;
    return;
  end if;

  booker := public.pool_booker(mine.pool_id);
  select st_makeline(r.pickup::geometry, r.dropoff::geometry), r.trip_km into route, route_km
    from pool_requests r where r.pool_id = mine.pool_id and r.user_id = booker and r.status = 'matched' limit 1;

  return query
    select mine.id, mine.pool_id, mine.status, mine.expires_at, r.user_id, r.user_id = me,
           coalesce(pr.display_name, 'rider'), r.pickup_label, r.dropoff_label,
           st_y(r.pickup::geometry), st_x(r.pickup::geometry),
           st_y(r.dropoff::geometry), st_x(r.dropoff::geometry), r.trip_km,
           -- where each rider gets on and off, in km along the booker's route
           coalesce(st_linelocatepoint(route, r.pickup::geometry) * route_km, 0),
           greatest(coalesce(st_linelocatepoint(route, r.dropoff::geometry) * route_km, r.trip_km),
                    coalesce(st_linelocatepoint(route, r.pickup::geometry) * route_km, 0) + 0.1),
           r.user_id = booker, pr.upi_id, r.paid,
           p.status, p.fare_rupees, p.provider
      from pool_requests r
      join pools p on p.id = r.pool_id
      left join profiles pr on pr.id = r.user_id
     where r.pool_id = mine.pool_id and r.status = 'matched'
     order by r.created_at;
end;
$$;

-- booker records the cab they booked and the fare; locks the pool (no more joiners)
create or replace function public.set_booking(p_fare integer, p_provider text)
returns void language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); pid uuid;
begin
  select pool_id into pid from pool_requests where user_id = me and status = 'matched' order by created_at desc limit 1;
  if pid is null then raise exception 'not in a pool'; end if;
  if public.pool_booker(pid) <> me then raise exception 'only the booker can set the fare'; end if;
  if p_fare < 0 or p_fare > 100000 then raise exception 'fare looks wrong'; end if;
  update pools set fare_rupees = p_fare, provider = p_provider, booker_id = me, status = 'booked', booked_at = now() where id = pid;
  update pool_requests set paid = true where pool_id = pid and user_id = me;
end;
$$;

create or replace function public.mark_paid()
returns void language plpgsql security definer set search_path = public as $$
begin
  update pool_requests set paid = true where user_id = auth.uid() and status = 'matched';
end;
$$;

-- anyone can end the ride; the pool moves to history
create or replace function public.finish_pool()
returns void language plpgsql security definer set search_path = public as $$
declare pid uuid;
begin
  select pool_id into pid from pool_requests where user_id = auth.uid() and status = 'matched' order by created_at desc limit 1;
  if pid is null then return; end if;
  update pools set status = 'done', done_at = now() where id = pid;
  update pool_requests set status = 'done' where pool_id = pid and status = 'matched';
end;
$$;

-- leaving a booked pool is not allowed (the cab is booked)
create or replace function public.leave_pool()
returns void language plpgsql security definer set search_path = public as $$
declare me uuid := auth.uid(); pid uuid; left_count int;
begin
  if me is null then raise exception 'sign in first'; end if;
  if exists (select 1 from pool_requests r join pools p on p.id = r.pool_id
             where r.user_id = me and r.status = 'matched' and p.status = 'booked') then
    raise exception 'the cab is booked. finish the ride instead';
  end if;
  update pool_requests set status = 'cancelled'
   where user_id = me and status in ('searching', 'matched')
   returning pool_id into pid;
  if pid is null then return; end if;
  select count(*) into left_count from pool_requests where pool_id = pid and status = 'matched';
  if left_count < 2 then
    update pools set status = 'cancelled' where id = pid;
    update pool_requests set status = 'searching', pool_id = null,
           expires_at = greatest(expires_at, now() + interval '10 minutes')
     where pool_id = pid and status = 'matched';
  end if;
end;
$$;

-- history: finished pools I was in
create or replace function public.my_rides()
returns table (pool_id uuid, done_at timestamptz, pickup_label text, dropoff_label text,
               trip_km double precision, riders integer, fare_rupees integer, provider text)
language sql stable security definer set search_path = public as $$
  select p.id, p.done_at, r.pickup_label, r.dropoff_label, r.trip_km,
         (select count(*)::int from pool_requests x where x.pool_id = p.id and x.status = 'done'),
         p.fare_rupees, p.provider
    from pool_requests r join pools p on p.id = r.pool_id
   where r.user_id = auth.uid() and r.status = 'done'
   order by p.done_at desc nulls last limit 50;
$$;

-- chat with names
create or replace function public.pool_messages(p_after bigint default 0)
returns table (id bigint, user_id uuid, is_me boolean, display_name text, body text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select m.id, m.user_id, m.user_id = auth.uid(), coalesce(pr.display_name, 'rider'), m.body, m.created_at
    from messages m left join profiles pr on pr.id = m.user_id
   where m.pool_id = (select pool_id from pool_requests where user_id = auth.uid() and status = 'matched' order by created_at desc limit 1)
     and m.id > p_after
   order by m.id limit 200;
$$;

create or replace function public.send_chat(p_body text)
returns void language plpgsql security definer set search_path = public as $$
declare pid uuid;
begin
  select pool_id into pid from pool_requests where user_id = auth.uid() and status = 'matched' order by created_at desc limit 1;
  if pid is null then raise exception 'not in a pool'; end if;
  insert into messages (pool_id, user_id, body) values (pid, auth.uid(), left(trim(p_body), 1000));
end;
$$;

create or replace function public.update_profile(p_name text, p_upi text)
returns void language sql security definer set search_path = public as $$
  insert into profiles (id, display_name, upi_id) values (auth.uid(), nullif(trim(p_name), ''), nullif(trim(p_upi), ''))
  on conflict (id) do update set display_name = excluded.display_name, upi_id = excluded.upi_id;
$$;

create or replace function public.my_profile()
returns table (display_name text, upi_id text)
language sql stable security definer set search_path = public as $$
  select display_name, upi_id from profiles where id = auth.uid();
$$;

do $$ declare f text; begin
  foreach f in array array['my_pool()','set_booking(integer,text)','mark_paid()','finish_pool()','leave_pool()',
    'my_rides()','pool_messages(bigint)','send_chat(text)','update_profile(text,text)','my_profile()','pool_booker(uuid)'] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
