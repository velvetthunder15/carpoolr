-- carpoolr: post-ride UPI settlement, read-only ride chat, and zero-length route guards.

create or replace function public.trips_match(
  a_pickup extensions.geography, a_drop extensions.geography, a_km double precision,
  b_pickup extensions.geography, b_drop extensions.geography, b_km double precision
) returns boolean language plpgsql immutable set search_path = public, extensions as $$
declare
  lp extensions.geography; ld extensions.geography;
  sp extensions.geography; sd extensions.geography;
  line extensions.geometry;
begin
  if greatest(a_km, b_km) < 0.05 then return false; end if;
  if a_km >= b_km then lp := a_pickup; ld := a_drop; sp := b_pickup; sd := b_drop;
  else lp := b_pickup; ld := b_drop; sp := a_pickup; sd := a_drop; end if;
  line := st_makeline(lp::geometry, ld::geometry);
  if st_length(line) = 0 then return false; end if;
  return st_dwithin(sp, line::geography, 2000)
     and st_dwithin(sd, line::geography, 2000)
     and st_linelocatepoint(line, sp::geometry) <= st_linelocatepoint(line, sd::geometry);
end;
$$;

create or replace function public.find_pool(
  p_pickup_lat double precision, p_pickup_lng double precision, p_pickup_label text,
  p_drop_lat double precision, p_drop_lng double precision, p_drop_label text
)
returns table (request_id uuid, pool_id uuid, status public.request_status)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
  me uuid := auth.uid();
  p_pt extensions.geography;
  d_pt extensions.geography;
  my_req pool_requests;
  cand pool_requests;
  target_pool uuid;
begin
  if me is null then raise exception 'sign in first'; end if;
  p_pt := st_setsrid(st_makepoint(p_pickup_lng, p_pickup_lat), 4326)::geography;
  d_pt := st_setsrid(st_makepoint(p_drop_lng, p_drop_lat), 4326)::geography;
  if st_distance(p_pt, d_pt) < 200 then
    raise exception 'pickup and drop are too close';
  end if;

  update pool_requests set status = 'expired' where status = 'searching' and expires_at < now();
  -- one live trip per rider: leave anything older first
  perform public.leave_pool();

  insert into pool_requests (user_id, pickup, pickup_label, dropoff, dropoff_label)
  values (me, p_pt, p_pickup_label, d_pt, p_drop_label)
  returning * into my_req;

  select r.* into cand
    from pool_requests r left join pools p on p.id = r.pool_id
   where r.user_id <> me
     and r.created_at > now() - interval '15 minutes'
     and (r.status = 'searching' or (r.status = 'matched' and p.status = 'forming'
          and (select count(*) from pool_requests x where x.pool_id = r.pool_id and x.status = 'matched') < 4))
     and public.trips_match(my_req.pickup, my_req.dropoff, my_req.trip_km, r.pickup, r.dropoff, r.trip_km)
   order by st_distance(r.pickup, my_req.pickup) + st_distance(r.dropoff, my_req.dropoff)
   limit 1
   for update of r skip locked;

  if not found then
    return query select my_req.id, null::uuid, my_req.status;
    return;
  end if;

  target_pool := cand.pool_id;
  if target_pool is null then
    insert into pools default values returning id into target_pool;
    update pool_requests set status = 'matched', pool_id = target_pool where id = cand.id;
  end if;
  update pool_requests set status = 'matched', pool_id = target_pool where id = my_req.id;
  return query select my_req.id, target_pool, 'matched'::public.request_status;
end;
$$;

create or replace function public.my_pool()
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
      null::public.pool_status, null::integer, null::text;
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
           p.status, p.fare_rupees, p.provider
      from pool_requests r
      join pools p on p.id = r.pool_id
      left join profiles pr on pr.id = r.user_id
     where r.pool_id = mine.pool_id and r.status = 'matched'
     order by r.created_at;
end;
$$;

-- Replace 0-arg mark_paid() with optional p_pool so riders can also settle finished rides.
drop function if exists public.mark_paid();
create or replace function public.mark_paid(p_pool uuid default null)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'sign in first'; end if;
  if p_pool is null then
    update pool_requests set paid = true
     where user_id = auth.uid() and status = 'matched';
  else
    update pool_requests set paid = true
     where user_id = auth.uid() and pool_id = p_pool and status in ('matched', 'done');
  end if;
