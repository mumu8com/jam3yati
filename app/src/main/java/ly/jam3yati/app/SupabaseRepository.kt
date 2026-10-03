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
 fun createAssociation(name:String,amount:Double,members:Int,cycles:Int,startDate:String):JamAssociation{
  val body=JSONObject().put("owner_id",uid()).put("name",name).put("installment",amount).put("frequency","monthly").put("member_count",members).put("cycle_count",cycles).put("start_date",startDate)
  val o=JSONArray(request("/rest/v1/associations?select=id,name,installment,frequency,member_count,cycle_count","POST",body.toString())).getJSONObject(0)
  return JamAssociation(o.getString("id"),o.getString("name"),o.getDouble("installment"),o.getString("frequency"),o.getInt("member_count"),o.getInt("cycle_count"))
 }
}