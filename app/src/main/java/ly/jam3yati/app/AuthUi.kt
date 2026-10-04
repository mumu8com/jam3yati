package ly.jam3yati.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

private val AuthPrimary = Color(0xFF126B5A)

@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val repo = remember { AuthRepository() }
    var loading by remember { mutableStateOf(true) }
    var signedIn by remember { mutableStateOf(false) }
    var subscription by remember { mutableStateOf<JamSubscription?>(null) }

    suspend fun refresh() {
        loading = true
        signedIn = repo.isSignedIn()
        subscription = if (signedIn) repo.subscription() else null
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AuthPrimary)
        }
    } else if (!signedIn) {
        LoginScreen(repo) { refresh() }
    } else if (subscription == null || subscriptionAccessible(subscription!!)) {
        Column(Modifier.fillMaxSize()) {
            TrialBanner(subscription)
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        SubscriptionScreen(repo) { refresh() }
    }
}

private fun subscriptionAccessible(s: JamSubscription): Boolean {
    if (s.status == "active") return true
    if (s.status != "trialing") return false
    val end = s.trialEndsAt ?: return false
    return runCatching { OffsetDateTime.parse(end).isAfter(OffsetDateTime.now()) }.getOrDefault(false)
}

@Composable
private fun TrialBanner(s: JamSubscription?) {
    val days = s?.trialEndsAt?.let {
        runCatching {
            ChronoUnit.DAYS.between(OffsetDateTime.now(), OffsetDateTime.parse(it)).toInt()
        }.getOrNull()
    }
    if (s?.status == "trialing" && days != null) {
        Surface(color = Color(0xFFFFF3D6), tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Timer, null, tint = Color(0xFF8A5A00))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (days <= 0) "تنتهي التجربة المجانية اليوم"
                    else "التجربة المجانية: متبقي $days يوم",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6F4A00)
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(repo: AuthRepository, onSuccess: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("جمعياتي", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = AuthPrimary)
                Text(if (register) "أنشئ حسابك وابدأ 90 يومًا مجانًا" else "تسجيل الدخول إلى حسابك",
                    style = MaterialTheme.typography.titleMedium)
                Text("بيانات الحساب والاشتراك محفوظة بشكل آمن، ولا نضع مفاتيح الدفع داخل التطبيق.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (register) {
                    OutlinedTextField(fullName, { fullName = it }, label = { Text("الاسم الكامل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("كلمة المرور") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = ""
                            try {
                                if (register) repo.signUp(fullName, phone, email, password)
                                else repo.signIn(email, password)
                                onSuccess()
                            } catch (e: Exception) {
                                error = e.message ?: "تعذر تنفيذ العملية"
                            } finally { busy = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                    else Text(if (register) "إنشاء الحساب والبدء مجانًا" else "تسجيل الدخول")
                }

                TextButton({ register = !register; error = "" }, Modifier.fillMaxWidth()) {
                    Text(if (register) "لدي حساب بالفعل" else "إنشاء حساب جديد")
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = AuthPrimary)
                    Spacer(Modifier.width(5.dp))
                    Text("90 يومًا مجانية للحساب الجديد", style = MaterialTheme.typography.labelSmall, color = AuthPrimary)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionScreen(repo: AuthRepository, onRefresh: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val paymentRepo = remember { PaymentRepository(repo) }

    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.CreditCard, null, Modifier.size(52.dp), tint = AuthPrimary)
                Text("انتهت الفترة المجانية", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("استمر في استخدام جمعياتي بعد انتهاء الـ90 يومًا مقابل 10 د.ل لمدة 3 أشهر.", color = MaterialTheme.colorScheme.onSurfaceVariant)

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5F1))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, null, tint = AuthPrimary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("10 د.ل / 3 أشهر", fontWeight = FontWeight.Bold)
                                Text("الدفع بالبطاقة المصرفية الليبية عبر بوابة دفع معتمدة", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            message = ""
                            try {
                                val checkoutUrl = paymentRepo.createCheckoutUrl()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(checkoutUrl)))
                            } catch (e: Exception) {
                                message = e.message ?: "تعذر بدء عملية الدفع"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("الدفع بالبطاقة المصرفية") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repo.signOut()
                            onRefresh()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("تسجيل الخروج") }

                if (message.isNotBlank()) Text(message, color = Color(0xFF8A5A00), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
