-- Jam3yati subscription/trial infrastructure
create table if not exists public.subscription_payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  amount numeric not null check (amount > 0),
  currency text not null default 'LYD',
  provider text not null,
  gateway text,
  status text not null default 'pending' check (status in ('pending','paid','failed','cancelled')),
  provider_transaction_id text,
  reference text unique,
  checkout_url text,
  created_at timestamptz not null default now(),
  paid_at timestamptz
);

alter table public.subscription_payments enable row level security;

drop policy if exists subscription_payments_select_self on public.subscription_payments;
create policy subscription_payments_select_self
on public.subscription_payments for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists subscriptions_insert_self on public.subscriptions;
drop policy if exists subscriptions_update_self on public.subscriptions;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  insert into public.profiles (id, full_name, phone)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', ''),
    coalesce(new.raw_user_meta_data ->> 'phone', new.phone)
  )
  on conflict (id) do nothing;

  insert into public.subscriptions (
    user_id, plan, status, trial_started_at, trial_ends_at, amount, currency
  )
  values (
    new.id, 'pro', 'trialing', now(), now() + interval '90 days', 10, 'LYD'
  )
  on conflict (user_id) do nothing;

  return new;
end;
$function$;

insert into public.subscriptions (
  user_id, plan, status, trial_started_at, trial_ends_at, amount, currency
)
select p.id, 'pro', 'trialing', now(), now() + interval '90 days', 10, 'LYD'
from public.profiles p
left join public.subscriptions s on s.user_id = p.id
where s.user_id is null;
