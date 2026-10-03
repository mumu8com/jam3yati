package ly.jam3yati.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class JamAssociation(val id:String,val name:String,val installment:Double,val frequency:String,val memberCount:Int,val cycleCount:Int)
data class JamMember(val id:String,val userId:String,val name:String,val phone:String?,val order:Int)
data class JamInstallment(val id:String,val memberId:String,val memberName:String,val cycleNumber:Int,val dueDate:String,val amount:Double,val paidAmount:Double,val status:String)
data class JamPaymentResult(val paymentId:String,val receiptNo:String,val paidAmount:Double,val remaining:Double)

class SupabaseRepository(context:Context){
 private val prefs=context.getSharedPreferences("jam3yati_local",Context.MODE_PRIVATE)
 private fun arr(key:String)=JSONArray(prefs.getString(key,"[]")!!)
 private fun save(key:String,a:JSONArray)=prefs.edit().putString(key,a.toString()).apply()
 private fun uid()=prefs.getString("local_user_id",null) ?: UUID.randomUUID().toString().also{prefs.edit().putString("local_user_id",it).apply()}
 fun signUp(email:String,password:String)="تم إنشاء الحساب محليًا على هذا الجهاز."
 fun signIn(email:String,password:String)="تم الدخول إلى الحساب المحلي."
 fun signOut(){ }
 fun isSignedIn()=true

 fun associations():List<JamAssociation>{
  val a=arr("associations")
  return (0 until a.length()).map{val o=a.getJSONObject(it);JamAssociation(o.getString("id"),o.getString("name"),o.getDouble("installment"),o.optString("frequency","monthly"),o.getInt("memberCount"),o.getInt("cycleCount"))}
 }
 fun members(associationId:String):List<JamMember>{
  val a=arr("members")
  return (0 until a.length()).map{a.getJSONObject(it)}.filter{it.getString("associationId")==associationId}.map{JamMember(it.getString("id"),it.optString("userId",uid()),it.getString("name"),it.optString("phone",null),it.getInt("order"))}
 }
 fun installments(associationId:String):List<JamInstallment>{
  val a=arr("installments")
  val memberMap=members(associationId).associateBy{it.id}
  return (0 until a.length()).map{a.getJSONObject(it)}.filter{memberMap.containsKey(it.getString("memberId"))}.map{val m=memberMap[it.getString("memberId")]!!;JamInstallment(it.getString("id"),m.id,m.name,it.getInt("cycleNumber"),it.getString("dueDate"),it.getDouble("amount"),it.optDouble("paidAmount",0.0),it.optString("status","pending"))}
 }
 fun generateSchedule(associationId:String):Int{
  val association=associations().firstOrNull{it.id==associationId} ?: return 0
  val members=members(associationId)
  val all=arr("installments")
  val existing=(0 until all.length()).count{all.getJSONObject(it).optString("associationId")==associationId}
  if(existing>0)return existing
  var count=0
  val start=prefs.getString("start_$associationId",LocalDate.now().toString())!!
  val startDate=runCatching{LocalDate.parse(start)}.getOrDefault(LocalDate.now())
  members.forEachIndexed{index,m->
   for(cycle in 1..association.cycleCount){
    val due=startDate.plusMonths((cycle-1).toLong()).toString()
    all.put(JSONObject().put("id",UUID.randomUUID().toString()).put("associationId",associationId).put("memberId",m.id).put("cycleNumber",cycle).put("dueDate",due).put("amount",association.installment).put("paidAmount",0).put("status","pending"))
    count++
   }
  }
  save("installments",all);return count
 }
 fun recordPayment(installmentId:String,memberId:String,amount:Double,notes:String?):JamPaymentResult{
  val a=arr("installments");var paid=0.0;var remaining=0.0
  for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.getString("id")==installmentId){paid=o.optDouble("paidAmount",0.0)+amount;remaining=(o.getDouble("amount")-paid).coerceAtLeast(0.0);o.put("paidAmount",paid).put("status",if(remaining<=0.0)"paid" else "partial");break}}
  save("installments",a)
  val receipt="RC-"+LocalDate.now().toString().replace("-","")+"-"+System.currentTimeMillis().toString().takeLast(5)
  return JamPaymentResult(UUID.randomUUID().toString(),receipt,paid,remaining)
 }
 fun addMemberByEmail(associationId:String,email:String,order:Int):JamMember{
  val m=JamMember(UUID.randomUUID().toString(),UUID.randomUUID().toString(),email.trim(),null,order)
  val a=arr("members");a.put(JSONObject().put("id",m.id).put("userId",m.userId).put("associationId",associationId).put("name",m.name).put("order",m.order));save("members",a);return m
 }
 fun createMember(associationId:String,userId:String,order:Int)=addMemberByEmail(associationId,userId,order)
 fun createAssociation(name:String,amount:Double,members:Int,cycles:Int,startDate:String):JamAssociation{
  val a=JamAssociation(UUID.randomUUID().toString(),name,amount,"monthly",members,cycles);val x=arr("associations");x.put(JSONObject().put("id",a.id).put("name",a.name).put("installment",a.installment).put("frequency",a.frequency).put("memberCount",a.memberCount).put("cycleCount",a.cycleCount));save("associations",x);prefs.edit().putString("start_"+a.id,startDate).apply();return a
 }
 fun exportJson():String{
  return JSONObject().put("associations",arr("associations")).put("members",arr("members")).put("installments",arr("installments")).put("exportedAt",System.currentTimeMillis()).toString(2)
 }
}