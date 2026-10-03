create or replace function public.record_payment(
  p_installment_id uuid, p_member_id uuid, p_amount numeric,
  p_receipt_no text, p_notes text default null
) returns jsonb language plpgsql security invoker set search_path = public as $$
declare v_payment_id uuid; v_receipt_id uuid; v_paid numeric; v_due numeric; v_status text;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if p_amount is null or p_amount <= 0 then raise exception 'Payment amount must be greater than zero'; end if;
  select i.amount, coalesce(i.paid_amount,0) into v_due,v_paid from public.installments i where i.id=p_installment_id and i.member_id=p_member_id for update;
  if not found then raise exception 'Installment not found or member mismatch'; end if;
  if v_paid + p_amount > v_due then raise exception 'Payment exceeds remaining installment balance'; end if;
  insert into public.payments(installment_id,member_id,amount,receipt_no,notes,created_by) values(p_installment_id,p_member_id,p_amount,p_receipt_no,p_notes,auth.uid()) returning id into v_payment_id;
  insert into public.receipts(payment_id,receipt_no,issued_by,notes) values(v_payment_id,p_receipt_no,auth.uid(),p_notes) returning id into v_receipt_id;
  v_paid := v_paid + p_amount;
  if v_paid >= v_due then v_status := 'paid'; elsif v_paid > 0 then v_status := 'partial'; else v_status := 'unpaid'; end if;
  update public.installments set paid_amount=v_paid, paid_at=case when v_status='paid' then now() else paid_at end, status=v_status, receipt_no=case when v_status='paid' then p_receipt_no else receipt_no end where id=p_installment_id;
  return jsonb_build_object('payment_id',v_payment_id,'receipt_id',v_receipt_id,'receipt_no',p_receipt_no,'paid_amount',v_paid,'remaining_amount',greatest(v_due-v_paid,0),'status',v_status);
end; $$;
revoke all on function public.record_payment(uuid,uuid,numeric,text,text) from public;
grant execute on function public.record_payment(uuid,uuid,numeric,text,text) to authenticated;