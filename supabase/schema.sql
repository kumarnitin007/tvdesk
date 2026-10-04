-- TV Desk clean install.
-- Running this deletes the current board and creates the family task experience.
-- Paste the whole file into the Supabase SQL editor and run it once.

drop view if exists public.tvdesk_posts_active;
drop trigger if exists tvdesk_task_star_trigger on public.tvdesk_task_logs;
drop trigger if exists tvdesk_prepare_post_trigger on public.tvdesk_posts;
drop trigger if exists tvdesk_post_bonus_trigger on public.tvdesk_posts;
drop function if exists public.tvdesk_redeem_reward(uuid);
drop function if exists public.tvdesk_apply_post_bonus();
drop function if exists public.tvdesk_prepare_post();
drop function if exists public.tvdesk_sync_task_star();
drop function if exists public.tvdesk_refresh_user_stats(uuid);

drop table if exists public.tvdesk_reward_redemptions cascade;
drop table if exists public.tvdesk_star_ledger cascade;
drop table if exists public.tvdesk_user_achievements cascade;
drop table if exists public.tvdesk_posts cascade;
drop table if exists public.tvdesk_task_logs cascade;
drop table if exists public.tvdesk_awards cascade;
drop table if exists public.tvdesk_schedule_items cascade;
drop table if exists public.tvdesk_tasks cascade;
drop table if exists public.tvdesk_settings cascade;
drop table if exists public.tvdesk_users cascade;

create table public.tvdesk_users (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  sort_order int not null default 0,
  active boolean not null default true,
  reward_title text not null default 'Movie night',
  reward_target int not null default 50 check (reward_target >= 0),
  stars_toward_reward int not null default 0 check (stars_toward_reward >= 0),
  streak_current int not null default 0 check (streak_current >= 0),
  streak_best int not null default 0 check (streak_best >= 0),
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_settings (
  id int primary key,
  theme text not null default 'cards',
  city text not null default 'Bothell',
  countdown_label text not null default 'Weekend',
  countdown_date date,
  active_user_id uuid references public.tvdesk_users (id) on delete set null,
  meta jsonb not null default '{"tv_experience":"family","look":"paper"}'::jsonb
);

create table public.tvdesk_tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  label text not null,
  detail text not null default '',
  icon text not null default 'star',
  sort_order int not null default 0,
  cadence text not null default 'daily' check (cadence in ('daily', 'weekly', 'periodic', 'once')),
  weekdays int[] not null default '{}',
  every_n_days int,
  anchor_date date,
  on_date date,
  goal_count int not null default 1,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_schedule_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  title text not null,
  time_label text not null default '',
  sort_order int not null default 0,
  task_id uuid references public.tvdesk_tasks (id) on delete set null,
  cadence text not null default 'once' check (cadence in ('daily', 'weekly', 'periodic', 'once')),
  weekdays int[] not null default '{}',
  every_n_days int,
  anchor_date date,
  on_date date,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_task_logs (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references public.tvdesk_tasks (id) on delete cascade,
  day date not null,
  count int not null default 1,
  meta jsonb not null default '{}'::jsonb,
  unique (task_id, day)
);

create table public.tvdesk_awards (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.tvdesk_users (id) on delete cascade,
  title text not null default '',
  message text not null,
  minutes int,
  icon text not null default 'star',
  show_when text not null default 'all_done' check (show_when in ('all_done', 'min_done', 'always')),
  min_done int,
  sort_order int not null default 0,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_posts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.tvdesk_users (id) on delete cascade,
  message text not null,
  badge text not null default 'none' check (badge in ('star', 'fire', 'heart', 'none')),
  duration text not null default 'today' check (duration in ('today', '3_days', 'until_removed')),
  bonus_stars int not null default 0 check (bonus_stars >= 0),
  posted_on date not null default current_date,
  expires_on date,
  removed_at timestamptz,
  sort_order int not null default 0,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_user_achievements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  title text not null,
  subtitle text not null default '',
  icon text not null default 'star' check (icon in ('star', 'fire', 'heart', 'none')),
  earned_on date not null default current_date,
  sort_order int not null default 0,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_star_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  stars int not null check (stars <> 0),
  reason text not null check (reason in ('task_complete', 'post_bonus', 'manual', 'redeem')),
  task_id uuid references public.tvdesk_tasks (id) on delete set null,
  day date,
  post_id uuid references public.tvdesk_posts (id) on delete set null,
  note text not null default '',
  created_at timestamptz not null default now()
);

create table public.tvdesk_reward_redemptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  reward_title text not null,
  reward_target int not null check (reward_target > 0),
  stars_spent int not null check (stars_spent > 0),
  redeemed_at timestamptz not null default now()
);

