-- Prevent unauthenticated callers from invoking the SECURITY DEFINER member RPC.
REVOKE EXECUTE ON FUNCTION public.add_member_by_email(uuid, text, integer) FROM anon;
GRANT EXECUTE ON FUNCTION public.add_member_by_email(uuid, text, integer) TO authenticated;
