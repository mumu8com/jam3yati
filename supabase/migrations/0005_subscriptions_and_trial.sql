-- Jam3yati subscription / 3-month trial
revoke execute on function public.add_member_by_email(uuid,text,integer) from anon, authenticated;

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references public.profiles(id) on delete cascade,
  plan text not null default 'pro',
  status text not null default 'trialing',
  trial_started_at timestamptz not null default now(),
  trial_ends_at timestamptz not null default (now() + interval '3 months'),
  amount numeric(12,2) not null default 0,
  currency text not null default 'LYD',
  next_billing_at timestamptz,
  provider text,
  provider_customer_id text,
  provider_subscription_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint subscriptions_plan_check check (plan in ('pro')),
  constraint subscriptions_status_check check (status in ('trialing','active','past_due','cancelled','expired')),
  constraint subscriptions_amount_check check (amount >= 0)
);

alter table public.subscriptions enable row level security;

create policy "subscriptions_select_self" on public.subscriptions
for select to authenticated
using ((select auth.uid()) = user_id);

create policy "subscriptions_insert_self" on public.subscriptions
for insert to authenticated
with check ((select auth.uid()) = user_id);

create policy "subscriptions_update_self" on public.subscriptions
for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

create index if not exists subscriptions_user_id_idx on public.subscriptions(user_id);

insert into public.subscriptions (user_id, status, trial_started_at, trial_ends_at)
select id, 'trialing', now(), now() + interval '3 months'
from public.profiles
on conflict (user_id) do nothing;
