-- carpoolr: what a rider sees while searching / pooled, leaving a pool, and display names.

-- display name = email prefix until the rider sets one
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into profiles (id, display_name) values (new.id, split_part(new.email, '@', 1))
  on conflict (id) do nothing;
  return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();
insert into public.profiles (id, display_name)
  select id, split_part(email, '@', 1) from auth.users on conflict (id) do nothing;

-- One call returns my latest live request plus everyone in its pool.
-- A searching request also retries the match, so two riders who searched at the
-- same moment still find each other.
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
     and (r.status = 'searching' or exists (select 1 from pools p where p.id = r.pool_id and p.status in ('forming', 'booked')))
   order by r.created_at desc limit 1;
  if not found then return; end if;

  if mine.status = 'searching' then
    select r.* into cand
      from pool_requests r left join pools p on p.id = r.pool_id
     where r.user_id <> me and r.id <> mine.id
       and r.created_at > now() - interval '15 minutes'
       and (r.status = 'searching' or (r.status = 'matched' and p.status = 'forming'
            and (select count(*) from pool_requests x where x.pool_id = r.pool_id and x.status = 'matched') < 4))
       and st_dwithin(r.pickup, mine.pickup, 1500)
       and ((mine.trip_km <= r.trip_km and st_dwithin(mine.dropoff, st_makeline(r.pickup::geometry, r.dropoff::geometry)::geography, 2000))
         or (r.trip_km < mine.trip_km and st_dwithin(r.dropoff, st_makeline(mine.pickup::geometry, mine.dropoff::geometry)::geography, 2000)))
     order by st_distance(r.pickup, mine.pickup) + st_distance(r.dropoff, mine.dropoff)
     limit 1 for update of r skip locked;
    if found then
      target := cand.pool_id;
      if target is null then
        insert into pools default values returning id into target;
        update pool_requests set status = 'matched', pool_id = target where id = cand.id;
      end if;
      update pool_requests set status = 'matched', pool_id = target where id = mine.id
        returning * into mine;
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

-- Leave: cancels my request. If one rider would be left alone, they go back to searching.
create or replace function public.leave_pool()
returns void language plpgsql security definer set search_path = public as $$
declare
  me uuid := auth.uid();
  pid uuid;
  left_count int;
begin
  if me is null then raise exception 'sign in first'; end if;
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

revoke all on function public.my_pool from public, anon;
grant execute on function public.my_pool to authenticated;
revoke all on function public.leave_pool from public, anon;
grant execute on function public.leave_pool to authenticated;
