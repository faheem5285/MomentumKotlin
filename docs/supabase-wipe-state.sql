-- Repairs the project-wide wipe marker used to trigger local device resets.
-- Safe to run even if the marker already exists.

create table if not exists public.momentum_wipe_state (
  id smallint primary key check (id = 1),
  generation bigint not null default 0,
  updated_at timestamptz not null default now()
);

insert into public.momentum_wipe_state (id, generation)
values (1, 0)
on conflict (id) do nothing;

alter table public.momentum_wipe_state enable row level security;
drop policy if exists momentum_wipe_state_read on public.momentum_wipe_state;
create policy momentum_wipe_state_read on public.momentum_wipe_state
  for select to authenticated using (true);
grant select on public.momentum_wipe_state to authenticated;

notify pgrst, 'reload schema';
