create extension if not exists pgcrypto;

create table if not exists public.profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 full_name text not null default '',
 phone text,
 role text not null default 'member' check (role in ('admin','member')),
 created_at timestamptz not null default now()
);

create table if not exists public.associations (
 id uuid primary key default gen_random_uuid(),
 name text not null,
 contribution numeric(12,2) not null check (contribution > 0),
 cycle_count integer not null check (cycle_count > 0),
 payment_frequency text not null default 'monthly' check (payment_frequency in ('weekly','monthly')),
 start_date date not null,
 created_by uuid not null references public.profiles(id),
 created_at timestamptz not null default now()
);

create table if not exists public.memberships (
 id uuid primary key default gen_random_uuid(),
 association_id uuid not null references public.associations(id) on delete cascade,
 user_id uuid not null references public.profiles(id) on delete cascade,
 slot_number integer not null,
 joined_at timestamptz not null default now(),
 unique (association_id, user_id),
 unique (association_id, slot_number)
);

create table if not exists public.payments (
 id uuid primary key default gen_random_uuid(),
 association_id uuid not null references public.associations(id) on delete cascade,
 membership_id uuid not null references public.memberships(id) on delete cascade,
 due_date date not null,
 amount numeric(12,2) not null check (amount > 0),
 paid_at timestamptz,
 status text not null default 'pending' check (status in ('pending','paid','late')),
 receipt_number text unique,
 created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.associations enable row level security;
alter table public.memberships enable row level security;
alter table public.payments enable row level security;

create policy "profiles own read" on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profiles own update" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy "members read associations" on public.associations for select to authenticated using (exists (select 1 from public.memberships m where m.association_id=id and m.user_id=(select auth.uid())) or created_by=(select auth.uid()));
create policy "members read memberships" on public.memberships for select to authenticated using (user_id=(select auth.uid()) or exists (select 1 from public.associations a where a.id=association_id and a.created_by=(select auth.uid())));
create policy "members read payments" on public.payments for select to authenticated using (exists (select 1 from public.memberships m where m.id=membership_id and m.user_id=(select auth.uid())) or exists (select 1 from public.associations a where a.id=association_id and a.created_by=(select auth.uid())));
