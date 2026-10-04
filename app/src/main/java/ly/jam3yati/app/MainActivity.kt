package ly.jam3yati.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val JamPrimary=Color(0xFF126B5A)
private val JamPrimaryContainer=Color(0xFFD0F0E7)
private val JamBackground=Color(0xFFF6F8F7)
private val JamGold=Color(0xFF9A6B12)
private val JamGoldContainer=Color(0xFFFFEFC5)
private val JamRed=Color(0xFFB3261E)
private val JamRedContainer=Color(0xFFFFDAD6)
private val JamBlue=Color(0xFF315A8A)
private val JamBlueContainer=Color(0xFFDCEBFF)

@Composable
fun Jam3yatiTheme(content: @Composable () -> Unit){
 MaterialTheme(
  colorScheme=lightColorScheme(primary=JamPrimary,onPrimary=Color.White,primaryContainer=JamPrimaryContainer,onPrimaryContainer=Color(0xFF002019),background=JamBackground,surface=Color.White,surfaceVariant=Color(0xFFE7ECEA),onSurface=Color(0xFF17201D),onSurfaceVariant=Color(0xFF59635F),error=JamRed,errorContainer=JamRedContainer),
  shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(22.dp)),content=content)
}

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   registerForActivityResult(ActivityResultContracts.RequestPermission()){ }.launch(Manifest.permission.POST_NOTIFICATIONS)
  setContent{Jam3yatiTheme{App()}}
 }
}

@Composable
fun App(){
 val context=androidx.compose.ui.platform.LocalContext.current
 val repo=remember{SupabaseRepository(context)}
 var refresh by remember{mutableIntStateOf(0)}
 LaunchedEffect(Unit){
  WorkManager.getInstance(context).enqueueUniquePeriodicWork("payment_reminders",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<PaymentReminderWorker>(1,TimeUnit.DAYS).build())
 }
 Main(repo,refresh){refresh++}
}

private data class NavItem(val title:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(r:SupabaseRepository,refresh:Int,changed:()->Unit){
 var associations by remember{mutableStateOf<List<JamAssociation>>(emptyList())}
 var selected by remember{mutableStateOf<JamAssociation?>(null)}
 var tab by remember{mutableIntStateOf(0)}
 var create by remember{mutableStateOf(false)}
 LaunchedEffect(refresh){associations=r.associations();if(selected!=null)selected=associations.firstOrNull{it.id==selected!!.id}}
 val nav=listOf(NavItem("الرئيسية",Icons.Filled.Dashboard),NavItem("الجمعيات",Icons.Filled.Groups),NavItem("الأعضاء",Icons.Filled.People),NavItem("الدفعات",Icons.Filled.Payments))
 Scaffold(
  topBar={
   CenterAlignedTopAppBar(
    title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text(selected?.name?:"جمعياتي",fontWeight=FontWeight.Bold);if(selected==null)Text("إدارة مالية ذكية ومحلية",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
    navigationIcon={if(selected!=null)IconButton({selected=null;tab=1}){Icon(Icons.Filled.ArrowBack,"رجوع")}},
    actions={IconButton({}){Icon(Icons.Filled.MoreVert,"المزيد")}},
    colors=TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor=Color.White))
  },
  bottomBar={NavigationBar(containerColor=Color.White,tonalElevation=4.dp){nav.forEachIndexed{i,n->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(n.icon,n.title)},label={Text(n.title)})}}}
 ){padding->
  Box(Modifier.fillMaxSize().padding(padding)){
   when(tab){
    0->Dashboard(r,associations,refresh){selected=it;tab=1}
    1->AssociationsScreen(associations,{create=true}){selected=it;tab=2}
    2->MembersScreen(r,selected){changed()}
    else->PaymentsScreen(r,selected){changed()}
   }
  }
 }
 if(create)CreateAssociation(r,{create=false;changed()},{create=false})
}

