-- carpoolr: detailed rider profile, UPI VPA verification state, UIDAI QR Aadhaar status, avatars, and PII privacy.

alter table public.profiles add column if not exists upi_verified boolean not null default false;
alter table public.profiles add column if not exists phone text;
alter table public.profiles add column if not exists dob text;
alter table public.profiles add column if not exists gender text
  check (gender is null or gender in ('female', 'male', 'non-binary', 'prefer not to say'));
alter table public.profiles add column if not exists org_tag text;
alter table public.profiles add column if not exists emergency_name text;
alter table public.profiles add column if not exists emergency_phone text;
alter table public.profiles add column if not exists aadhaar_last4 text
  check (aadhaar_last4 is null or aadhaar_last4 ~ '^\d{4}$');
alter table public.profiles add column if not exists aadhaar_verified boolean not null default false;
alter table public.profiles add column if not exists avatar_data text
  check (avatar_data is null or length(avatar_data) <= 80000);

-- Prevent co-riders from reading private fields (dob, phone, emergency_phone, aadhaar_last4) directly on profiles.
-- Public pool fields are projected strictly through security-definer RPCs below.
drop policy if exists "pool members see each other's profile" on public.profiles;

drop function if exists public.my_profile();
create function public.my_profile()
returns table (
  display_name text,
  upi_id text,
  upi_verified boolean,
  phone text,
  dob text,
  gender text,
  org_tag text,
  emergency_name text,
  emergency_phone text,
  aadhaar_last4 text,
  aadhaar_verified boolean,
  avatar_data text,
  created_at timestamptz,
  pools_count integer,
  km_shared double precision
)
language sql stable security definer set search_path = public as $$
  select
    p.display_name,
    p.upi_id,
    coalesce(p.upi_verified, false),
    p.phone,
    p.dob,
    p.gender,
    p.org_tag,
    p.emergency_name,
    p.emergency_phone,
    p.aadhaar_last4,
    coalesce(p.aadhaar_verified, false),
    p.avatar_data,
    coalesce(p.created_at, now()),
    (select count(*)::int from pool_requests r where r.user_id = me.uid and r.status = 'done'),
    coalesce((select sum(r.trip_km) from pool_requests r where r.user_id = me.uid and r.status = 'done'), 0::double precision)
  from (select auth.uid() as uid) me
  left join profiles p on p.id = me.uid
  where me.uid is not null;
$$;

drop function if exists public.update_profile(text, text);
create or replace function public.update_profile(
  p_name text,
  p_upi text,
  p_upi_verified boolean default false,
  p_phone text default null,
  p_dob text default null,
  p_gender text default null,
  p_org_tag text default null,
  p_emergency_name text default null,
  p_emergency_phone text default null,
  p_aadhaar_last4 text default null,
  p_aadhaar_verified boolean default false,
  p_avatar_data text default null
)
returns void language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  clean_upi text := nullif(left(trim(coalesce(p_upi, '')), 80), '');
  clean_gender text := lower(trim(coalesce(p_gender, '')));
  clean_last4 text := nullif(trim(coalesce(p_aadhaar_last4, '')), '');
  clean_avatar text := nullif(trim(coalesce(p_avatar_data, '')), '');
begin
  if me is null then raise exception 'sign in first'; end if;
  if clean_gender not in ('female', 'male', 'non-binary', 'prefer not to say') then
    clean_gender := null;
  end if;
  if clean_last4 is not null and clean_last4 !~ '^\d{4}$' then
    clean_last4 := null;
  end if;
  if clean_avatar is not null and length(clean_avatar) > 80000 then
    raise exception 'photo is too large';
  end if;

  insert into profiles (
    id, display_name, upi_id, upi_verified, phone, dob, gender, org_tag,
    emergency_name, emergency_phone, aadhaar_last4, aadhaar_verified, avatar_data
  ) values (
    me,
    nullif(left(trim(coalesce(p_name, '')), 40), ''),
    clean_upi,
    (clean_upi is not null and coalesce(p_upi_verified, false)),
    nullif(left(trim(coalesce(p_phone, '')), 20), ''),
    nullif(left(trim(coalesce(p_dob, '')), 16), ''),
    clean_gender,
    nullif(left(trim(coalesce(p_org_tag, '')), 50), ''),
    nullif(left(trim(coalesce(p_emergency_name, '')), 40), ''),
    nullif(left(trim(coalesce(p_emergency_phone, '')), 20), ''),
    clean_last4,
    (clean_last4 is not null and coalesce(p_aadhaar_verified, false)),
    clean_avatar
  )
  on conflict (id) do update set
    display_name = excluded.display_name,
    upi_id = excluded.upi_id,
    upi_verified = excluded.upi_verified,
    phone = excluded.phone,
    dob = excluded.dob,
    gender = excluded.gender,
    org_tag = excluded.org_tag,
    emergency_name = excluded.emergency_name,
    emergency_phone = excluded.emergency_phone,
    aadhaar_last4 = excluded.aadhaar_last4,
    aadhaar_verified = excluded.aadhaar_verified,
    avatar_data = excluded.avatar_data;
