-- Repairs the project wipe marker and installs the write lock used during a
-- project-wide cleanup. Safe to run repeatedly.

create table if not exists public.momentum_wipe_state (
  id smallint primary key check (id = 1),
  generation bigint not null default 0,
  wipe_in_progress boolean not null default false,
  updated_at timestamptz not null default now()
);

alter table public.momentum_wipe_state
  add column if not exists wipe_in_progress boolean not null default false;
insert into public.momentum_wipe_state (id, generation)
values (1, 0)
on conflict (id) do nothing;

alter table public.momentum_wipe_state enable row level security;
drop policy if exists momentum_wipe_state_read on public.momentum_wipe_state;
create policy momentum_wipe_state_read on public.momentum_wipe_state
  for select to authenticated using (true);
grant select on public.momentum_wipe_state to authenticated;

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

-- Update existing record-table policies as well as policies created by the
-- full schema. Reads remain available; app writes pause while the wipe runs.
do $$
declare
  table_name text;
begin
  foreach table_name in array array[
    'momentum_habits', 'momentum_completions', 'momentum_habit_skips',
    'momentum_habit_reminders', 'momentum_expenses', 'momentum_category_budgets',
    'momentum_loan_people', 'momentum_loans', 'momentum_loan_attachments',
    'momentum_loan_payments', 'momentum_contacts', 'momentum_files',
    'momentum_todos', 'momentum_life_rules', 'momentum_life_rule_files',
    'momentum_custom_notifications', 'momentum_vault_entries'
  ] loop
    if to_regclass('public.' || quote_ident(table_name)) is not null then
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_insert', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_update', table_name);
    execute format('drop policy if exists %I on public.%I', table_name || '_owner_delete', table_name);
    execute format('create policy %I on public.%I for insert with check (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_insert', table_name);
    execute format('create policy %I on public.%I for update using (user_id = auth.uid() and public.momentum_sync_writes_allowed()) with check (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_update', table_name);
    execute format('create policy %I on public.%I for delete using (user_id = auth.uid() and public.momentum_sync_writes_allowed())', table_name || '_owner_delete', table_name);
    execute format('drop policy if exists %I on public.%I', 'momentum_wipe_guard_insert', table_name);
    execute format('drop policy if exists %I on public.%I', 'momentum_wipe_guard_update', table_name);
    execute format('drop policy if exists %I on public.%I', 'momentum_wipe_guard_delete', table_name);
    execute format('create policy %I on public.%I as restrictive for insert to authenticated with check (public.momentum_sync_writes_allowed())', 'momentum_wipe_guard_insert', table_name);
    execute format('create policy %I on public.%I as restrictive for update to authenticated using (public.momentum_sync_writes_allowed()) with check (public.momentum_sync_writes_allowed())', 'momentum_wipe_guard_update', table_name);
    execute format('create policy %I on public.%I as restrictive for delete to authenticated using (public.momentum_sync_writes_allowed())', 'momentum_wipe_guard_delete', table_name);
    end if;
  end loop;
end $$;

do $$
begin
  if to_regclass('public.momentum_vault_keys') is not null then
drop policy if exists momentum_vault_keys_owner_insert on public.momentum_vault_keys;
drop policy if exists momentum_vault_keys_owner_update on public.momentum_vault_keys;
drop policy if exists momentum_vault_keys_owner_delete on public.momentum_vault_keys;
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
  end if;
end $$;

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

notify pgrst, 'reload schema';