@Composable
fun Dashboard(r:SupabaseRepository,associations:List<JamAssociation>,refresh:Int,open:(JamAssociation)->Unit){
 val stats=r.dashboardStats()
 LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{
   Text("مرحباً بك 👋",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text("لوحة التحكم",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
   Text("كل أرقامك المالية في مكان واحد",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  item{
   Card(colors=CardDefaults.cardColors(containerColor=JamPrimary),shape=RoundedCornerShape(22.dp)){
    Column(Modifier.padding(20.dp)){Text("الرصيد المحصّل",color=Color.White.copy(alpha=.8f));Text(money(stats.collected),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White);Spacer(Modifier.height(4.dp));Text("المتبقي للتحصيل: "+money(stats.outstanding),color=Color.White.copy(alpha=.85f))}
   }
  }
  item{
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
    MetricCard(Modifier.weight(1f),"الاستحقاقات القادمة",stats.upcoming,Icons.Filled.Schedule,JamBlueContainer,JamBlue)
    MetricCard(Modifier.weight(1f),"المتأخرون",stats.overdue,Icons.Filled.WarningAmber,JamRedContainer,JamRed)
   }
  }
  item{
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
    MetricCard(Modifier.weight(1f),"المبالغ المدفوعة",stats.paid,Icons.Filled.CheckCircle,JamPrimaryContainer,JamPrimary)
    MetricCard(Modifier.weight(1f),"الجمعيات",associations.size,Icons.Filled.Groups,JamGoldContainer,JamGold)
   }
  }
  item{
   Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
    Column(Modifier.padding(18.dp)){Text("الوضع الحالي",fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(if(associations.isEmpty())"أنشئ أول جمعية للبدء." else "لديك ${associations.size} جمعية مالية محفوظة على الجهاز.",color=MaterialTheme.colorScheme.onSurfaceVariant);if(associations.isNotEmpty()){Spacer(Modifier.height(12.dp));associations.take(3).forEach{AssociationCompact(it,open)}}}
   }
  }
 }
}

@Composable
fun MetricCard(modifier:Modifier,title:String,value:Int,icon:androidx.compose.ui.graphics.vector.ImageVector,container:Color,content:Color){
 Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=container)){
  Column(Modifier.padding(14.dp)){Surface(shape=CircleShape,color=Color.White.copy(alpha=.7f)){Icon(icon,title,Modifier.padding(8.dp),tint=content)};Spacer(Modifier.height(10.dp));Text(value.toString(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=content);Text(title,style=MaterialTheme.typography.labelMedium,color=content)}
 }
}

@Composable
fun AssociationCompact(a:JamAssociation,open:(JamAssociation)->Unit){
 Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
  Surface(shape=RoundedCornerShape(12.dp),color=JamPrimaryContainer){Icon(Icons.Filled.AccountBalanceWallet,null,Modifier.padding(9.dp),tint=JamPrimary)}
  Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(a.name,fontWeight=FontWeight.Bold);Text("${a.memberCount} أعضاء • ${a.cycleCount} دورات • ${money(a.installment)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  TextButton({open(a)}){Text("فتح")}
 }
}

@Composable
fun AssociationsScreen(xs:List<JamAssociation>,newAssociation:()->Unit,open:(JamAssociation)->Unit){
 Scaffold(floatingActionButton={FloatingActionButton(newAssociation,containerColor=JamPrimary,contentColor=Color.White){Icon(Icons.Filled.Add,"إضافة جمعية")}}){p->
  if(xs.isEmpty())EmptyState(Modifier.padding(p).padding(20.dp),"لا توجد جمعيات","أنشئ جمعية جديدة وابدأ بإضافة الأعضاء وجدول الاستحقاقات.",newAssociation,"إنشاء جمعية")
  else LazyColumn(Modifier.padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("جمعياتي",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${xs.size} جمعيات محفوظة على جهازك",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   items(xs){a->Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=JamPrimaryContainer){Icon(Icons.Filled.Groups,null,Modifier.padding(11.dp),tint=JamPrimary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(a.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("قسط شهري",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton({open(a)}){Icon(Icons.Filled.ArrowBack,"فتح")}};Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){InfoPill("${a.memberCount} عضو");InfoPill("${a.cycleCount} دورة");InfoPill(money(a.installment))};Spacer(Modifier.height(10.dp));Button({open(a)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){Text("إدارة الجمعية")}}}}
  }
 }
}

@Composable fun InfoPill(text:String){Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceVariant){Text(text,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium)}}