end;
$$;

-- History: finished pools I rode in, including leg distances and booker UPI so unpaid shares can still settle.
drop function if exists public.my_rides();
create function public.my_rides()
returns table (
  pool_id uuid,
  done_at timestamptz,
  pickup_label text,
  dropoff_label text,
  trip_km double precision,
  riders integer,
  fare_rupees integer,
  provider text,
  is_booker boolean,
  paid boolean,
  booker_name text,
  booker_upi text,
  legs jsonb
)
language sql stable security definer set search_path = public, extensions as $$
  select
    p.id as pool_id,
    p.done_at,
    r.pickup_label,
    r.dropoff_label,
    r.trip_km,
    (select count(*)::int from pool_requests x where x.pool_id = p.id and x.status = 'done') as riders,
    p.fare_rupees,
    p.provider,
    coalesce(r.user_id = bk.uid, false) as is_booker,
    r.paid,
    coalesce(bpr.display_name, 'booker') as booker_name,
    bpr.upi_id as booker_upi,
    coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'rider_id', u.user_id,
          'is_me', coalesce(u.user_id = auth.uid(), false),
          'display_name', coalesce(upr.display_name, 'rider'),
          'board_km', u.board_km,
          'alight_km', greatest(u.raw_alight_km, u.board_km + 0.1),
          'paid', u.paid,
          'is_booker', coalesce(u.user_id = bk.uid, false)
        ) order by u.created_at
      )
      from (
        select distinct on (x.user_id)
          x.user_id,
          x.created_at,
          x.paid,
          case when broute.line is not null and st_length(broute.line) > 0
               then coalesce(st_linelocatepoint(broute.line, x.pickup::geometry) * broute.km, 0::double precision)
               else 0::double precision end as board_km,
          case when broute.line is not null and st_length(broute.line) > 0
               then coalesce(st_linelocatepoint(broute.line, x.dropoff::geometry) * broute.km, x.trip_km)
               else x.trip_km end as raw_alight_km
        from pool_requests x
        where x.pool_id = p.id and x.status = 'done'
        order by x.user_id, x.created_at desc
      ) u
      left join profiles upr on upr.id = u.user_id
    ), '[]'::jsonb) as legs
  from (
    select distinct on (r0.pool_id) r0.*
    from pool_requests r0
    where r0.user_id = auth.uid() and r0.status = 'done' and r0.pool_id is not null
    order by r0.pool_id, r0.created_at desc
  ) r
  join pools p on p.id = r.pool_id
  cross join lateral (select public.pool_booker(p.id) as uid) bk
  left join profiles bpr on bpr.id = bk.uid
  left join lateral (
    select
      case when br.trip_km > 0.01 then st_makeline(br.pickup::geometry, br.dropoff::geometry) else null end as line,
      br.trip_km as km
    from pool_requests br
    where br.pool_id = p.id and br.user_id = bk.uid and br.status = 'done'
    order by br.created_at desc
    limit 1
  ) broute on true
  order by p.done_at desc nulls last
  limit 50;
$$;

-- Read-only chat transcript for a pool I rode in (active or finished).
create or replace function public.ride_messages(p_pool uuid)
returns table (id bigint, user_id uuid, is_me boolean, display_name text, body text, created_at timestamptz)
language sql stable security definer set search_path = public as $$
  select m.id, m.user_id, coalesce(m.user_id = auth.uid(), false), coalesce(pr.display_name, 'rider'), m.body, m.created_at
    from messages m left join profiles pr on pr.id = m.user_id
   where m.pool_id = p_pool
     and exists (
       select 1 from pool_requests r
        where r.pool_id = p_pool and r.user_id = auth.uid() and r.status in ('matched', 'done')
     )
   order by m.id limit 200;
$$;

do $$ declare f text; begin
  foreach f in array array[
    'trips_match(extensions.geography,extensions.geography,double precision,extensions.geography,extensions.geography,double precision)',
    'find_pool(double precision,double precision,text,double precision,double precision,text)',
    'my_pool()',
    'mark_paid(uuid)',
    'my_rides()',
    'ride_messages(uuid)'
  ] loop
    execute format('revoke all on function public.%s from public, anon', f);
    execute format('grant execute on function public.%s to authenticated', f);
  end loop;
end $$;
