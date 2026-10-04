-- Optional additive migration. Do not run this if you are resetting the board.
-- Use supabase/schema.sql instead: it deletes the current data and creates the family experience.

alter table public.tvdesk_users
  add column if not exists reward_title text not null default 'Movie night',
  add column if not exists reward_target int not null default 50,
  add column if not exists stars_toward_reward int not null default 0,
  add column if not exists streak_current int not null default 0,
  add column if not exists streak_best int not null default 0;

alter table public.tvdesk_users drop constraint if exists tvdesk_users_reward_target_check;
alter table public.tvdesk_users add constraint tvdesk_users_reward_target_check check (reward_target >= 0);
alter table public.tvdesk_users drop constraint if exists tvdesk_users_stars_check;
alter table public.tvdesk_users add constraint tvdesk_users_stars_check check (stars_toward_reward >= 0);

create table if not exists public.tvdesk_posts (
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

create table if not exists public.tvdesk_user_achievements (
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

create table if not exists public.tvdesk_star_ledger (
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

create table if not exists public.tvdesk_reward_redemptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.tvdesk_users (id) on delete cascade,
  reward_title text not null,
  reward_target int not null check (reward_target > 0),
  stars_spent int not null check (stars_spent > 0),
  redeemed_at timestamptz not null default now()
);

create unique index if not exists tvdesk_star_task_once
  on public.tvdesk_star_ledger (user_id, task_id, day)
  where reason = 'task_complete';
create unique index if not exists tvdesk_star_post_once
  on public.tvdesk_star_ledger (user_id, post_id)
  where reason = 'post_bonus';
create index if not exists tvdesk_star_user_created
  on public.tvdesk_star_ledger (user_id, created_at desc);
create index if not exists tvdesk_posts_user_active
  on public.tvdesk_posts (user_id, posted_on desc)
  where active = true and removed_at is null;
create index if not exists tvdesk_achievements_user_sort
  on public.tvdesk_user_achievements (user_id, sort_order)
  where active = true;
create index if not exists tvdesk_logs_day on public.tvdesk_task_logs (day);
create index if not exists tvdesk_tasks_user on public.tvdesk_tasks (user_id) where active = true;

create or replace view public.tvdesk_posts_active as
select *
from public.tvdesk_posts
where active = true
  and removed_at is null
  and (expires_on is null or expires_on >= current_date);

create or replace function public.tvdesk_refresh_user_stats(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_total int;
  v_last_day date;
  v_cursor date;
  v_streak int := 0;
  v_best int;
begin
  select greatest(coalesce(sum(stars), 0), 0)::int
    into v_total
    from public.tvdesk_star_ledger
   where user_id = p_user_id;

  select max(l.day)
    into v_last_day
    from public.tvdesk_task_logs l
    join public.tvdesk_tasks t on t.id = l.task_id
   where t.user_id = p_user_id
     and l.count >= t.goal_count;

  if v_last_day is not null and v_last_day >= current_date - 1 then
    v_cursor := v_last_day;
    loop
      exit when not exists (
        select 1
          from public.tvdesk_task_logs l
          join public.tvdesk_tasks t on t.id = l.task_id
         where t.user_id = p_user_id
           and l.day = v_cursor
           and l.count >= t.goal_count
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
end;
$$;

create or replace function public.tvdesk_sync_task_star()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_task_id uuid;
  v_day date;
  v_count int;
  v_user_id uuid;
  v_goal int;
begin
  v_task_id := coalesce(new.task_id, old.task_id);
  v_day := coalesce(new.day, old.day);
  v_count := case when tg_op = 'DELETE' then 0 else new.count end;

  select user_id, goal_count into v_user_id, v_goal
    from public.tvdesk_tasks
   where id = v_task_id;

  if v_user_id is null then
    return coalesce(new, old);
  end if;

  if v_count >= v_goal then
    insert into public.tvdesk_star_ledger (user_id, stars, reason, task_id, day, note)
    values (v_user_id, 1, 'task_complete', v_task_id, v_day, 'Task completed')
    on conflict do nothing;
  else
    delete from public.tvdesk_star_ledger
     where user_id = v_user_id
       and task_id = v_task_id
       and day = v_day
       and reason = 'task_complete';
  end if;

  perform public.tvdesk_refresh_user_stats(v_user_id);
  return coalesce(new, old);
end;
$$;

drop trigger if exists tvdesk_task_star_trigger on public.tvdesk_task_logs;
create trigger tvdesk_task_star_trigger
after insert or update or delete on public.tvdesk_task_logs
for each row execute function public.tvdesk_sync_task_star();

create or replace function public.tvdesk_prepare_post()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.posted_on := coalesce(new.posted_on, current_date);
  if new.duration = 'today' then
    new.expires_on := new.posted_on;
  elsif new.duration = '3_days' then
    new.expires_on := new.posted_on + 2;
  else
    new.expires_on := null;
  end if;
  return new;
end;
$$;

drop trigger if exists tvdesk_prepare_post_trigger on public.tvdesk_posts;
create trigger tvdesk_prepare_post_trigger
before insert or update of duration, posted_on on public.tvdesk_posts
for each row execute function public.tvdesk_prepare_post();

create or replace function public.tvdesk_apply_post_bonus()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user record;
begin
  if new.bonus_stars <= 0 then
    return new;
  end if;

  for v_user in
    select id
      from public.tvdesk_users
     where active = true
       and (new.user_id is null or id = new.user_id)
  loop
    insert into public.tvdesk_star_ledger (user_id, stars, reason, post_id, day, note)
    values (v_user.id, new.bonus_stars, 'post_bonus', new.id, new.posted_on, new.message)
    on conflict do nothing;
    perform public.tvdesk_refresh_user_stats(v_user.id);
  end loop;
  return new;
end;
$$;

drop trigger if exists tvdesk_post_bonus_trigger on public.tvdesk_posts;
create trigger tvdesk_post_bonus_trigger
after insert on public.tvdesk_posts
for each row execute function public.tvdesk_apply_post_bonus();

create or replace function public.tvdesk_redeem_reward(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user public.tvdesk_users%rowtype;
begin
  select * into v_user
    from public.tvdesk_users
   where id = p_user_id
   for update;

  if v_user.id is null or v_user.reward_target <= 0 or v_user.stars_toward_reward < v_user.reward_target then
    raise exception 'Reward is not ready to redeem';
  end if;

  insert into public.tvdesk_reward_redemptions
    (user_id, reward_title, reward_target, stars_spent)
  values
    (v_user.id, v_user.reward_title, v_user.reward_target, v_user.reward_target);

  insert into public.tvdesk_star_ledger
    (user_id, stars, reason, note)
  values
    (v_user.id, -v_user.reward_target, 'redeem', v_user.reward_title);

  perform public.tvdesk_refresh_user_stats(v_user.id);
end;
$$;

-- Backfill one star for each historical completed task-day.
insert into public.tvdesk_star_ledger (user_id, stars, reason, task_id, day, note)
select t.user_id, 1, 'task_complete', l.task_id, l.day, 'Historical task completion'
  from public.tvdesk_task_logs l
  join public.tvdesk_tasks t on t.id = l.task_id
 where l.count >= t.goal_count
on conflict do nothing;

do $$
declare
  v_user record;
begin
  for v_user in select id from public.tvdesk_users loop
    perform public.tvdesk_refresh_user_stats(v_user.id);
  end loop;
end;
$$;

update public.tvdesk_settings
   set meta = coalesce(meta, '{}'::jsonb) || '{"tv_experience":"family"}'::jsonb
 where id = 1
   and not (coalesce(meta, '{}'::jsonb) ? 'tv_experience');

alter table public.tvdesk_posts enable row level security;
alter table public.tvdesk_user_achievements enable row level security;
alter table public.tvdesk_star_ledger enable row level security;
alter table public.tvdesk_reward_redemptions enable row level security;

drop policy if exists "anon all tvdesk_posts" on public.tvdesk_posts;
create policy "anon all tvdesk_posts" on public.tvdesk_posts for all to anon using (true) with check (true);
drop policy if exists "anon all tvdesk_achievements" on public.tvdesk_user_achievements;
create policy "anon all tvdesk_achievements" on public.tvdesk_user_achievements for all to anon using (true) with check (true);
drop policy if exists "anon all tvdesk_star_ledger" on public.tvdesk_star_ledger;
create policy "anon all tvdesk_star_ledger" on public.tvdesk_star_ledger for all to anon using (true) with check (true);
drop policy if exists "anon all tvdesk_redemptions" on public.tvdesk_reward_redemptions;
create policy "anon all tvdesk_redemptions" on public.tvdesk_reward_redemptions for all to anon using (true) with check (true);

grant select, insert, update, delete on public.tvdesk_posts to anon;
grant select, insert, update, delete on public.tvdesk_user_achievements to anon;
grant select, insert, update, delete on public.tvdesk_star_ledger to anon;
grant select, insert, update, delete on public.tvdesk_reward_redemptions to anon;
grant select on public.tvdesk_posts_active to anon;
grant execute on function public.tvdesk_redeem_reward(uuid) to anon;
