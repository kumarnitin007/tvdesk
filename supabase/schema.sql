-- TV Desk. Run once in the Supabase SQL editor.
-- Running this again deletes the board and reseeds the sample.
-- Postgres stores these names in lowercase: tvdesk_users, tvdesk_tasks, and so on.
--
-- Extra fields can go in the meta jsonb column on any table without a new migration.
--
-- cadence on tasks and schedule lines:
--   daily      every day
--   weekly     weekdays, 0 = Sunday through 6 = Saturday
--   periodic   every every_n_days, counting from anchor_date
--   once       only on on_date
--
-- tvdesk_awards.show_when:
--   all_done   after every task due today is marked done
--   min_done   after at least min_done tasks are done today
--   always     included whenever that user's award panel is open
-- user_id null on an award means every user can earn it.

drop table if exists public.tvdesk_task_logs cascade;
drop table if exists public.tvdesk_awards cascade;
drop table if exists public.tvdesk_schedule_items cascade;
drop table if exists public.tvdesk_tasks cascade;
drop table if exists public.tvdesk_settings cascade;
drop table if exists public.tvdesk_users cascade;
drop table if exists public.task_logs cascade;
drop table if exists public.schedule_items cascade;
drop table if exists public.tasks cascade;
drop table if exists public.settings cascade;

create table public.tvdesk_users (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  sort_order int not null default 0,
  active boolean not null default true,
  meta jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table public.tvdesk_settings (
  id int primary key,
  theme text not null default 'cards',
  city text not null default 'Bothell',
  countdown_label text not null default 'SAT',
  countdown_date date,
  active_user_id uuid references public.tvdesk_users (id) on delete set null,
  meta jsonb not null default '{}'::jsonb
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

alter table public.tvdesk_users enable row level security;
alter table public.tvdesk_settings enable row level security;
alter table public.tvdesk_tasks enable row level security;
alter table public.tvdesk_schedule_items enable row level security;
alter table public.tvdesk_task_logs enable row level security;
alter table public.tvdesk_awards enable row level security;

create policy "anon all tvdesk_users" on public.tvdesk_users for all to anon using (true) with check (true);
create policy "anon all tvdesk_settings" on public.tvdesk_settings for all to anon using (true) with check (true);
create policy "anon all tvdesk_tasks" on public.tvdesk_tasks for all to anon using (true) with check (true);
create policy "anon all tvdesk_schedule" on public.tvdesk_schedule_items for all to anon using (true) with check (true);
create policy "anon all tvdesk_logs" on public.tvdesk_task_logs for all to anon using (true) with check (true);
create policy "anon all tvdesk_awards" on public.tvdesk_awards for all to anon using (true) with check (true);

grant usage on schema public to anon;
grant select, insert, update, delete on public.tvdesk_users to anon;
grant select, insert, update, delete on public.tvdesk_settings to anon;
grant select, insert, update, delete on public.tvdesk_tasks to anon;
grant select, insert, update, delete on public.tvdesk_schedule_items to anon;
grant select, insert, update, delete on public.tvdesk_task_logs to anon;
grant select, insert, update, delete on public.tvdesk_awards to anon;

insert into public.tvdesk_users (id, name, sort_order) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Kaashvi', 1);

insert into public.tvdesk_settings (id, theme, city, countdown_label, active_user_id)
values (1, 'cards', 'Bothell', 'SAT', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');

insert into public.tvdesk_tasks (id, user_id, label, detail, icon, sort_order, cadence, goal_count) values
  ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'SAT prep', '1 set', 'document', 1, 'daily', 1),
  ('22222222-2222-4222-8222-222222222222', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Maths', 'Chapter 6', 'math', 2, 'daily', 1),
  ('33333333-3333-4333-8333-333333333333', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Game prep', 'Scrim notes', 'game', 3, 'daily', 1),
  ('44444444-4444-4444-8444-444444444444', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Nail art', 'Practice design', 'art', 4, 'daily', 1);

insert into public.tvdesk_schedule_items (user_id, title, time_label, sort_order, task_id, cadence, on_date) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'SAT prep', '9:00 AM', 1, '11111111-1111-4111-8111-111111111111', 'once', current_date),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Game scrimmage', '4:00 PM', 2, '33333333-3333-4333-8333-333333333333', 'once', current_date),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'SAT practice test', '10:00 AM', 1, '11111111-1111-4111-8111-111111111111', 'once', current_date + 1),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Nail art class', '2:00 PM', 2, '44444444-4444-4444-8444-444444444444', 'once', current_date + 1);

insert into public.tvdesk_task_logs (task_id, day, count)
values ('11111111-1111-4111-8111-111111111111', current_date, 1);

insert into public.tvdesk_awards (user_id, title, message, minutes, icon, show_when, sort_order)
values (null, 'TV time', 'You have been awarded with 60 mins TV time today', 60, 'star', 'all_done', 1);