end;
$$;

-- Extend my_pool() with lightweight rider trust badges (aadhaar_verified, upi_verified, org_tag).
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
  pool_status public.pool_status, fare_rupees integer, provider text,
  aadhaar_verified boolean, upi_verified boolean, org_tag text
)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
  me uuid := auth.uid();
  mine pool_requests;
  cand pool_requests;
  target uuid;
  booker uuid;
  route extensions.geometry;
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
      null::public.pool_status, null::integer, null::text,
      coalesce((select aadhaar_verified from profiles where id = me), false),
      coalesce((select upi_verified from profiles where id = me), false),
      (select org_tag from profiles where id = me);
    return;
  end if;

  booker := public.pool_booker(mine.pool_id);
  select case when r.trip_km > 0.01 then st_makeline(r.pickup::geometry, r.dropoff::geometry) else null end,
         r.trip_km
    into route, route_km
    from pool_requests r where r.pool_id = mine.pool_id and r.user_id = booker and r.status = 'matched' limit 1;

  return query
    select mine.id, mine.pool_id, mine.status, mine.expires_at, r.user_id, r.user_id = me,
           coalesce(pr.display_name, 'rider'), r.pickup_label, r.dropoff_label,
           st_y(r.pickup::geometry), st_x(r.pickup::geometry),
           st_y(r.dropoff::geometry), st_x(r.dropoff::geometry), r.trip_km,
           case when route is not null and st_length(route) > 0
                then coalesce(st_linelocatepoint(route, r.pickup::geometry) * route_km, 0::double precision)
                else 0::double precision end,
           greatest(
             case when route is not null and st_length(route) > 0
                  then coalesce(st_linelocatepoint(route, r.dropoff::geometry) * route_km, r.trip_km)
                  else r.trip_km end,
             (case when route is not null and st_length(route) > 0
                   then coalesce(st_linelocatepoint(route, r.pickup::geometry) * route_km, 0::double precision)
                   else 0::double precision end) + 0.1
           ),
           coalesce(r.user_id = booker, false), pr.upi_id, r.paid,
           p.status, p.fare_rupees, p.provider,
           coalesce(pr.aadhaar_verified, false),
           coalesce(pr.upi_id is not null and pr.upi_verified, false),
           pr.org_tag
      from pool_requests r
      join pools p on p.id = r.pool_id
      left join profiles pr on pr.id = r.user_id
     where r.pool_id = mine.pool_id and r.status = 'matched'
     order by r.created_at;
end;
$$;

-- Fetched once per poolId so the 3-second my_pool() poll stays tiny.
create or replace function public.pool_avatars(p_pool uuid)
returns table (rider_id uuid, avatar_data text)
language sql stable security definer set search_path = public as $$
  select distinct on (r.user_id) r.user_id, pr.avatar_data
    from pool_requests r
    join profiles pr on pr.id = r.user_id
   where r.pool_id = p_pool
     and r.status in ('matched', 'done')
     and pr.avatar_data is not null
     and exists (
       select 1 from pool_requests me
        where me.pool_id = p_pool and me.user_id = auth.uid() and me.status in ('matched', 'done')
     );
$$;

do $$ declare f text; begin
  foreach f in array array[
    'my_profile()',
    'update_profile(text,text,boolean,text,text,text,text,text,text,text,boolean,text)',
    'my_pool()',
    'pool_avatars(uuid)'
  ] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