@Composable
fun MembersScreen(r:SupabaseRepository,a:JamAssociation?,changed:()->Unit){
 if(a==null){EmptyState(Modifier.fillMaxSize().padding(20.dp),"اختر جمعية","انتقل إلى الجمعيات ثم اختر جمعية لإدارة أعضائها.",null,null);return}
 var members by remember(a.id){mutableStateOf(r.members(a.id))};var add by remember{mutableStateOf(false)}
 LaunchedEffect(a.id){members=r.members(a.id)}
 Scaffold(floatingActionButton={if(members.size<a.memberCount)FloatingActionButton({add=true},containerColor=JamPrimary,contentColor=Color.White){Icon(Icons.Filled.Add,"إضافة عضو")}}){p->
  LazyColumn(Modifier.padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("أعضاء الجمعية",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${members.size} من ${a.memberCount} أعضاء",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   item{LinearProgressIndicator({members.size.toFloat()/a.memberCount.coerceAtLeast(1)},Modifier.fillMaxWidth(),color=JamPrimary,trackColor=MaterialTheme.colorScheme.surfaceVariant)}
   itemsIndexed(members){index,m->Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=JamPrimaryContainer){Text((index+1).toString(),Modifier.padding(10.dp),fontWeight=FontWeight.Bold,color=JamPrimary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(m.name,fontWeight=FontWeight.Bold);Text("ترتيب الاستلام: ${m.order}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Filled.People,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}
  }
 }
 if(add)AddMember(r,a,{members=r.members(a.id);add=false;changed()},{add=false})
}

@Composable
fun PaymentsScreen(r:SupabaseRepository,a:JamAssociation?,changed:()->Unit){
 if(a==null){EmptyState(Modifier.fillMaxSize().padding(20.dp),"اختر جمعية","انتقل إلى الجمعيات ثم اختر جمعية لإدارة الاستحقاقات والدفعات.",null,null);return}
 var xs by remember(a.id){mutableStateOf(r.installments(a.id))};var pay by remember{mutableStateOf<JamInstallment?>(null)};var filter by remember{mutableStateOf("all")};var msg by remember{mutableStateOf("")}
 LaunchedEffect(a.id){xs=r.installments(a.id)}
 val today=LocalDate.now()
 val filtered=xs.filter{
  when(filter){"upcoming"->it.status!="paid"&&!LocalDate.parse(it.dueDate).isBefore(today);"overdue"->it.status!="paid"&&LocalDate.parse(it.dueDate).isBefore(today);"paid"->it.status=="paid";else->true}
 }
 Scaffold(floatingActionButton={if(xs.isEmpty())FloatingActionButton({try{r.generateSchedule(a.id);xs=r.installments(a.id);msg="تم إنشاء جدول ${xs.size} استحقاق"}catch(e:Exception){msg=e.message?:"تعذر إنشاء الجدول"}},containerColor=JamPrimary,contentColor=Color.White){Icon(Icons.Filled.CalendarMonth,"إنشاء الجدول")}}){p->
  LazyColumn(Modifier.padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{Text("الاستحقاقات والدفعات",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("جدول واضح لمتابعة كل عضو وكل دورة",color=MaterialTheme.colorScheme.onSurfaceVariant)}
   item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Filter("الكل",filter=="all"){filter="all"};Filter("القادمة",filter=="upcoming"){filter="upcoming"};Filter("متأخرة",filter=="overdue"){filter="overdue"};Filter("مدفوعة",filter=="paid"){filter="paid"}}}
   if(xs.isEmpty())item{Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Filled.CalendarMonth,null,Modifier.size(42.dp),tint=JamPrimary);Spacer(Modifier.height(8.dp));Text("لم يتم إنشاء جدول الاستحقاقات",fontWeight=FontWeight.Bold);Text("أضف جميع الأعضاء ثم اضغط إنشاء الجدول.",textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));Button({try{r.generateSchedule(a.id);xs=r.installments(a.id);msg="تم إنشاء الجدول"}catch(e:Exception){msg=e.message?:"تعذر إنشاء الجدول"}}){Text("إنشاء الجدول")};if(msg.isNotBlank())Text(msg,color=JamRed,modifier=Modifier.padding(top=8.dp))}}}
   else{
    item{ScheduleHeader(xs)}
    items(filtered){i->InstallmentRow(i,today){pay=i}}
   }
   if(xs.isNotEmpty()&&filtered.isEmpty())item{Text("لا توجد نتائج بهذا الفلتر.",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  }
 }
 if(pay!=null)Pay(r,pay!!,{pay=null;xs=r.installments(a.id);changed()},{pay=null})
}

@Composable fun Filter(label:String,selected:Boolean,onClick:()->Unit){FilterChip(selected=selected,onClick=onClick,label={Text(label)},shape=RoundedCornerShape(50))}

@Composable
fun ScheduleHeader(xs:List<JamInstallment>){
 val total=xs.sumOf{it.amount};val paid=xs.sumOf{it.paidAmount}
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=JamPrimaryContainer)){Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text("إجمالي الجدول",style=MaterialTheme.typography.labelMedium);Text(money(total),fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f)){Text("المدفوع",style=MaterialTheme.typography.labelMedium);Text(money(paid),fontWeight=FontWeight.Bold,color=JamPrimary)};Column(Modifier.weight(1f)){Text("المتبقي",style=MaterialTheme.typography.labelMedium);Text(money(total-paid),fontWeight=FontWeight.Bold,color=JamRed)}}}
}

