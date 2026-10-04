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
data class JamDashboardStats(val upcoming:Int,val overdue:Int,val paid:Int,val collected:Double,val outstanding:Double)

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
  return (0 until a.length()).map{a.getJSONObject(it)}.filter{it.getString("associationId")==associationId}.map{JamMember(it.getString("id"),it.optString("userId",uid()),it.getString("name"),it.optString("phone",null),it.getInt("order"))}.sortedBy{it.order}
 }
 fun installments(associationId:String):List<JamInstallment>{
  val memberMap=members(associationId).associateBy{it.id}
  val a=arr("installments")
  return (0 until a.length()).mapNotNull{idx->
   val o=a.getJSONObject(idx); val m=memberMap[o.getString("memberId")] ?: return@mapNotNull null
   JamInstallment(o.getString("id"),m.id,m.name,o.getInt("cycleNumber"),o.getString("dueDate"),o.getDouble("amount"),o.optDouble("paidAmount",0.0),o.optString("status","pending"))
  }.sortedWith(compareBy<JamInstallment>{it.dueDate}.thenBy{it.cycleNumber}.thenBy{it.memberName})
 }
 fun dashboardStats():JamDashboardStats{
  val today=LocalDate.now();var upcoming=0;var overdue=0;var paid=0;var collected=0.0;var outstanding=0.0
  val a=arr("installments")
  for(i in 0 until a.length()){
   val o=a.getJSONObject(i);val amount=o.getDouble("amount");val p=o.optDouble("paidAmount",0.0).coerceAtLeast(0.0);collected+=p;outstanding+=(amount-p).coerceAtLeast(0.0)
   if(p>=amount){paid++;continue}
   val due=runCatching{LocalDate.parse(o.getString("dueDate"))}.getOrDefault(today)
   if(due.isBefore(today)) overdue++ else upcoming++
  }
  return JamDashboardStats(upcoming,overdue,paid,collected,outstanding)
 }
 fun generateSchedule(associationId:String):Int{
  val association=associations().firstOrNull{it.id==associationId} ?: error("الجمعية غير موجودة")
  val ms=members(associationId)
  require(ms.size==association.memberCount){"يجب إضافة جميع أعضاء الجمعية قبل إنشاء الجدول (${ms.size}/${association.memberCount})"}
  val all=arr("installments")
  val existing=(0 until all.length()).count{all.getJSONObject(it).optString("associationId")==associationId}
  if(existing>0)return existing
  val start=prefs.getString("start_$associationId",LocalDate.now().toString())!!
  val startDate=runCatching{LocalDate.parse(start)}.getOrElse{error("تاريخ بداية الجمعية غير صحيح")}
  ms.forEach{m->
   for(cycle in 1..association.cycleCount){
    val due=startDate.plusMonths((cycle-1).toLong()).toString()
    all.put(JSONObject().put("id",UUID.randomUUID().toString()).put("associationId",associationId).put("memberId",m.id).put("cycleNumber",cycle).put("dueDate",due).put("amount",association.installment).put("paidAmount",0.0).put("status","pending"))
   }
  }
  save("installments",all);return ms.size*association.cycleCount
 }
 fun recordPayment(installmentId:String,memberId:String,amount:Double,notes:String?):JamPaymentResult{
  require(amount>0){"يجب أن يكون مبلغ الدفعة أكبر من صفر"}
  val a=arr("installments");var result:JamPaymentResult?=null
  for(i in 0 until a.length()){
   val o=a.getJSONObject(i)
   if(o.getString("id")==installmentId){
    require(o.getString("memberId")==memberId){"العضو غير مطابق للاستحقاق"}
    val current=o.optDouble("paidAmount",0.0);val due=o.getDouble("amount");val remainingBefore=(due-current).coerceAtLeast(0.0)
    require(amount<=remainingBefore+0.0001){"المبلغ يتجاوز المتبقي (${String.format("%.2f",remainingBefore)} د.ل)"}
    val paid=current+amount;val remaining=(due-paid).coerceAtLeast(0.0)
    o.put("paidAmount",paid).put("status",if(remaining<=0.0001)"paid" else "partial")
    val receipt="RC-"+LocalDate.now().toString().replace("-","")+"-"+System.currentTimeMillis().toString().takeLast(6)
    result=JamPaymentResult(UUID.randomUUID().toString(),receipt,amount,remaining);break
   }
  }
  require(result!=null){"الاستحقاق غير موجود"};save("installments",a);return result!!
 }
 fun addMemberByEmail(associationId:String,email:String,order:Int):JamMember{
  require(email.trim().isNotBlank()){"اسم العضو مطلوب"}
  val association=associations().firstOrNull{it.id==associationId} ?: error("الجمعية غير موجودة");val existing=members(associationId)
  require(existing.size<association.memberCount){"تم الوصول إلى عدد أعضاء الجمعية"};require(order in 1..association.memberCount){"ترتيب العضو غير صحيح"};require(existing.none{it.order==order}){"ترتيب العضو مستخدم"}
  val m=JamMember(UUID.randomUUID().toString(),UUID.randomUUID().toString(),email.trim(),null,order);val a=arr("members")
  a.put(JSONObject().put("id",m.id).put("userId",m.userId).put("associationId",associationId).put("name",m.name).put("order",m.order));save("members",a);return m
 }
 fun createMember(associationId:String,userId:String,order:Int)=addMemberByEmail(associationId,userId,order)
 fun createAssociation(name:String,amount:Double,members:Int,cycles:Int,startDate:String):JamAssociation{
  require(name.trim().isNotBlank()){"اسم الجمعية مطلوب"};require(amount>0){"قيمة القسط يجب أن تكون أكبر من صفر"};require(members>0){"عدد الأعضاء غير صحيح"};require(cycles>0){"عدد الدورات غير صحيح"}
  val parsed=runCatching{LocalDate.parse(startDate)}.getOrElse{error("تاريخ البداية غير صحيح")}
  val a=JamAssociation(UUID.randomUUID().toString(),name.trim(),amount,"monthly",members,cycles);val x=arr("associations")
  x.put(JSONObject().put("id",a.id).put("name",a.name).put("installment",a.installment).put("frequency",a.frequency).put("memberCount",a.memberCount).put("cycleCount",a.cycleCount));save("associations",x);prefs.edit().putString("start_"+a.id,parsed.toString()).apply();return a
 }
 fun exportJson():String=JSONObject().put("associations",arr("associations")).put("members",arr("members")).put("installments",arr("installments")).put("exportedAt",System.currentTimeMillis()).toString(2)
}
