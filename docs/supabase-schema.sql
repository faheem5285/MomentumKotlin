-- Momentum Phase 8 schema. Run after enabling Supabase Auth.
-- The Android app must use only the publishable key. Never expose a service key.

create extension if not exists pgcrypto;

create table if not exists public.momentum_habits (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  description text not null default '',
  icon text not null default 'star',
  color_index integer not null default 0,
  time_of_day integer not null default 0,
  days_mask integer not null default 127,
  archived boolean not null default false,
  created_epoch_day bigint not null default 0,
  schedule_type text not null default 'specific_days',
  frequency_per_week integer not null default 0,
  every_n_days integer not null default 1,
  target_quantity integer not null default 1,
  quantity_unit text not null default '',
  timer_minutes integer not null default 0,
  category text not null default 'General',
  streak_freezes integer not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_completions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  habit_id uuid not null,
  epoch_day bigint not null,
  note text not null default '',
  quantity integer not null default 1,
  duration_seconds bigint not null default 0,
  completed_at_epoch_millis bigint not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_habit_skips (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  habit_id uuid not null,
  epoch_day bigint not null,
  reason text not null default 'freeze',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_habit_reminders (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  habit_id uuid not null,
  hour integer not null,
  minute integer not null,
  days_mask integer not null default 127,
  enabled boolean not null default true,
  date_epoch_day bigint,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_expenses (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null,
  amount_minor_units bigint not null,
  category text not null,
  epoch_day bigint not null,
  account text not null default 'Cash',
  recurring_rule text not null default '',
  category_icon text not null default '',
  category_color integer not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_category_budgets (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  category text not null,
  month_key text not null,
  limit_minor_units bigint not null,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1,
  unique(user_id, category, month_key)
);

create table if not exists public.momentum_loans (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  person text not null,
  amount_minor_units bigint not null,
  lent boolean not null,
  note text not null default '',
  epoch_day bigint not null,
  settled boolean not null default false,
  due_epoch_day bigint,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_loan_payments (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  loan_id uuid not null,
  amount_minor_units bigint not null,
  epoch_day bigint not null,
  note text not null default '',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_files (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  uri text not null,
  mime text not null,
  added_epoch_day bigint not null,
  tags text not null default '',
  folder text not null default '',
  last_accessed_epoch_day bigint not null default 0,
  missing boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_todos (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null,
  notes text not null default '',
  due_epoch_day bigint not null,
  due_epoch_millis bigint,
  completed boolean not null default false,
  category text not null default 'Personal',
  priority integer not null default 1,
  reminder_hour integer,
  reminder_minute integer,
  repeat_rule text not null default '',
  project text not null default '',
  labels text not null default '',
  parent_id uuid,
  sort_order bigint not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_vault_entries (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  nonce bytea not null,
  ciphertext bytea not null,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'momentum_habits', 'momentum_completions', 'momentum_habit_skips',
    'momentum_habit_reminders', 'momentum_expenses', 'momentum_category_budgets',
    'momentum_loans', 'momentum_loan_payments', 'momentum_files',
    'momentum_todos', 'momentum_vault_entries'
  ] loop
    execute format('create index if not exists %I_user_updated_idx on public.%I(user_id, updated_at)', table_name, table_name);
    execute format('create index if not exists %I_user_deleted_idx on public.%I(user_id, deleted_at)', table_name, table_name);
    execute format('alter table public.%I enable row level security', table_name);
    execute format('drop policy if exists %I_owner_select on public.%I', table_name, table_name);
    execute format('drop policy if exists %I_owner_insert on public.%I', table_name, table_name);
    execute format('drop policy if exists %I_owner_update on public.%I', table_name, table_name);
    execute format('drop policy if exists %I_owner_delete on public.%I', table_name, table_name);
    execute format('create policy %I_owner_select on public.%I for select using (user_id = auth.uid())', table_name, table_name);
    execute format('create policy %I_owner_insert on public.%I for insert with check (user_id = auth.uid())', table_name, table_name);
    execute format('create policy %I_owner_update on public.%I for update using (user_id = auth.uid()) with check (user_id = auth.uid())', table_name, table_name);
    execute format('create policy %I_owner_delete on public.%I for delete using (user_id = auth.uid())', table_name, table_name);
  end loop;
end $$;
