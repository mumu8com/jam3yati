package ly.jam3yati.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class JamAssociation(val id:String,val name:String,val installment:Double,val frequency:String,val memberCount:Int,val cycleCount:Int)

data class JamMember(val id:String,val userId:String,val name:String,val phone:String?,val order:Int)
data class JamInstallment(val id:String,val memberId:String,val memberName:String,val cycleNumber:Int,val dueDate:String,val amount:Double,val paidAmount:Double,val status:String)
data class JamPaymentResult(val paymentId:String,val receiptNo:String,val paidAmount:Double,val remaining:Double)


class SupabaseRepository(context:Context){
 private val client=OkHttpClient()
 private val prefs=context.getSharedPreferences("jam3yati_session",Context.MODE_PRIVATE)
 private val jsonType="application/json".toMediaType()
 private fun request(path:String,method:String="GET",body:String?=null):String{
  val b=Request.Builder().url(BuildConfig.SUPABASE_URL+path).addHeader("apikey",BuildConfig.SUPABASE_PUBLISHABLE_KEY).addHeader("Accept","application/json")
  prefs.getString("access_token",null)?.let{b.addHeader("Authorization","Bearer "+it)}
  if(body!=null)b.method(method,body.toRequestBody(jsonType))else b.method(method,null)
  client.newCall(b.build()).execute().use{res->val text=res.body?.string().orEmpty();if(!res.isSuccessful)throw IllegalStateException(text.ifBlank{"HTTP "+res.code});return text}
 }
 fun signUp(email:String,password:String):String{
  val o=JSONObject(request("/auth/v1/signup","POST",JSONObject().put("email",email).put("password",password).toString()));val token=o.optString("access_token")
  if(token.isNotBlank())prefs.edit().putString("access_token",token).putString("user_id",o.getJSONObject("user").getString("id")).apply()
  return if(token.isBlank())"تم إنشاء الحساب. تحقق من البريد الإلكتروني ثم سجل الدخول." else "تم إنشاء الحساب وتسجيل الدخول."
 }
 fun signIn(email:String,password:String):String{
  val o=JSONObject(request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email).put("password",password).toString()))
  prefs.edit().putString("access_token",o.getString("access_token")).putString("user_id",o.getJSONObject("user").getString("id")).apply();return "تم تسجيل الدخول بنجاح."
 }
 fun signOut(){prefs.edit().clear().apply()}
 fun isSignedIn()=!prefs.getString("access_token",null).isNullOrBlank()
 private fun uid()=prefs.getString("user_id",null)?:error("يجب تسجيل الدخول")
 fun associations():List<JamAssociation>{
  val id=URLEncoder.encode(uid(),"UTF-8");val a=JSONArray(request("/rest/v1/associations?select=id,name,installment,frequency,member_count,cycle_count&owner_id=eq."+id+"&order=created_at.desc"))
  return (0 until a.length()).map{val o=a.getJSONObject(it);JamAssociation(o.getString("id"),o.getString("name"),o.getDouble("installment"),o.getString("frequency"),o.getInt("member_count"),o.getInt("cycle_count"))}
 }
 fun members(associationId:String):List<JamMember>{
  val a=JSONArray(request("/rest/v1/association_members?select=id,user_id,receiving_order,profiles(full_name,phone)&association_id=eq."+URLEncoder.encode(associationId,"UTF-8")+"&order=receiving_order.asc"))
  return (0 until a.length()).map{val o=a.getJSONObject(it);val p=o.optJSONObject("profiles");JamMember(o.getString("id"),o.getString("user_id"),p?.optString("full_name","")?:"",p?.optString("phone",null),o.getInt("receiving_order"))}
 }
 fun installments(associationId:String):List<JamInstallment>{
  val q=URLEncoder.encode(associationId,"UTF-8")
  val a=JSONArray(request("/rest/v1/installments?select=id,member_id,due_date,amount,paid_amount,status,cycles!inner(cycle_number,association_id),association_members!inner(profiles(full_name))&cycles.association_id=eq."+q+"&order=due_date.asc"))
  return (0 until a.length()).map{val o=a.getJSONObject(it);val cyc=o.getJSONObject("cycles");val mem=o.getJSONObject("association_members").getJSONObject("profiles");JamInstallment(o.getString("id"),o.getString("member_id"),mem.optString("full_name",""),cyc.getInt("cycle_number"),o.getString("due_date"),o.getDouble("amount"),o.optDouble("paid_amount",0.0),o.getString("status"))}
 }
 fun generateSchedule(associationId:String):Int{
  val result=request("/rest/v1/rpc/generate_association_schedule","POST",JSONObject().put("p_association_id",associationId).toString()).trim()
  return result.toIntOrNull() ?: 0
 }
 fun recordPayment(installmentId:String,memberId:String,amount:Double,notes:String?):JamPaymentResult{
  val o=JSONObject(request("/rest/v1/rpc/record_payment","POST",JSONObject().put("p_installment_id",installmentId).put("p_member_id",memberId).put("p_amount",amount).put("p_notes",notes).toString()))
  return JamPaymentResult(o.getString("payment_id"),o.getString("receipt_no"),o.getDouble("paid_amount"),o.getDouble("remaining"))
 }
 fun addMemberByEmail(associationId:String,email:String,order:Int):JamMember{
  val o=JSONObject(request("/rest/v1/rpc/add_member_by_email","POST",JSONObject().put("p_association_id",associationId).put("p_email",email).put("p_order",order).toString()))
  return JamMember(o.getString("id"),o.getString("user_id"),"",null,o.getInt("receiving_order"))
 }
 fun createMember(associationId:String,userId:String,order:Int):JamMember{
  val body=JSONObject().put("association_id",associationId).put("user_id",userId).put("receiving_order",order)
  val o=JSONArray(request("/rest/v1/association_members?select=id,user_id,receiving_order","POST",body.toString())).getJSONObject(0)
  return JamMember(o.getString("id"),o.getString("user_id"),"",null,o.getInt("receiving_order"))
 }

 fun createAssociation(name:String,amount:Double,members:Int,cycles:Int,startDate:String):JamAssociation{
  val body=JSONObject().put("owner_id",uid()).put("name",name).put("installment",amount).put("frequency","monthly").put("member_count",members).put("cycle_count",cycles).put("start_date",startDate)
  val o=JSONArray(request("/rest/v1/associations?select=id,name,installment,frequency,member_count,cycle_count","POST",body.toString())).getJSONObject(0)
  return JamAssociation(o.getString("id"),o.getString("name"),o.getDouble("installment"),o.getString("frequency"),o.getInt("member_count"),o.getInt("cycle_count"))
 }
}