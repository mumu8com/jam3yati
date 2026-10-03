package ly.jam3yati.app
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private fun ui(block:()->Unit)=Handler(Looper.getMainLooper()).post(block)
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(){val c=androidx.compose.ui.platform.LocalContext.current;val r=remember{SupabaseRepository(c)};var ok by remember{mutableStateOf(r.isSignedIn())};LaunchedEffect(ok){if(ok){val work=PeriodicWorkRequestBuilder<PaymentReminderWorker>(1,TimeUnit.DAYS).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("payment_reminders",ExistingPeriodicWorkPolicy.UPDATE,work);if(android.os.Build.VERSION.SDK_INT>=33 && c is android.app.Activity){c.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),1001)}}};if(!ok)Auth(r){ok=true}else Main(r){r.signOut();ok=false}}
@Composable fun Auth(r:SupabaseRepository,done:()->Unit){var e by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var login by remember{mutableStateOf(true)};var msg by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){Text("جمعياتي",style=MaterialTheme.typography.headlineLarge);OutlinedTextField(e,{e=it},label={Text("البريد")});OutlinedTextField(p,{p=it},label={Text("كلمة المرور")});Button(onClick={Thread{try{val x=if(login)r.signIn(e.trim(),p)else r.signUp(e.trim(),p);ui{msg=x;if(r.isSignedIn())done()}}catch(x:Exception){ui{msg=x.message?:"خطأ"}}}.start()}){Text(if(login)"دخول" else "إنشاء حساب")};TextButton({login=!login}){Text(if(login)"إنشاء حساب" else "تسجيل الدخول")};Text(msg)}}
@Composable fun Main(r:SupabaseRepository,logout:()->Unit){var xs by remember{mutableStateOf<List<JamAssociation>>(emptyList())};var a by remember{mutableStateOf<JamAssociation?>(null)};var tab by remember{mutableStateOf(0)};var refresh by remember{mutableStateOf(0)};var create by remember{mutableStateOf(false)};LaunchedEffect(refresh){Thread{try{val z=r.associations();ui{xs=z}}catch(_:Exception){}}.start()};Scaffold(topBar={TopAppBar(title={Text(a?.name?:"جمعياتي")},actions={TextButton(logout){Text("خروج")}})},bottomBar={NavigationBar{listOf("الرئيسية","الجمعيات","الأعضاء","الدفعات").forEachIndexed{i,t->NavigationBarItem(tab==i,{tab=i},{},label={Text(t)})}}}){p->Column(Modifier.padding(p).padding(16.dp)){when(tab){0->Dash(xs){a=it;tab=1};1->Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("الجمعيات");Button({create=true}){Text("جمعية جديدة")}};Assoc(xs){a=it}};2->Members(r,a){refresh++};3->Pays(r,a)}}}}
@Composable fun Dash(xs:List<JamAssociation>,open:(JamAssociation)->Unit){Text("لوحة المتابعة",style=MaterialTheme.typography.headlineSmall);Text("عدد الجمعيات: "+xs.size);xs.forEach{z->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(z.name);Text("القسط: "+z.installment+" | الأعضاء: "+z.memberCount);TextButton({open(z)}){Text("فتح")}}}}}
@Composable fun Assoc(xs:List<JamAssociation>,open:(JamAssociation)->Unit){Text("الجمعيات",style=MaterialTheme.typography.headlineSmall);LazyColumn{items(xs){z->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(z.name);Text("الأعضاء: "+z.memberCount+" | الدورات: "+z.cycleCount);TextButton({open(z)}){Text("إدارة")}}}}}}
@Composable fun Members(r:SupabaseRepository,a:JamAssociation?,changed:()->Unit){if(a==null){Text("اختر جمعية أولاً");return};var xs by remember(a.id){mutableStateOf<List<JamMember>>(emptyList())};var add by remember{mutableStateOf(false)};LaunchedEffect(a.id){Thread{try{val z=r.members(a.id);ui{xs=z}}catch(_:Exception){}}.start()};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("الأعضاء");Button({add=true}){Text("إضافة")}};LazyColumn{items(xs){m->Card(Modifier.fillMaxWidth()){Text("#"+m.order+"  "+m.name.ifBlank{"عضو"},Modifier.padding(12.dp))}}};if(add)AddMember(r,a,{add=false;changed()},{add=false})}
@Composable fun AddMember(r:SupabaseRepository,a:JamAssociation,done:()->Unit,cancel:()->Unit){var e by remember{mutableStateOf("")};var o by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")};AlertDialog(onDismissRequest=cancel,title={Text("إضافة عضو")},text={Column{OutlinedTextField(e,{e=it},label={Text("البريد الإلكتروني")});OutlinedTextField(o,{o=it},label={Text("ترتيب الاستلام")});Text(msg)}},confirmButton={TextButton({val n=o.toIntOrNull();if(n==null){msg="ترتيب غير صحيح";return@TextButton};Thread{try{r.addMemberByEmail(a.id,e,n);ui(done)}catch(x:Exception){ui{msg=x.message?:"تعذر الإضافة"}}}.start()}){Text("إضافة")}},dismissButton={TextButton(cancel){Text("إلغاء")}})}
@Composable fun Pays(r:SupabaseRepository,a:JamAssociation?){
 if(a==null){Text("اختر جمعية أولاً");return}
 val context=androidx.compose.ui.platform.LocalContext.current
 var xs by remember(a.id){mutableStateOf<List<JamInstallment>>(emptyList())}
 var pay by remember{mutableStateOf<JamInstallment?>(null)}
 var msg by remember{mutableStateOf("")}
 fun reload(){Thread{try{val z=r.installments(a.id);ui{xs=z}}catch(x:Exception){ui{msg=x.message?:"خطأ"}}}.start()}
 LaunchedEffect(a.id){reload()}
 Text("الدفعات",style=MaterialTheme.typography.headlineSmall)
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  Button({Thread{try{r.generateSchedule(a.id);reload()}catch(x:Exception){ui{msg=x.message?:"تعذر إنشاء الجدول"}}}.start()}){Text("إنشاء الجدول")}
  TextButton({ReportUtils.createPdfReport(context,a,xs)}){Text("PDF")}
  TextButton({ReportUtils.createXlsxReport(context,a,xs)}){Text("Excel")}
 }
 Text(msg)
 LazyColumn{items(xs){i->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("الدورة "+i.cycleNumber+" — "+i.memberName.ifBlank{"عضو"});Text(i.dueDate+" | "+i.paidAmount+"/"+i.amount+" | "+i.status);if(i.status!="paid")TextButton({pay=i}){Text("تسجيل دفعة")}}}}}
 if(pay!=null)Pay(r,pay!!,{pay=null;reload()},{pay=null})
}
@Composable fun Pay(r:SupabaseRepository,i:JamInstallment,done:()->Unit,cancel:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current
 var amount by remember{mutableStateOf((i.amount-i.paidAmount).toString())}
 var msg by remember{mutableStateOf("")}
 var result by remember{mutableStateOf<JamPaymentResult?>(null)}
 AlertDialog(
  onDismissRequest=cancel,
  title={Text("تسجيل دفعة")},
  text={
   Column{
    OutlinedTextField(amount,{amount=it},label={Text("المبلغ")})
    if(msg.isNotBlank())Text(msg)
   }
  },
  confirmButton={
   TextButton({
    val n=amount.toDoubleOrNull()
    if(n==null||n<=0){msg="مبلغ غير صحيح";return@TextButton}
    Thread{
     try{
      val x=r.recordPayment(i.id,i.memberId,n,null)
      ui{result=x;msg="تم تسجيل الدفعة. الإيصال: "+x.receiptNo}
     }catch(x:Exception){ui{msg=x.message?:"تعذر التسجيل"}}
    }.start()
   }){Text("حفظ")}
  },
  dismissButton={
   if(result!=null)TextButton({
    ReceiptUtils.createAndShareReceipt(context,result!!.receiptNo,"جمعية",""+i.memberName,i.cycleNumber,result!!.paidAmount,result!!.remaining)
    done()
   }){Text("مشاركة PDF")}
   else TextButton(cancel){Text("إلغاء")}
  }
 )
}