@Composable
fun InstallmentRow(i:JamInstallment,today:LocalDate,pay:()->Unit){
 val due=runCatching{LocalDate.parse(i.dueDate)}.getOrDefault(today);val overdue=i.status!="paid"&&due.isBefore(today);val remaining=(i.amount-i.paidAmount).coerceAtLeast(0.0)
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=if(i.status=="paid")JamPrimaryContainer else if(overdue)JamRedContainer else JamBlueContainer){Icon(if(i.status=="paid")Icons.Filled.CheckCircle else if(overdue)Icons.Filled.WarningAmber else Icons.Filled.Schedule,null,Modifier.padding(9.dp),tint=if(i.status=="paid")JamPrimary else if(overdue)JamRed else JamBlue)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(i.memberName,fontWeight=FontWeight.Bold);Text("الدورة ${i.cycleNumber} • ${i.dueDate}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)};StatusPill(if(i.status=="paid")"مدفوع" else if(overdue)"متأخر" else if(i.status=="partial")"جزئي" else "قادم",if(i.status=="paid")JamPrimary else if(overdue)JamRed else JamBlue)}
  Spacer(Modifier.height(12.dp));Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("المطلوب: ${money(i.amount)}",style=MaterialTheme.typography.bodySmall);Text("المدفوع: ${money(i.paidAmount)}",style=MaterialTheme.typography.bodySmall,color=JamPrimary)};if(i.status!="paid")Button(pay,shape=RoundedCornerShape(10.dp)){Text(if(i.status=="partial")"إكمال الدفعة" else "تسجيل دفعة")}}
  if(i.status!="paid")Text("المتبقي: ${money(remaining)}",style=MaterialTheme.typography.labelMedium,color=JamRed,modifier=Modifier.padding(top=6.dp))
 }}
}

@Composable fun StatusPill(text:String,color:Color){Surface(shape=RoundedCornerShape(50),color=color.copy(alpha=.12f)){Text(text,Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=color,fontWeight=FontWeight.Bold)}}

