-- Optional schema only. Language sync is not enabled by the application.
-- Enable only after a versioned sync contract is implemented and tested.
create table if not exists public.language_vocabulary (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  word text not null,
  meaning text not null,
  part_of_speech text not null default '',
  example text not null default '',
  language text not null default 'en',
  tags text not null default '',
  difficulty integer not null default 1,
  mastered boolean not null default false,
  updated_at timestamptz not null default now()
);
alter table public.language_vocabulary enable row level security;
create policy "Users manage their own language vocabulary" on public.language_vocabulary
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.language_grammar_notes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  category text not null default 'Other',
  explanation text not null default '',
  correct_example text not null default '',
  incorrect_example text not null default '',
  common_mistake boolean not null default false,
  pinned boolean not null default false,
  tags text not null default '',
  updated_at timestamptz not null default now()
);
alter table public.language_grammar_notes enable row level security;
create policy "Users manage their own language grammar notes" on public.language_grammar_notes
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
