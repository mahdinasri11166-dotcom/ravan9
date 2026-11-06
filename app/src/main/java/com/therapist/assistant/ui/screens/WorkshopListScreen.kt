package com.therapist.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.therapist.assistant.ui.components.EmptyState
import com.therapist.assistant.ui.components.GradientTopBar
import com.therapist.assistant.ui.components.SoftCard
import com.therapist.assistant.util.JalaliDate
import com.therapist.assistant.viewmodel.AppointmentViewModel

@Composable
fun WorkshopListScreen(
    viewModel: AppointmentViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit
) {
    val list by viewModel.workshops.collectAsState()
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    val filtered = remember(list, query, statusFilter) {
        list.filter { w ->
            (statusFilter == "ALL" || w.status == statusFilter) &&
                (query.isBlank() ||
                    w.title.contains(query, true) ||
                    w.trainerName.contains(query, true) ||
                    w.category.contains(query, true) ||
                    w.city.contains(query, true))
        }
    }

    fun statusFa(s: String) = when (s) {
        "DRAFT" -> "پیش‌نویس"
        "DONE" -> "برگزار شد"
        "CANCELLED" -> "لغو"
        else -> "برنامه‌ریزی"
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("کارگاه جدید", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GradientTopBar(
                title = "کارگاه‌های آموزشی",
                subtitle = "${filtered.size} مورد",
                onBack = onBack
            )
            if (list.isEmpty()) {
                Box(Modifier.padding(16.dp)) {
                    EmptyState(
                        Icons.Default.Groups,
                        "هنوز کارگاهی ثبت نشده",
                        "شرکت‌کنندگان را از لیست مراجعین انتخاب کنید و اکسل بگیرید",
                        "ایجاد کارگاه",
                        onCreate
                    )
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جستجو عنوان / آموزشگر / دسته") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "ALL" to "همه",
                        "SCHEDULED" to "برنامه",
                        "DONE" to "برگزار",
                        "DRAFT" to "پیش‌نویس"
                    ).forEach { (id, lab) ->
                        FilterChip(
                            selected = statusFilter == id,
                            onClick = { statusFilter = id },
                            label = { Text(lab) }
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { w ->
                        SoftCard(onClick = { onOpen(w.id) }) {
                            Text(w.title, fontWeight = FontWeight.Bold)
                            Text(
                                "${JalaliDate.formatDate(w.dateMillis)} · ${w.durationHours} ساعت · ${w.trainerName.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${w.city} — ${w.centerName} · ${statusFa(w.status)}" +
                                    (w.category.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    viewModel.duplicateWorkshop(w.id) { newId -> onOpen(newId) }
                                }) { Text("کپی") }
                                TextButton(
                                    onClick = { viewModel.deleteWorkshop(w) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("حذف") }
                            }
                        }
                    }
                }
            }
        }
    }
}
