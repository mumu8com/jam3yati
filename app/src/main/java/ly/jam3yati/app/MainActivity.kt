package ly.jam3yati.app
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.work.*
import java.util.concurrent.TimeUnit

private val JamPrimary = Color(0xFF126B5A)
private val JamPrimaryContainer = Color(0xFFD0F0E7)
private val JamBackground = Color(0xFFF7F8FA)

@Composable
fun Jam3yatiTheme(content:@Composable ()->Unit){
 MaterialTheme(
  colorScheme=lightColorScheme(
   primary=JamPrimary,
   primaryContainer=JamPrimaryContainer,
   onPrimaryContainer=Color(0xFF002019),
   background=JamBackground,
   surface=JamBackground,
   surfaceVariant=Color(0xFFE8ECEB),
   onSurface=Color(0xFF17201D),
   onSurfaceVariant=Color(0xFF59635F)
  ),
  shapes=Shapes(
   small=RoundedCornerShape(10.dp),
   medium=RoundedCornerShape(16.dp),
   large=RoundedCornerShape(22.dp)
  ),
  content=content
 )
}

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   registerForActivityResult(ActivityResultContracts.RequestPermission()){ }.launch(Manifest.permission.POST_NOTIFICATIONS)
  setContent{Jam3yatiTheme{App()}}
 }
}

@Composable fun App(){
 val c=androidx.compose.ui.platform.LocalContext.current
 val r=remember{SupabaseRepository(c)}
 var refresh by remember{mutableStateOf(0)}
 LaunchedEffect(Unit){
  val work=PeriodicWorkRequestBuilder<PaymentReminderWorker>(1,TimeUnit.DAYS).build()
  WorkManager.getInstance(c).enqueueUniquePeriodicWork("payment_reminders",ExistingPeriodicWorkPolicy.UPDATE,work)
 }
 Main(r,refresh){refresh++}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Main(r:SupabaseRepository,refresh:Int,changed:()->Unit){
 var xs by remember{mutableStateOf<List<JamAssociation>>(emptyList())}
 var a by remember{mutableStateOf<JamAssociation?>(null)}
 var tab by remember{mutableStateOf(0)}
 var create by remember{mutableStateOf(false)}
 LaunchedEffect(refresh){xs=r.associations()}
 Scaffold(
  topBar={CenterAlignedTopAppBar(
 title={Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
  Text(a?.name?:"جمعياتي",fontWeight=FontWeight.Bold)
  if(a==null) Text("إدارة الجمعيات المالية",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }},
 colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=MaterialTheme.colorScheme.surface)
)},
  bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=3.dp){
   listOf("الرئيسية","الجمعيات","الأعضاء","الدفعات").forEachIndexed{i,t->
    NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▣","♙","✓")[i],fontWeight=FontWeight.Bold)},label={Text(t)})
   }
  }}
 ){p->
  Column(Modifier.padding(p).padding(16.dp)){
   when(tab){
    0->Dash(xs){a=it;tab=1}
    1->Column{
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      Text("الجمعيات");Button({create=true}){Text("جمعية جديدة")}
     }
     Assoc(xs){a=it}
    }
    2->Members(r,a){changed()}
    3->Pays(r,a)
   }
  }
 }
 if(create)CreateAssociation(r,{create=false;xs=r.associations()},{create=false})
}

