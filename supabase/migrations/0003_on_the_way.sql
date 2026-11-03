-- carpoolr: match "on the way", not "same pickup".
-- Two trips pool when the shorter one fits inside the longer one's corridor:
-- its pickup AND its drop are each within 2 km of the longer trip's straight line
-- (that line includes the longer trip's destination, so "drop near my destination" counts),
-- and it runs the same direction (its pickup comes before its drop along that line).
create or replace function public.trips_match(
  a_pickup extensions.geography, a_drop extensions.geography, a_km double precision,
  b_pickup extensions.geography, b_drop extensions.geography, b_km double precision
) returns boolean language plpgsql immutable set search_path = public, extensions as $$
declare
  lp extensions.geography; ld extensions.geography;   -- longer trip
  sp extensions.geography; sd extensions.geography;   -- shorter trip
  line extensions.geometry;
begin
  if a_km >= b_km then lp := a_pickup; ld := a_drop; sp := b_pickup; sd := b_drop;
  else lp := b_pickup; ld := b_drop; sp := a_pickup; sd := a_drop; end if;
  line := st_makeline(lp::geometry, ld::geometry);
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
  my_req pool_requests;
  cand pool_requests;
  target_pool uuid;
begin
  if me is null then raise exception 'sign in first'; end if;
  update pool_requests set status = 'expired' where status = 'searching' and expires_at < now();
  -- one live trip per rider: leave anything older first
  perform public.leave_pool();

  insert into pool_requests (user_id, pickup, pickup_label, dropoff, dropoff_label)
  values (me,
          st_setsrid(st_makepoint(p_pickup_lng, p_pickup_lat), 4326)::geography, p_pickup_label,
          st_setsrid(st_makepoint(p_drop_lng, p_drop_lat), 4326)::geography, p_drop_label)
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

-- my_pool: same on-the-way rule for the retry while searching
create or replace function public.my_pool()
returns table (
  request_id uuid, pool_id uuid, status public.request_status, expires_at timestamptz,
  rider_id uuid, is_me boolean, display_name text,
  pickup_label text, dropoff_label text,
  pickup_lat double precision, pickup_lng double precision,
  drop_lat double precision, drop_lng double precision, trip_km double precision
)
language plpgsql security definer set search_path = public, extensions as $$
#variable_conflict use_column
declare
  me uuid := auth.uid();
  mine pool_requests;
  cand pool_requests;
  target uuid;
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
      st_y(mine.dropoff::geometry), st_x(mine.dropoff::geometry), mine.trip_km;
    return;
  end if;

  return query
    select mine.id, mine.pool_id, mine.status, mine.expires_at, r.user_id, r.user_id = me,
           coalesce(pr.display_name, 'rider'), r.pickup_label, r.dropoff_label,
           st_y(r.pickup::geometry), st_x(r.pickup::geometry),
           st_y(r.dropoff::geometry), st_x(r.dropoff::geometry), r.trip_km
      from pool_requests r left join profiles pr on pr.id = r.user_id
     where r.pool_id = mine.pool_id and r.status = 'matched'
     order by r.created_at;
end;
$$;
