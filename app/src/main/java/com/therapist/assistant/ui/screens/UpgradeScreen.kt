package com.therapist.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.therapist.assistant.data.LicensePlan
import com.therapist.assistant.ui.components.GradientTopBar
import com.therapist.assistant.ui.components.SoftCard
import com.therapist.assistant.viewmodel.AppointmentViewModel

@Composable
fun UpgradeScreen(
    viewModel: AppointmentViewModel,
    onBack: () -> Unit
) {
    val isPro by viewModel.isPro.collectAsState()
    val isOrg by viewModel.isOrg.collectAsState()
    val trialActive = viewModel.isTrialActive()
    val daysLeft = viewModel.trialDaysRemaining()
    val plan = viewModel.currentPlan()
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            GradientTopBar(
                title = "اشتراک و ارتقا",
                subtitle = "پلن فعلی: ${plan.titleFa}",
                onBack = onBack
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftCard {
                    Text("وضعیت لایسنس", fontWeight = FontWeight.Bold)
                    when {
                        isOrg -> Text("سازمانی فعال — چند درمانگر، منشی و گزارش مرکز")
                        isPro -> Text("حرفه‌ای فردی فعال — بدون سقف مراجع و آزمون")
                        trialActive -> Text("آزمایشی — $daysLeft روز مانده از ۱۴ روز (همه قابلیت‌ها)")
                        else -> Text("رایگان — تا ${LicensePlan.FREE.clientLimit} مراجع و آزمون‌های غربالگری پایه")
                    }
                }

                SoftCard {
                    Text("مقایسه پلن‌ها", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    PlanRow("قابلیت", "رایگان", "حرفه‌ای", "سازمانی")
                    HorizontalDivider()
                    PlanRow("سقف مراجع", "${LicensePlan.FREE.clientLimit}", "نامحدود", "نامحدود")
                    PlanRow("درمانگر", "۱", "تا ۳", "تا ۵۰")
                    PlanRow("همه آزمون‌ها", "خیر", "بله", "بله")
                    PlanRow("اکسل کارگاه", "خیر*", "بله", "بله")
                    PlanRow("خروجی مالی کامل", "پایه", "کامل", "کامل")
                    PlanRow("نقش منشی", "—", "—", "بله")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "* در دوره آزمایشی ۱۴روزه همه قابلیت‌های حرفه‌ای فعال است.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SoftCard {
                    Text("چرا حرفه‌ای؟", fontWeight = FontWeight.Bold)
                    listOf(
                        "خروجی اکسل رسمی کارگاه با آمار حضور، نمرات و گواهی",
                        "همه آزمون‌های بالینی و روند درمان",
                        "مراجع نامحدود و چند درمانگر",
                        "بکاپ پیشرفته و قالب‌های پیام حرفه‌ای"
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }

                SoftCard {
                    Text("فعال‌سازی با کد", fontWeight = FontWeight.Bold)
                    Text(
                        "کد حرفه‌ای یا سازمانی (ORG-…) را وارد کنید. هر کد فقط یک‌بار روی همین دستگاه.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("کد فعال‌سازی") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val err = viewModel.unlockWithCodeDetailed(code)
                            if (err == null) {
                                msgOk = true
                                msg = "✓ نسخه ویژه فعال شد"
                                code = ""
                            } else {
                                msgOk = false
                                msg = err
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("فعال‌سازی") }
                    msg?.let {
                        Text(
                            it,
                            color = if (msgOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "برای دریافت کد: کانال تیم توسعهٔ نو @new_development_ir یا بخش درباره ما.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (trialActive) {
                    SoftCard {
                        Text("دوره آزمایشی", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { (daysLeft.toFloat() / 14f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                        Text("$daysLeft روز باقی‌مانده — بعد از آن به رایگان برمی‌گردید مگر با کد.")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(label: String, free: String, pro: String, org: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.labelMedium)
        Text(free, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        Text(pro, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(org, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
    }
}