@Composable fun Dash(xs:List<JamAssociation>,open:(JamAssociation)->Unit){
 Text("لوحة المتابعة",style=MaterialTheme.typography.headlineSmall)
 Text("بيانات محفوظة على الجهاز")
 Text("عدد الجمعيات: "+xs.size)
 xs.forEach{z->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(z.name);Text("القسط: "+z.installment+" | الأعضاء: "+z.memberCount);TextButton({open(z)}){Text("فتح")}}}}
}
@Composable fun Assoc(xs:List<JamAssociation>,open:(JamAssociation)->Unit){
 Text("الجمعيات",style=MaterialTheme.typography.headlineSmall)
 LazyColumn{items(xs){z->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text(z.name);Text("الأعضاء: "+z.memberCount+" | الدورات: "+z.cycleCount);TextButton({open(z)}){Text("إدارة")}}}}}
}
@Composable fun Members(r:SupabaseRepository,a:JamAssociation?,changed:()->Unit){
 if(a==null){Text("اختر جمعية أولاً");return}
 var xs by remember(a.id){mutableStateOf<List<JamMember>>(emptyList())}
 var add by remember{mutableStateOf(false)}
 LaunchedEffect(a.id){xs=r.members(a.id)}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("الأعضاء");Button({add=true}){Text("إضافة")}}
 LazyColumn{items(xs){m->Card(Modifier.fillMaxWidth()){Text("#"+m.order+"  "+m.name,Modifier.padding(12.dp))}}}
 if(add)AddMember(r,a,{add=false;xs=r.members(a.id);changed()},{add=false})
}
@Composable fun AddMember(r:SupabaseRepository,a:JamAssociation,done:()->Unit,cancel:()->Unit){
 var e by remember{mutableStateOf("")};var o by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("إضافة عضو")},text={Column{OutlinedTextField(e,{e=it},label={Text("اسم العضو")});OutlinedTextField(o,{o=it},label={Text("ترتيب الاستلام")});Text(msg)}},confirmButton={TextButton({val n=o.toIntOrNull();if(n==null){msg="ترتيب غير صحيح";return@TextButton};r.addMemberByEmail(a.id,e,n);done()}){Text("إضافة")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}
@Composable fun Pays(r:SupabaseRepository,a:JamAssociation?){
 if(a==null){Text("اختر جمعية أولاً");return}
 var xs by remember(a.id){mutableStateOf<List<JamInstallment>>(emptyList())}
 var pay by remember{mutableStateOf<JamInstallment?>(null)}
 var msg by remember{mutableStateOf("")}
 LaunchedEffect(a.id){xs=r.installments(a.id)}
 Text("الدفعات",style=MaterialTheme.typography.headlineSmall)
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({r.generateSchedule(a.id);xs=r.installments(a.id)}){Text("إنشاء الجدول")};Text(msg)}
 LazyColumn{items(xs){i->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Text("الدورة "+i.cycleNumber+" — "+i.memberName);Text(i.dueDate+" | "+i.paidAmount+"/"+i.amount+" | "+i.status);if(i.status!="paid")TextButton({pay=i}){Text("تسجيل دفعة")}}}}}
 if(pay!=null)Pay(r,pay!!,{pay=null;xs=r.installments(a.id)},{pay=null})
}
@Composable fun Pay(r:SupabaseRepository,i:JamInstallment,done:()->Unit,cancel:()->Unit){
 var amount by remember{mutableStateOf((i.amount-i.paidAmount).toString())};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("تسجيل دفعة")},text={Column{OutlinedTextField(amount,{amount=it},label={Text("المبلغ")});Text(msg)}},confirmButton={TextButton({val n=amount.toDoubleOrNull();if(n==null||n<=0){msg="مبلغ غير صحيح";return@TextButton};runCatching { r.recordPayment(i.id,i.memberId,n,null) }.onSuccess { x -> msg="تم التسجيل: "+x.receiptNo; done() }.onFailure { e -> msg=e.message ?: "تعذر تسجيل الدفعة" }}){Text("حفظ")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}
@Composable fun CreateAssociation(r:SupabaseRepository,done:()->Unit,cancel:()->Unit){
 var name by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var members by remember{mutableStateOf("")};var cycles by remember{mutableStateOf("")};var start by remember{mutableStateOf(java.time.LocalDate.now().toString())};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("جمعية جديدة")},text={Column{OutlinedTextField(name,{name=it},label={Text("اسم الجمعية")});OutlinedTextField(amount,{amount=it},label={Text("قيمة القسط")});OutlinedTextField(members,{members=it},label={Text("عدد الأعضاء")});OutlinedTextField(cycles,{cycles=it},label={Text("عدد الدورات")});OutlinedTextField(start,{start=it},label={Text("تاريخ البداية YYYY-MM-DD")});Text(msg)}},confirmButton={TextButton({val n=amount.toDoubleOrNull();val m=members.toIntOrNull();val k=cycles.toIntOrNull();if(name.isBlank()||n==null||m==null||k==null||m<=0||k<=0){msg="البيانات غير صحيحة";return@TextButton};r.createAssociation(name.trim(),n,m,k,start.trim());done()}){Text("إنشاء")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}
