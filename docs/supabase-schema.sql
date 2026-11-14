-- Momentum Phase 8 schema. Run after enabling Supabase Auth.
-- The Android app must use only the publishable key. Never expose a service key.

create extension if not exists pgcrypto;

-- Project-wide local-reset generation. Authenticated clients may read the
-- generation; only the service-role Edge Function can advance it.
create table if not exists public.momentum_wipe_state (
  id smallint primary key check (id = 1),
  generation bigint not null default 0,
  wipe_in_progress boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table public.momentum_wipe_state
  add column if not exists wipe_in_progress boolean not null default false;
insert into public.momentum_wipe_state (id, generation)
values (1, 0) on conflict (id) do nothing;
alter table public.momentum_wipe_state enable row level security;
drop policy if exists momentum_wipe_state_read on public.momentum_wipe_state;
create policy momentum_wipe_state_read on public.momentum_wipe_state
  for select to authenticated using (true);
grant select on public.momentum_wipe_state to authenticated;

-- Used by RLS to block app writes while the admin wipe is running. The
-- security-definer function can read the marker despite its own RLS policy.
create or replace function public.momentum_sync_writes_allowed()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select not coalesce(
    (select wipe_in_progress from public.momentum_wipe_state where id = 1),
    true
  );
$$;
revoke all on function public.momentum_sync_writes_allowed() from public, anon;
grant execute on function public.momentum_sync_writes_allowed() to authenticated;

-- Private bucket for file contents. File metadata remains in momentum_files;
-- object ownership is enforced by Supabase Storage RLS.
insert into storage.buckets (id, name, public)
values ('momentum-files', 'momentum-files', false)
on conflict (id) do update set public = false;

drop policy if exists momentum_files_read on storage.objects;
drop policy if exists momentum_files_insert on storage.objects;
drop policy if exists momentum_files_update on storage.objects;
drop policy if exists momentum_files_delete on storage.objects;
create policy momentum_files_read on storage.objects for select
  using (bucket_id = 'momentum-files' and owner_id = auth.uid()::text);
create policy momentum_files_insert on storage.objects for insert
  with check (bucket_id = 'momentum-files' and owner_id = auth.uid()::text);
create policy momentum_files_update on storage.objects for update
  using (bucket_id = 'momentum-files' and owner_id = auth.uid()::text)
  with check (bucket_id = 'momentum-files' and owner_id = auth.uid()::text);
create policy momentum_files_delete on storage.objects for delete
  using (bucket_id = 'momentum-files' and owner_id = auth.uid()::text);

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
  title text not null default '',
  status text not null default 'pending',
  person_id uuid,
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

create table if not exists public.momentum_loan_people (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  phone text not null default '',
  address text not null default '',
  profile_image_path text not null default '',
  notes text not null default '',
  archived boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

alter table public.momentum_loans
  add column if not exists title text not null default '',
  add column if not exists status text not null default 'pending',
  add column if not exists person_id uuid;
alter table public.momentum_loans
  drop constraint if exists momentum_loans_person_id_fkey;
alter table public.momentum_loans
  add constraint momentum_loans_person_id_fkey
  foreign key (person_id) references public.momentum_loan_people(id) on delete set null;

create table if not exists public.momentum_loan_attachments (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  loan_id uuid not null references public.momentum_loans(id) on delete cascade,
  name text not null,
  object_path text not null,
  mime text not null default '',
  added_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_contacts (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null,
  phone text not null default '',
  email text not null default '',
  address text not null default '',
  notes text not null default '',
  profile_image_path text not null default '',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_life_rules (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null,
  body text not null default '',
  kind text not null default 'Rule',
  pinned boolean not null default false,
  created_epoch_day bigint not null default 0,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_life_rule_files (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  rule_id uuid not null references public.momentum_life_rules(id) on delete cascade,
  name text not null,
  object_path text not null,
  mime text not null default '',
  folder_path text not null default '',
  added_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  device_id text not null,
  version bigint not null default 1
);

create table if not exists public.momentum_custom_notifications (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null,
  message text not null default '',
  repeat_mode text not null default 'once',
  hour integer not null default 9,
  minute integer not null default 0,
  date_epoch_day bigint,
  days_mask integer not null default 127,
  day_of_month integer not null default 1,
  enabled boolean not null default true,
  last_delivered_epoch_millis bigint not null default 0,
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

create table if not exists public.momentum_vault_keys (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  key_material text not null,
  created_at timestamptz not null default now()
);
comment on column public.momentum_vault_keys.key_material is
  'Account-recoverable Vault key material. RLS scopes it to the signed-in user; account or server-side access can decrypt Vault entries.';

do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'momentum_habits', 'momentum_completions', 'momentum_habit_skips',
    'momentum_habit_reminders', 'momentum_expenses', 'momentum_category_budgets',
    'momentum_loan_people', 'momentum_loans', 'momentum_loan_attachments', 'momentum_loan_payments',
    'momentum_contacts', 'momentum_files', 'momentum_todos', 'momentum_life_rules',
    'momentum_life_rule_files', 'momentum_custom_notifications', 'momentum_vault_entries'
  ] loop
    execute format('create index if not exists %I on public.%I(user_id, updated_at)', table_name || '_user_updated_idx', table_name);
    execute format('create index if not exists %I on public.%I(user_id, deleted_at)', table_name || '_user_deleted_idx', table_name);
    execute format('alter table public.%I enable row level security', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_select', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_insert', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_update', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_delete', table_name);
    execute format('create policy %I on public.%I for select using (user_id = auth.uid())', table_name || '_owner_select', table_name);
    execute format('create policy %I on public.%I for insert with check (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_insert', table_name);
    execute format('create policy %I on public.%I for update using (user_id = auth.uid() and public.momentum_sync_writes_allowed()) with check (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_update', table_name);
    execute format('create policy %I on public.%I for delete using (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_delete', table_name);
    execute format('drop policy if exists momentum_wipe_guard_insert on public.%I', table_name);
    execute format('drop policy if exists momentum_wipe_guard_update on public.%I', table_name);
    execute format('drop policy if exists momentum_wipe_guard_delete on public.%I', table_name);
    execute format('create policy momentum_wipe_guard_insert on public.%I as restrictive for insert to authenticated with check (public.momentum_sync_writes_allowed())', table_name);
    execute format('create policy momentum_wipe_guard_update on public.%I as restrictive for update to authenticated using (public.momentum_sync_writes_allowed()) with check (public.momentum_sync_writes_allowed())', table_name);
    execute format('create policy momentum_wipe_guard_delete on public.%I as restrictive for delete to authenticated using (public.momentum_sync_writes_allowed())', table_name);
  end loop;
end $$;

alter table public.momentum_vault_keys enable row level security;
drop policy if exists momentum_vault_keys_owner_select on public.momentum_vault_keys;
drop policy if exists momentum_vault_keys_owner_insert on public.momentum_vault_keys;
drop policy if exists momentum_vault_keys_owner_update on public.momentum_vault_keys;
drop policy if exists momentum_vault_keys_owner_delete on public.momentum_vault_keys;
create policy momentum_vault_keys_owner_select on public.momentum_vault_keys
  for select using (user_id = auth.uid());
create policy momentum_vault_keys_owner_insert on public.momentum_vault_keys
  for insert with check (user_id = auth.uid() and public.momentum_sync_writes_allowed());
create policy momentum_vault_keys_owner_update on public.momentum_vault_keys
  for update using (user_id = auth.uid() and public.momentum_sync_writes_allowed())
  with check (user_id = auth.uid() and public.momentum_sync_writes_allowed());
create policy momentum_vault_keys_owner_delete on public.momentum_vault_keys
  for delete using (user_id = auth.uid() and public.momentum_sync_writes_allowed());
drop policy if exists momentum_wipe_guard_insert on public.momentum_vault_keys;
drop policy if exists momentum_wipe_guard_update on public.momentum_vault_keys;
drop policy if exists momentum_wipe_guard_delete on public.momentum_vault_keys;
create policy momentum_wipe_guard_insert on public.momentum_vault_keys
  as restrictive for insert to authenticated with check (public.momentum_sync_writes_allowed());
create policy momentum_wipe_guard_update on public.momentum_vault_keys
  as restrictive for update to authenticated using (public.momentum_sync_writes_allowed())
  with check (public.momentum_sync_writes_allowed());
create policy momentum_wipe_guard_delete on public.momentum_vault_keys
  as restrictive for delete to authenticated using (public.momentum_sync_writes_allowed());
grant select, insert, update, delete on public.momentum_vault_keys to authenticated;

-- User uploads/deletes are also blocked until a project wipe completes.
drop policy if exists momentum_files_insert on storage.objects;
drop policy if exists momentum_files_update on storage.objects;
drop policy if exists momentum_files_delete on storage.objects;
create policy momentum_files_insert on storage.objects for insert
  with check (bucket_id = 'momentum-files' and owner_id = auth.uid()::text and public.momentum_sync_writes_allowed());
create policy momentum_files_update on storage.objects for update
  using (bucket_id = 'momentum-files' and owner_id = auth.uid()::text and public.momentum_sync_writes_allowed())
  with check (bucket_id = 'momentum-files' and owner_id = auth.uid()::text and public.momentum_sync_writes_allowed());
create policy momentum_files_delete on storage.objects for delete
  using (bucket_id = 'momentum-files' and owner_id = auth.uid()::text and public.momentum_sync_writes_allowed());
drop policy if exists momentum_wipe_storage_guard_insert on storage.objects;
drop policy if exists momentum_wipe_storage_guard_update on storage.objects;
drop policy if exists momentum_wipe_storage_guard_delete on storage.objects;
create policy momentum_wipe_storage_guard_insert on storage.objects
  as restrictive for insert to authenticated
  with check (bucket_id <> 'momentum-files' or public.momentum_sync_writes_allowed());
create policy momentum_wipe_storage_guard_update on storage.objects
  as restrictive for update to authenticated
  using (bucket_id <> 'momentum-files' or public.momentum_sync_writes_allowed())
  with check (bucket_id <> 'momentum-files' or public.momentum_sync_writes_allowed());
create policy momentum_wipe_storage_guard_delete on storage.objects
  as restrictive for delete to authenticated
  using (bucket_id <> 'momentum-files' or public.momentum_sync_writes_allowed());

-- Make newly created tables visible to Supabase's PostgREST API immediately.
notify pgrst, 'reload schema';
