-- Momentum Phase 2: Supabase deployment verification (read-only).
-- Run in Supabase SQL Editor after applying docs/supabase-schema.sql.
-- This checks deployed schema/policy presence only. SQL Editor commonly runs
-- with elevated privileges and therefore does NOT prove end-user RLS isolation.
-- Complete the two-account app test described at the bottom as well.

with expected_tables(table_name) as (
  values
    ('momentum_habits'), ('momentum_completions'), ('momentum_habit_skips'),
    ('momentum_habit_reminders'), ('momentum_expenses'), ('momentum_category_budgets'),
    ('momentum_loan_people'), ('momentum_loans'), ('momentum_loan_attachments'),
    ('momentum_loan_payments'), ('momentum_contacts'), ('momentum_files'),
    ('momentum_todos'), ('momentum_life_rules'), ('momentum_life_rule_files'),
    ('momentum_custom_notifications'), ('momentum_vault_entries'),
    ('momentum_vault_keys')
),
table_status as (
  select e.table_name,
         coalesce(c.relrowsecurity, false) as rls_enabled,
         count(p.policyname) filter (where p.policyname like '%owner_select') as owner_select,
         count(p.policyname) filter (where p.policyname like '%owner_insert') as owner_insert,
         count(p.policyname) filter (where p.policyname like '%owner_update') as owner_update,
         count(p.policyname) filter (where p.policyname like '%owner_delete') as owner_delete
  from expected_tables e
  left join pg_namespace n on n.nspname = 'public'
  left join pg_class c on c.relnamespace = n.oid and c.relname = e.table_name
  left join pg_policies p on p.schemaname = 'public' and p.tablename = e.table_name
  group by e.table_name, c.relrowsecurity
)
select table_name,
       case when not rls_enabled then 'FAIL: RLS disabled or table missing'
            when owner_select = 0 or owner_insert = 0 or owner_update = 0 or owner_delete = 0
              then 'FAIL: one or more owner policies missing'
            else 'PASS: RLS enabled; owner policies present' end as result,
       owner_select, owner_insert, owner_update, owner_delete
from table_status
order by table_name;

-- Vault key table: account-only unlock intentionally remains enabled.
-- This confirms owner policies exist, not that the cloud operator cannot read
-- key_material. key_material currently contains the raw VMK encoded as Base64.
select 'momentum_vault_keys' as object_name,
       exists(select 1 from pg_policies where schemaname='public'
         and tablename='momentum_vault_keys' and policyname='momentum_vault_keys_owner_select')
       and exists(select 1 from pg_policies where schemaname='public'
         and tablename='momentum_vault_keys' and policyname='momentum_vault_keys_owner_insert')
       and exists(select 1 from pg_policies where schemaname='public'
         and tablename='momentum_vault_keys' and policyname='momentum_vault_keys_owner_update')
       and exists(select 1 from pg_policies where schemaname='public'
         and tablename='momentum_vault_keys' and policyname='momentum_vault_keys_owner_delete')
       as owner_policies_present;

-- Private storage bucket and expected ownership policies.
select
  exists(select 1 from storage.buckets where id='momentum-files' and public=false)
    as private_bucket_configured,
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects'
    and policyname='momentum_files_read') as read_policy_present,
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects'
    and policyname='momentum_files_insert') as insert_policy_present,
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects'
    and policyname='momentum_files_update') as update_policy_present,
  exists(select 1 from pg_policies where schemaname='storage' and tablename='objects'
    and policyname='momentum_files_delete') as delete_policy_present;

-- REQUIRED end-user isolation check (do this in the app, not as SQL Editor):
-- 1. Sign in as Account A, create a uniquely named test habit and upload a test file.
-- 2. Sign out; sign in as Account B. Confirm neither A's habit nor file appears.
-- 3. Create a B-only habit/file; sign back in as A. Confirm neither B item appears.
-- 4. Sign back into A and verify A's original data still exists.
-- Never use real Vault credentials or personal files for this test.
