-- Jam3yati phase 2
create or replace function public.record_payment(p_installment_id uuid,p_member_id uuid,p_amount numeric,p_notes text default null)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare v_user uuid:=auth.uid();v_owner uuid;v_expected numeric;v_paid numeric;v_receipt text;v_payment_id uuid;
begin
 if v_user is null then raise exception 'غير مصرح'; end if;
 select a.owner_id,i.amount,i.paid_amount into v_owner,v_expected,v_paid from installments i join cycles c on c.id=i.cycle_id join associations a on a.id=c.association_id where i.id=p_installment_id and i.member_id=p_member_id for update;
 if not found then raise exception 'القسط غير موجود'; end if;
 if v_owner<>v_user and not is_manager() then raise exception 'لا تملك صلاحية تسجيل الدفعة'; end if;
 if p_amount<=0 or v_paid+p_amount>v_expected then raise exception 'قيمة الدفعة غير صحيحة'; end if;
 v_receipt:='J3-'||to_char(clock_timestamp(),'YYYYMMDDHH24MISSMS');
 insert into payments(installment_id,member_id,amount,paid_at,receipt_no,notes,created_by) values(p_installment_id,p_member_id,p_amount,now(),v_receipt,p_notes,v_user) returning id into v_payment_id;
 update installments set paid_amount=v_paid+p_amount,paid_at=now(),receipt_no=v_receipt,status=case when v_paid+p_amount>=v_expected then 'paid' when v_paid+p_amount>0 then 'partial' else 'unpaid' end where id=p_installment_id;
 return jsonb_build_object('payment_id',v_payment_id,'receipt_no',v_receipt,'paid_amount',v_paid+p_amount,'remaining',v_expected-(v_paid+p_amount));
end $$;

create or replace function public.add_member_by_email(p_association_id uuid,p_email text,p_order integer)
returns jsonb language plpgsql security definer set search_path=public,auth as $$
declare v_user uuid;v_owner uuid;v_id uuid;
begin
 select owner_id into v_owner from public.associations where id=p_association_id;
 if v_owner is null or v_owner<>auth.uid() then raise exception 'لا تملك صلاحية إدارة هذه الجمعية'; end if;
 select id into v_user from auth.users where lower(email)=lower(trim(p_email)) limit 1;
 if v_user is null then raise exception 'لم يتم العثور على حساب بهذا البريد'; end if;
 if exists(select 1 from public.association_members where association_id=p_association_id and user_id=v_user) then raise exception 'العضو موجود بالفعل'; end if;
 insert into public.association_members(association_id,user_id,receiving_order) values(p_association_id,v_user,p_order) returning id into v_id;
 return jsonb_build_object('id',v_id,'user_id',v_user,'receiving_order',p_order);
end $$;
revoke all on function public.add_member_by_email(uuid,text,integer) from public;
grant execute on function public.add_member_by_email(uuid,text,integer) to authenticated;

create or replace function public.generate_association_schedule(p_association_id uuid)
returns integer language plpgsql security invoker set search_path=public as $$
declare a public.associations%rowtype;v_cycle_id uuid;v_date date;m record;n integer:=0;i integer;
begin
 select * into a from associations where id=p_association_id;
 if not found then raise exception 'الجمعية غير موجودة'; end if;
 if a.owner_id<>auth.uid() and not is_manager() then raise exception 'لا تملك صلاحية'; end if;
 if exists(select 1 from cycles where association_id=a.id) then return 0; end if;
 for i in 1..a.cycle_count loop
  v_date:=a.start_date+case when a.frequency='weekly' then (i-1)*7 else (i-1)*30 end;
  insert into cycles(association_id,cycle_number,beneficiary_user_id,due_date,amount) values(a.id,i,(select am.user_id from association_members am where am.association_id=a.id and am.receiving_order=i limit 1),v_date,a.installment*a.member_count) returning id into v_cycle_id;
  for m in select id from association_members where association_id=a.id loop
   insert into installments(cycle_id,member_id,due_date,amount) values(v_cycle_id,m.id,v_date,a.installment);n:=n+1;
  end loop;
 end loop;
 return n;
end $$;