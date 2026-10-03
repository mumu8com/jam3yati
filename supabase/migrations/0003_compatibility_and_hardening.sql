-- Jam3yati compatibility and production hardening
alter table public.associations
  add column if not exists owner_id uuid references auth.users(id),
  add column if not exists installment numeric(12,2),
  add column if not exists frequency text,
  add column if not exists member_count integer;

update public.associations
set owner_id = coalesce(owner_id, created_by),
    installment = coalesce(installment, contribution),
    frequency = coalesce(frequency, payment_frequency),
    member_count = coalesce(member_count, (select count(*) from public.memberships m where m.association_id = associations.id));

alter table public.associations alter column owner_id set not null;
alter table public.associations alter column installment set not null;
alter table public.associations alter column frequency set not null;
alter table public.associations alter column member_count set not null;

create table if not exists public.association_members (
 id uuid primary key default gen_random_uuid(),
 association_id uuid not null references public.associations(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade,
 receiving_order integer not null,
 created_at timestamptz not null default now(),
 unique (association_id,user_id),
 unique (association_id,receiving_order)
);

insert into public.association_members(association_id,user_id,receiving_order)
select association_id,user_id,slot_number from public.memberships
on conflict (association_id,user_id) do update set receiving_order=excluded.receiving_order;

create table if not exists public.cycles (
 id uuid primary key default gen_random_uuid(),
 association_id uuid not null references public.associations(id) on delete cascade,
 cycle_number integer not null,
 beneficiary_user_id uuid references auth.users(id),
 due_date date not null,
 amount numeric(12,2) not null check(amount>0),
 created_at timestamptz not null default now(),
 unique(association_id,cycle_number)
);

create table if not exists public.installments (
 id uuid primary key default gen_random_uuid(),
 cycle_id uuid not null references public.cycles(id) on delete cascade,
 member_id uuid not null references public.association_members(id) on delete cascade,
 due_date date not null,
 amount numeric(12,2) not null check(amount>0),
 paid_amount numeric(12,2) not null default 0 check(paid_amount>=0),
 paid_at timestamptz,
 receipt_no text,
 status text not null default 'unpaid' check(status in('unpaid','partial','paid')),
 created_at timestamptz not null default now(),
 unique(cycle_id,member_id)
);

alter table public.payments
 add column if not exists installment_id uuid references public.installments(id) on delete set null,
 add column if not exists member_id uuid references public.association_members(id) on delete set null,
 add column if not exists receipt_no text,
 add column if not exists notes text,
 add column if not exists created_by uuid references auth.users(id);

create unique index if not exists payments_receipt_no_uidx on public.payments(receipt_no) where receipt_no is not null;

create or replace function public.is_manager()
returns boolean language sql stable security definer set search_path=public as $$
 select exists(select 1 from public.profiles where id=auth.uid() and role='admin');
$$;

grant execute on function public.is_manager() to authenticated;

alter table public.association_members enable row level security;
alter table public.cycles enable row level security;
alter table public.installments enable row level security;

drop policy if exists "association members read" on public.association_members;
create policy "association members read" on public.association_members for select to authenticated using(
 user_id=auth.uid() or exists(select 1 from public.associations a where a.id=association_id and (a.owner_id=auth.uid() or public.is_manager()))
);

drop policy if exists "cycles read" on public.cycles;
create policy "cycles read" on public.cycles for select to authenticated using(
 exists(select 1 from public.associations a where a.id=association_id and (a.owner_id=auth.uid() or public.is_manager()))
);

drop policy if exists "installments read" on public.installments;
create policy "installments read" on public.installments for select to authenticated using(
 exists(select 1 from public.cycles c join public.associations a on a.id=c.association_id where c.id=cycle_id and (a.owner_id=auth.uid() or public.is_manager()))
);

create index if not exists association_members_association_order_idx on public.association_members(association_id,receiving_order);
create index if not exists cycles_association_due_idx on public.cycles(association_id,due_date);
create index if not exists installments_cycle_due_idx on public.installments(cycle_id,due_date);

create or replace function public.generate_association_schedule(p_association_id uuid)
returns integer language plpgsql security definer set search_path=public as $$
declare a public.associations%rowtype; v_cycle_id uuid; v_date date; m record; n integer:=0; i integer;
begin
 select * into a from public.associations where id=p_association_id;
 if not found then raise exception 'الجمعية غير موجودة'; end if;
 if a.owner_id<>auth.uid() and not public.is_manager() then raise exception 'لا تملك صلاحية'; end if;
 if exists(select 1 from public.cycles where association_id=a.id) then return 0; end if;
 for i in 1..a.cycle_count loop
  v_date:=a.start_date+case when a.frequency='weekly' then (i-1)*7 else (i-1)*30 end;
  insert into public.cycles(association_id,cycle_number,beneficiary_user_id,due_date,amount)
  values(a.id,i,(select am.user_id from public.association_members am where am.association_id=a.id and am.receiving_order=i limit 1),v_date,a.installment*a.member_count)
  returning id into v_cycle_id;
  for m in select id from public.association_members where association_id=a.id order by receiving_order loop
   insert into public.installments(cycle_id,member_id,due_date,amount) values(v_cycle_id,m.id,v_date,a.installment);
   n:=n+1;
  end loop;
 end loop;
 return n;
end;
$$;

create or replace function public.record_payment(p_installment_id uuid,p_member_id uuid,p_amount numeric,p_notes text default null)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_user uuid:=auth.uid(); v_owner uuid; v_expected numeric; v_paid numeric; v_receipt text; v_payment_id uuid;
begin
 if v_user is null then raise exception 'غير مصرح'; end if;
 select a.owner_id,i.amount,i.paid_amount into v_owner,v_expected,v_paid
 from public.installments i join public.cycles c on c.id=i.cycle_id join public.associations a on a.id=c.association_id
 where i.id=p_installment_id and i.member_id=p_member_id for update;
 if not found then raise exception 'القسط غير موجود'; end if;
 if v_owner<>v_user and not public.is_manager() then raise exception 'لا تملك صلاحية تسجيل الدفعة'; end if;
 if p_amount<=0 or v_paid+p_amount>v_expected then raise exception 'قيمة الدفعة غير صحيحة'; end if;
 v_receipt:='J3-'||to_char(clock_timestamp(),'YYYYMMDDHH24MISSMS');
 insert into public.payments(association_id,installment_id,member_id,amount,paid_at,receipt_no,receipt_number,notes,created_by,membership_id,due_date,status)
 select c.association_id,p_installment_id,p_member_id,p_amount,now(),v_receipt,v_receipt,p_notes,v_user,m.id,i.due_date,
 case when v_paid+p_amount>=v_expected then 'paid' else 'pending' end
 from public.installments i join public.cycles c on c.id=i.cycle_id join public.association_members am on am.id=i.member_id
 left join public.memberships m on m.association_id=c.association_id and m.user_id=am.user_id
 where i.id=p_installment_id;
 select id into v_payment_id from public.payments where receipt_no=v_receipt limit 1;
 update public.installments set paid_amount=v_paid+p_amount,paid_at=now(),receipt_no=v_receipt,
 status=case when v_paid+p_amount>=v_expected then 'paid' when v_paid+p_amount>0 then 'partial' else 'unpaid' end
 where id=p_installment_id;
 return jsonb_build_object('payment_id',v_payment_id,'receipt_no',v_receipt,'paid_amount',v_paid+p_amount,'remaining',v_expected-(v_paid+p_amount));
end;
$$;

grant execute on function public.generate_association_schedule(uuid) to authenticated;
grant execute on function public.record_payment(uuid,uuid,numeric,text) to authenticated;
