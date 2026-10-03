package ly.jam3yati.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { Jam3yatiApp() } }
}

@Composable fun Jam3yatiApp() {
 var selected by remember { mutableStateOf(0) }
 val items = listOf("الرئيسية", "الجمعيات", "الدفعات", "الأعضاء")
 Scaffold(bottomBar={ NavigationBar { items.forEachIndexed { i, label -> NavigationBarItem(selected=i==selected,onClick={selected=i},icon={},label={Text(label)}) } } }) { p ->
  Column(Modifier.fillMaxSize().padding(p).padding(20.dp), horizontalAlignment=Alignment.CenterHorizontally) {
   Text("جمعياتي", style=MaterialTheme.typography.headlineLarge)
   Spacer(Modifier.height(8.dp))
   Text("إدارة الجمعيات المالية بسهولة وأمان")
   Spacer(Modifier.height(28.dp))
   when(selected) { 0 -> Dashboard(); 1 -> Placeholder("الجمعيات"); 2 -> Placeholder("الدفعات"); else -> Placeholder("الأعضاء") }
  }
 }
}

@Composable private fun Dashboard() { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("لوحة المتابعة", style=MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); Text("لا توجد بيانات سحابية بعد. سيتم ربط التطبيق بـ Supabase في المرحلة التالية.") } } }
@Composable private fun Placeholder(title:String) { Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text(title, style=MaterialTheme.typography.headlineMedium) } }
