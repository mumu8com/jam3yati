package ly.jam3yati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Jam3yatiApp() }
    }
}

@Composable
fun Jam3yatiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repo = remember { SupabaseRepository(context) }
    var signedIn by remember { mutableStateOf(repo.isSignedIn()) }
    if (!signedIn) AuthScreen(repo = repo, onSuccess = { signedIn = true })
    else HomeScreen(repo = repo, onLogout = { repo.signOut(); signedIn = false })
}

@Composable
private fun AuthScreen(repo: SupabaseRepository, onSuccess: () -> Unit) {
    var loginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("جمعياتي", style = MaterialTheme.typography.headlineLarge)
        Text("إدارة الجمعيات المالية بسهولة وأمان")
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
            busy = true
            Thread {
                try {
                    val result = if (loginMode) repo.signIn(email.trim(), password) else repo.signUp(email.trim(), password)
                    runOnUiThread { message = result; busy = false; if (repo.isSignedIn()) onSuccess() }
                } catch (e: Exception) {
                    runOnUiThread { message = e.message ?: "حدث خطأ"; busy = false }
                }
            }.start()
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "جارٍ التنفيذ..." else if (loginMode) "تسجيل الدخول" else "إنشاء حساب") }
        TextButton(onClick = { loginMode = !loginMode }) { Text(if (loginMode) "ليس لديك حساب؟ إنشاء حساب" else "لديك حساب؟ تسجيل الدخول") }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun HomeScreen(repo: SupabaseRepository, onLogout: () -> Unit) {
    var selected by remember { mutableStateOf(0) }
    var associations by remember { mutableStateOf<List<JamAssociation>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        Thread {
            try {
                val data = repo.associations()
                runOnUiThread { associations = data; loading = false; error = "" }
            } catch (e: Exception) {
                runOnUiThread { error = e.message ?: "تعذر تحميل البيانات"; loading = false }
            }
        }.start()
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("جمعياتي") }, actions = { TextButton(onClick = onLogout) { Text("خروج") } }) },
        bottomBar = {
            NavigationBar {
                listOf("الرئيسية", "الجمعيات", "الدفعات", "الأعضاء").forEachIndexed { i, label ->
                    NavigationBarItem(selected = selected == i, onClick = { selected = i }, icon = {}, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selected) {
                0 -> Dashboard(associations)
                1 -> AssociationsScreen(associations, loading, error, { showCreate = true }, ::reload)
                2 -> EmptySection("الدفعات", "سجل الدفعات والإيصالات سيظهر هنا.")
                else -> EmptySection("الأعضاء", "إدارة أعضاء كل جمعية وترتيب الاستلام ستظهر هنا.")
            }
        }
    }
    if (showCreate) CreateAssociationDialog({ showCreate = false }, { showCreate = false; reload() }, repo)
}

@Composable
private fun Dashboard(items: List<JamAssociation>) {
    Text("لوحة المتابعة", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("الجمعيات", items.size.toString(), Modifier.weight(1f))
        StatCard("الأعضاء", items.sumOf { it.memberCount }.toString(), Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    if (items.isEmpty()) Text("لا توجد جمعيات بعد. افتح تبويب الجمعيات وأنشئ أول جمعية.")
    else items.take(3).forEach { AssociationCard(it) }
}

@Composable
private fun AssociationsScreen(items: List<JamAssociation>, loading: Boolean, error: String, onAdd: () -> Unit, onReload: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("الجمعيات", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onAdd) { Text("جمعية جديدة") }
    }
    Spacer(Modifier.height(12.dp))
    if (loading) CircularProgressIndicator()
    if (error.isNotBlank()) { Text(error); TextButton(onClick = onReload) { Text("إعادة المحاولة") } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(items) { AssociationCard(it) } }
}

@Composable
private fun AssociationCard(a: JamAssociation) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(a.name, style = MaterialTheme.typography.titleLarge)
            Text("القسط: ${a.installment} • ${if (a.frequency == "monthly") "شهري" else a.frequency}")
            Text("الأعضاء: ${a.memberCount} • الدورات: ${a.cycleCount}")
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(14.dp)) { Text(title); Text(value, style = MaterialTheme.typography.headlineMedium) } }
}

@Composable
private fun EmptySection(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(text)
}

@Composable
private fun CreateAssociationDialog(onDismiss: () -> Unit, onCreated: () -> Unit, repo: SupabaseRepository) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("10") }
    var cycles by remember { mutableStateOf("10") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء جمعية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الجمعية") })
                OutlinedTextField(amount, { amount = it }, label = { Text("قيمة القسط") })
                OutlinedTextField(members, { members = it }, label = { Text("عدد الأعضاء") })
                OutlinedTextField(cycles, { cycles = it }, label = { Text("عدد الدورات") })
                if (error.isNotBlank()) Text(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val n = amount.toDoubleOrNull()
                val m = members.toIntOrNull()
                val c = cycles.toIntOrNull()
                if (name.isBlank() || n == null || n <= 0 || m == null || m <= 0 || c == null || c <= 0) { error = "أدخل بيانات صحيحة"; return@TextButton }
                busy = true
                Thread {
                    try {
                        repo.createAssociation(name.trim(), n, m, c, java.time.LocalDate.now().toString())
                        runOnUiThread { onCreated() }
                    } catch (e: Exception) {
                        runOnUiThread { error = e.message ?: "تعذر إنشاء الجمعية"; busy = false }
                    }
                }.start()
            }) { Text(if (busy) "جارٍ..." else "حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