@Composable
fun EmptyState(modifier:Modifier,title:String,body:String,action:(()->Unit)?,button:String?){
 Column(modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Surface(shape=CircleShape,color=JamPrimaryContainer){Icon(Icons.Filled.AccountBalanceWallet,null,Modifier.padding(18.dp).size(42.dp),tint=JamPrimary)};Spacer(Modifier.height(14.dp));Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(body,Modifier.padding(horizontal=20.dp,vertical=6.dp),textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant);if(action!=null&&button!=null){Spacer(Modifier.height(10.dp));Button(action,shape=RoundedCornerShape(12.dp)){Text(button)}}}
}

@Composable
fun AddMember(r:SupabaseRepository,a:JamAssociation,done:()->Unit,cancel:()->Unit){
 var name by remember{mutableStateOf("")};var order by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("إضافة عضو")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("اسم العضو")},singleLine=true);OutlinedTextField(order,{order=it.filter(Char::isDigit)},label={Text("ترتيب الاستلام 1-${a.memberCount}")},singleLine=true);if(msg.isNotBlank())Text(msg,color=JamRed)}},confirmButton={Button({val n=order.toIntOrNull();try{if(n==null)error("أدخل ترتيباً صحيحاً");r.addMemberByEmail(a.id,name,n);done()}catch(e:Exception){msg=e.message?:"تعذر إضافة العضو"}}){Text("إضافة")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}

@Composable
fun Pay(r:SupabaseRepository,i:JamInstallment,done:()->Unit,cancel:()->Unit){
 var amount by remember{mutableStateOf((i.amount-i.paidAmount).toString())};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("تسجيل دفعة")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(i.memberName,fontWeight=FontWeight.Bold);Text("المتبقي: ${money(i.amount-i.paidAmount)}",color=JamRed);OutlinedTextField(amount,{amount=it},label={Text("المبلغ")},singleLine=true);if(msg.isNotBlank())Text(msg,color=JamRed)}},confirmButton={Button({try{val n=amount.toDoubleOrNull()?:error("مبلغ غير صحيح");r.recordPayment(i.id,i.memberId,n,null);done()}catch(e:Exception){msg=e.message?:"تعذر تسجيل الدفعة"}}){Text("حفظ الدفعة")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}

@Composable
fun CreateAssociation(r:SupabaseRepository,done:()->Unit,cancel:()->Unit){
 var name by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var members by remember{mutableStateOf("")};var cycles by remember{mutableStateOf("")};var start by remember{mutableStateOf(LocalDate.now().toString())};var msg by remember{mutableStateOf("")}
 AlertDialog(onDismissRequest=cancel,title={Text("إنشاء جمعية جديدة")},text={Column(verticalArrangement=Arrangement.spacedBy(7.dp)){OutlinedTextField(name,{name=it},label={Text("اسم الجمعية")},singleLine=true);OutlinedTextField(amount,{amount=it},label={Text("قيمة القسط — د.ل")},singleLine=true);OutlinedTextField(members,{members=it.filter(Char::isDigit)},label={Text("عدد الأعضاء")},singleLine=true);OutlinedTextField(cycles,{cycles=it.filter(Char::isDigit)},label={Text("عدد الدورات")},singleLine=true);OutlinedTextField(start,{start=it},label={Text("تاريخ البداية YYYY-MM-DD")},singleLine=true);if(msg.isNotBlank())Text(msg,color=JamRed)}},confirmButton={Button({try{r.createAssociation(name,amount.toDoubleOrNull()?:error("قيمة القسط غير صحيحة"),members.toIntOrNull()?:error("عدد الأعضاء غير صحيح"),cycles.toIntOrNull()?:error("عدد الدورات غير صحيح"),start);done()}catch(e:Exception){msg=e.message?:"تعذر إنشاء الجمعية"}}){Text("إنشاء الجمعية")}},dismissButton={TextButton(cancel){Text("إلغاء")}})
}

fun money(v:Double):String=String.format(Locale.US,"%.2f د.ل",v)