create unique index tvdesk_star_task_once on public.tvdesk_star_ledger (user_id, task_id, day) where reason = 'task_complete';
create unique index tvdesk_star_post_once on public.tvdesk_star_ledger (user_id, post_id) where reason = 'post_bonus';
create index tvdesk_logs_day on public.tvdesk_task_logs (day);
create index tvdesk_tasks_user on public.tvdesk_tasks (user_id) where active = true;

create or replace view public.tvdesk_posts_active as
select * from public.tvdesk_posts
where active = true and removed_at is null and (expires_on is null or expires_on >= current_date);

create or replace function public.tvdesk_refresh_user_stats(p_user_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_total int;
  v_last_day date;
  v_cursor date;
  v_streak int := 0;
  v_best int;
begin
  select greatest(coalesce(sum(stars), 0), 0)::int into v_total
    from public.tvdesk_star_ledger where user_id = p_user_id;
  select max(l.day) into v_last_day
    from public.tvdesk_task_logs l
    join public.tvdesk_tasks t on t.id = l.task_id
   where t.user_id = p_user_id and l.count >= t.goal_count;
  if v_last_day is not null and v_last_day >= current_date - 1 then
    v_cursor := v_last_day;
    loop
      exit when not exists (
        select 1 from public.tvdesk_task_logs l
        join public.tvdesk_tasks t on t.id = l.task_id
        where t.user_id = p_user_id and l.day = v_cursor and l.count >= t.goal_count
      );
      v_streak := v_streak + 1;
      v_cursor := v_cursor - 1;
    end loop;
  end if;
  select streak_best into v_best from public.tvdesk_users where id = p_user_id;
  update public.tvdesk_users
     set stars_toward_reward = v_total,
         streak_current = v_streak,
         streak_best = greatest(coalesce(v_best, 0), v_streak)
   where id = p_user_id;
end $$;

create or replace function public.tvdesk_sync_task_star()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_task_id uuid := coalesce(new.task_id, old.task_id);
  v_day date := coalesce(new.day, old.day);
  v_count int := case when tg_op = 'DELETE' then 0 else new.count end;
  v_user_id uuid;
  v_goal int;
begin
  select user_id, goal_count into v_user_id, v_goal from public.tvdesk_tasks where id = v_task_id;
  if v_user_id is null then return coalesce(new, old); end if;
  if v_count >= v_goal then
    insert into public.tvdesk_star_ledger (user_id, stars, reason, task_id, day, note)
    values (v_user_id, 1, 'task_complete', v_task_id, v_day, 'Task completed')
    on conflict do nothing;
  else
    delete from public.tvdesk_star_ledger
     where user_id = v_user_id and task_id = v_task_id and day = v_day and reason = 'task_complete';
  end if;
  perform public.tvdesk_refresh_user_stats(v_user_id);
  return coalesce(new, old);
end $$;

create trigger tvdesk_task_star_trigger
after insert or update or delete on public.tvdesk_task_logs
for each row execute function public.tvdesk_sync_task_star();

create or replace function public.tvdesk_prepare_post()
returns trigger language plpgsql set search_path = public as $$
begin
  new.posted_on := coalesce(new.posted_on, current_date);
  new.expires_on := case new.duration
    when 'today' then new.posted_on
    when '3_days' then new.posted_on + 2
    else null end;
  return new;
end $$;

create trigger tvdesk_prepare_post_trigger
before insert or update of duration, posted_on on public.tvdesk_posts
for each row execute function public.tvdesk_prepare_post();

create or replace function public.tvdesk_apply_post_bonus()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_user record;
begin
  if new.bonus_stars <= 0 then return new; end if;
  for v_user in select id from public.tvdesk_users where active and (new.user_id is null or id = new.user_id) loop
    insert into public.tvdesk_star_ledger (user_id, stars, reason, post_id, day, note)
    values (v_user.id, new.bonus_stars, 'post_bonus', new.id, new.posted_on, new.message)
    on conflict do nothing;
    perform public.tvdesk_refresh_user_stats(v_user.id);
  end loop;
  return new;
end $$;

create trigger tvdesk_post_bonus_trigger
after insert on public.tvdesk_posts
for each row execute function public.tvdesk_apply_post_bonus();

create or replace function public.tvdesk_redeem_reward(p_user_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare v_user public.tvdesk_users%rowtype;
begin
  select * into v_user from public.tvdesk_users where id = p_user_id for update;
  if v_user.id is null or v_user.reward_target <= 0 or v_user.stars_toward_reward < v_user.reward_target then
    raise exception 'Reward is not ready to redeem';
  end if;
  insert into public.tvdesk_reward_redemptions (user_id, reward_title, reward_target, stars_spent)
  values (v_user.id, v_user.reward_title, v_user.reward_target, v_user.reward_target);
  insert into public.tvdesk_star_ledger (user_id, stars, reason, note)
  values (v_user.id, -v_user.reward_target, 'redeem', v_user.reward_title);
  perform public.tvdesk_refresh_user_stats(v_user.id);
end $$;

alter table public.tvdesk_users enable row level security;
alter table public.tvdesk_settings enable row level security;
alter table public.tvdesk_tasks enable row level security;
alter table public.tvdesk_schedule_items enable row level security;
alter table public.tvdesk_task_logs enable row level security;
alter table public.tvdesk_awards enable row level security;
alter table public.tvdesk_posts enable row level security;
alter table public.tvdesk_user_achievements enable row level security;
alter table public.tvdesk_star_ledger enable row level security;
alter table public.tvdesk_reward_redemptions enable row level security;

create policy "anon all tvdesk_users" on public.tvdesk_users for all to anon using (true) with check (true);
create policy "anon all tvdesk_settings" on public.tvdesk_settings for all to anon using (true) with check (true);
create policy "anon all tvdesk_tasks" on public.tvdesk_tasks for all to anon using (true) with check (true);
create policy "anon all tvdesk_schedule" on public.tvdesk_schedule_items for all to anon using (true) with check (true);
create policy "anon all tvdesk_logs" on public.tvdesk_task_logs for all to anon using (true) with check (true);
create policy "anon all tvdesk_awards" on public.tvdesk_awards for all to anon using (true) with check (true);
create policy "anon all tvdesk_posts" on public.tvdesk_posts for all to anon using (true) with check (true);
create policy "anon all tvdesk_achievements" on public.tvdesk_user_achievements for all to anon using (true) with check (true);
create policy "anon all tvdesk_star_ledger" on public.tvdesk_star_ledger for all to anon using (true) with check (true);
create policy "anon all tvdesk_redemptions" on public.tvdesk_reward_redemptions for all to anon using (true) with check (true);

grant usage on schema public to anon;
grant select, insert, update, delete on public.tvdesk_users to anon;
grant select, insert, update, delete on public.tvdesk_settings to anon;
grant select, insert, update, delete on public.tvdesk_tasks to anon;
grant select, insert, update, delete on public.tvdesk_schedule_items to anon;
grant select, insert, update, delete on public.tvdesk_task_logs to anon;
grant select, insert, update, delete on public.tvdesk_awards to anon;
grant select, insert, update, delete on public.tvdesk_posts to anon;
grant select, insert, update, delete on public.tvdesk_user_achievements to anon;
grant select, insert, update, delete on public.tvdesk_star_ledger to anon;
grant select, insert, update, delete on public.tvdesk_reward_redemptions to anon;
grant select on public.tvdesk_posts_active to anon;
grant execute on function public.tvdesk_redeem_reward(uuid) to anon;

insert into public.tvdesk_users (id, name, sort_order, reward_title, reward_target) values
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Kid 1', 1, 'Movie night', 50),
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Kid 2', 2, 'Movie night', 50);

insert into public.tvdesk_settings (id, theme, city, countdown_label, active_user_id, meta)
values (1, 'cards', 'Bothell', 'Weekend', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', '{"tv_experience":"family","look":"paper"}'::jsonb);

insert into public.tvdesk_tasks (id, user_id, label, icon, sort_order, cadence) values
  ('d1000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Duolingo', 'star', 1, 'daily'),
  ('d1000000-0000-4000-8000-000000000002', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Exercise', 'star', 2, 'daily'),
  ('d1000000-0000-4000-8000-000000000003', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Maths', 'math', 3, 'daily'),
  ('d1000000-0000-4000-8000-000000000004', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Homework', 'book', 4, 'daily'),
  ('d2000000-0000-4000-8000-000000000001', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Reading', 'book', 1, 'daily'),
  ('d2000000-0000-4000-8000-000000000002', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Exercise', 'star', 2, 'daily'),
  ('d2000000-0000-4000-8000-000000000003', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Maths', 'math', 3, 'daily'),
  ('d2000000-0000-4000-8000-000000000004', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Chores', 'star', 4, 'daily');

insert into public.tvdesk_task_logs (task_id, day, count)
select 'd1000000-0000-4000-8000-000000000001', current_date - offset_day, 1
from generate_series(0, 5) as offset_day
union all
select 'd1000000-0000-4000-8000-000000000002', current_date, 1;

insert into public.tvdesk_star_ledger (user_id, stars, reason, note)
values ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 35, 'manual', 'Sample progress toward movie night');

select public.tvdesk_refresh_user_stats('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.tvdesk_refresh_user_stats('cccccccc-cccc-4ccc-8ccc-cccccccccccc');

insert into public.tvdesk_user_achievements (user_id, title, subtitle, icon, sort_order)
values ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', '5-day Duolingo streak', 'Keep the streak going', 'star', 1);

insert into public.tvdesk_posts (user_id, message, badge, duration, bonus_stars, sort_order)
values ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Great job finishing Maths early this week!', 'star', 'today', 0, 1);
