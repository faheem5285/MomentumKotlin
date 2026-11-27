-- Run in the Supabase SQL Editor after enabling email and Google Auth providers.
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '',
  email text not null default '',
  avatar_url text not null default '',
  phone text not null default '',
  bio text not null default '',
  member_since bigint not null default 0,
  updated_at timestamptz not null default now()
);
alter table public.profiles
  add column if not exists email text not null default '',
  add column if not exists member_since bigint not null default 0;

alter table public.profiles enable row level security;
revoke all on public.profiles from public, anon;
grant select, insert, update, delete on public.profiles to authenticated;

-- Use the same policy names as docs/supabase-schema.sql so this standalone
-- script cannot leave a second, more permissive policy set behind. When the
-- canonical wipe guard is installed, keep it in the write policies; without
-- it, the owner checks below are sufficient on a standalone project.
do $$
declare
  wipe_guard_installed boolean := to_regprocedure('public.momentum_sync_writes_allowed()') is not null;
begin
  drop policy if exists "Users can read their own profile" on public.profiles;
  drop policy if exists "Users can create their own profile" on public.profiles;
  drop policy if exists "Users can update their own profile" on public.profiles;
  drop policy if exists profiles_owner_select on public.profiles;
  drop policy if exists profiles_owner_insert on public.profiles;
  drop policy if exists profiles_owner_update on public.profiles;
  drop policy if exists profiles_owner_delete on public.profiles;

  create policy profiles_owner_select on public.profiles
    for select to authenticated using (id = auth.uid());

  if wipe_guard_installed then
    create policy profiles_owner_insert on public.profiles
      for insert to authenticated with check (id = auth.uid() and public.momentum_sync_writes_allowed());
    create policy profiles_owner_update on public.profiles
      for update to authenticated using (id = auth.uid() and public.momentum_sync_writes_allowed())
      with check (id = auth.uid() and public.momentum_sync_writes_allowed());
    create policy profiles_owner_delete on public.profiles
      for delete to authenticated using (id = auth.uid() and public.momentum_sync_writes_allowed());
  else
    create policy profiles_owner_insert on public.profiles
      for insert to authenticated with check (id = auth.uid());
    create policy profiles_owner_update on public.profiles
      for update to authenticated using (id = auth.uid())
      with check (id = auth.uid());
    create policy profiles_owner_delete on public.profiles
      for delete to authenticated using (id = auth.uid());
  end if;
end $$;

create or replace function public.set_profile_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_profile_updated_at();

-- Optional profile row creation for every new Auth user.
create or replace function public.create_profile_for_auth_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', ''))
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
  after insert on auth.users
  for each row execute function public.create_profile_for_auth_user();

-- The Profile screen calls this RPC after the user confirms account deletion.
-- It can delete only the currently authenticated user's Auth account.
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  requesting_user_id uuid := auth.uid();
begin
  if requesting_user_id is null then
    raise exception 'Authentication is required to delete an account.'
      using errcode = '28000';
  end if;

  delete from auth.users where id = requesting_user_id;
end;
$$;

revoke all on function public.delete_my_account() from public;
revoke all on function public.delete_my_account() from anon;
grant execute on function public.delete_my_account() to authenticated;

-- Optional avatar storage: create a private `avatars` bucket. Add authenticated
-- SELECT/INSERT/UPDATE/DELETE policies scoped to the first object path segment
-- (`storage.foldername(name))[1] = auth.uid()::text`). The app currently keeps
-- selected photos in app-private URI permissions until a bucket is configured.
