package com.therapist.assistant.util

import com.therapist.assistant.data.Workshop
import com.therapist.assistant.data.WorkshopParticipant
import java.io.File
import kotlin.math.sqrt

/**
 * خروجی اکسل رسمی و منظم کارگاه آموزشی.
 * شیت‌ها به ترتیب: شناسنامه · شرکت‌کنندگان · تحلیل نمرات · گزارش کمی · اقدامات · نظرسنجی‌ها
 */
object WorkshopExcelExporter {

    fun suggestedFileName(workshop: Workshop): String {
        val safe = workshop.title.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(" ", "_")
            .take(40)
            .ifBlank { "workshop" }
        val date = try {
            JalaliDate.formatDate(workshop.dateMillis).replace("/", "-")
        } catch (_: Exception) {
            "date"
        }
        return "workshop_${safe}_${date}.xlsx"
    }

    fun buildWorkbookFile(file: File, workshop: Workshop, participants: List<WorkshopParticipant>) {
        val ordered = participants.sortedWith(
            compareByDescending<WorkshopParticipant> { it.attended }
                .thenBy { it.fullName }
        )
        XlsxWriter.write(
            file,
            listOf(
                sheetIdentity(workshop, ordered),
                sheetParticipants(workshop, ordered),
                sheetAttendance(workshop, ordered),
                sheetScoreAnalysis(workshop, ordered),
                sheetCertificates(workshop, ordered),
                sheetQuantitative(workshop, ordered),
                sheetOtherActions(workshop),
                sheetSurvey16(workshop),
                sheetSurvey4(workshop)
            )
        )
    }

    // ───────────────── شیت ۱: شناسنامه و خلاصه ─────────────────

    private fun sheetIdentity(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        val headers = mutableSetOf<Int>()

        fun section(title: String) {
            rows.add(listOf())
            rows.add(listOf(title))
            headers.add(rows.size - 1)
        }

        rows.add(listOf("شناسنامه و خلاصه گزارش کارگاه آموزشی"))
        headers.add(0)
        rows.add(listOf("سامانه", "درمانگرنو", "تاریخ خروجی", JalaliDate.formatDateTime(System.currentTimeMillis())))

        section("الف) مشخصات کارگاه")
        rows.add(listOf("فیلد", "مقدار", "فیلد", "مقدار"))
        headers.add(rows.size - 1)
        rows.add(listOf("عنوان دوره", nz(w.title, "کارگاه آموزشی"), "دسته", nz(w.category, "—")))
        rows.add(listOf("وضعیت", statusFa(w.status), "مدت (ساعت)", w.durationHours.toString()))
        rows.add(listOf("استان", nz(w.province, "خوزستان"), "شهرستان", nz(w.city, "اهواز")))
        rows.add(listOf("مرکز مجری", nz(w.centerName, "گل سرخ"), "مکان برگزاری", nz(w.location, "—")))
        rows.add(listOf("آموزشگر", nz(w.trainerName, "—"), "تاریخ برگزاری", JalaliDate.formatDateTime(w.dateMillis)))
        rows.add(listOf("سقف ظرفیت", if (w.maxCapacity > 0) w.maxCapacity.toString() else "بدون سقف", "سرفصل‌ها", nz(w.topics, "—")))
        if (w.notes.isNotBlank()) rows.add(listOf("یادداشت", w.notes))

        val attended = parts.count { it.attended }
        val certs = parts.count { it.certificateIssued }
        val absent = parts.size - attended
        val stats = scoreStats(parts)

        section("ب) آمار حضور و گواهی")
        rows.add(listOf("شاخص", "مقدار", "توضیح"))
        headers.add(rows.size - 1)
        rows.add(listOf("کل ثبت‌نام‌شدگان", parts.size.toString(), "همه ردیف‌های شرکت‌کننده"))
        rows.add(listOf("حاضر", attended.toString(), percent(attended, parts.size)))
        rows.add(listOf("غایب", absent.toString(), percent(absent, parts.size)))
        rows.add(listOf("گواهی صادر شده", certs.toString(), percent(certs, parts.size)))
        if (w.maxCapacity > 0) {
            val remain = (w.maxCapacity - parts.size).coerceAtLeast(0)
            rows.add(listOf("ظرفیت باقی‌مانده", remain.toString(), "سقف ${w.maxCapacity}"))
        }

        section("ج) خلاصه نمرات پیش‌آزمون / پس‌آزمون")
        rows.add(listOf("شاخص", "مقدار"))
        headers.add(rows.size - 1)
        rows.add(listOf("تعداد دارای نمره پیش", stats.preCount.toString()))
        rows.add(listOf("تعداد دارای نمره پس", stats.postCount.toString()))
        rows.add(listOf("تعداد جفت کامل (پیش و پس)", stats.pairedCount.toString()))
        rows.add(listOf("میانگین پیش‌آزمون", fmt(stats.avgPre)))
        rows.add(listOf("میانگین پس‌آزمون", fmt(stats.avgPost)))
        rows.add(listOf("میانگین تغییر (پس − پیش)", fmt(stats.avgDelta)))
        rows.add(listOf("حداقل / حداکثر پیش", "${fmt(stats.minPre)} / ${fmt(stats.maxPre)}"))
        rows.add(listOf("حداقل / حداکثر پس", "${fmt(stats.minPost)} / ${fmt(stats.maxPost)}"))
        rows.add(listOf("تعداد بهبودیافته", "${stats.improved} از ${stats.pairedCount}"))
        rows.add(listOf("تعداد بدون تغییر", stats.same.toString()))
        rows.add(listOf("تعداد کاهش نمره", stats.declined.toString()))
        rows.add(listOf("درصد بهبود", if (stats.pairedCount == 0) "—" else "%.1f%%".format(stats.improved * 100.0 / stats.pairedCount)))
        rows.add(listOf("انحراف معیار تغییر", fmt(stats.deltaStd)))

        section("د) توزیع تحصیلات")
        val edu = countEducation(parts)
        rows.add(listOf("مقطع", "تعداد", "درصد"))
        headers.add(rows.size - 1)
        val totalEdu = parts.size.coerceAtLeast(1)
        rows.add(listOf("زیر دیپلم", edu.underDiploma.toString(), percent(edu.underDiploma, totalEdu)))
        rows.add(listOf("دیپلم", edu.diploma.toString(), percent(edu.diploma, totalEdu)))
        rows.add(listOf("کارشناسی / کاردانی", edu.bachelor.toString(), percent(edu.bachelor, totalEdu)))
        rows.add(listOf("ارشد و بالاتر", edu.masterPlus.toString(), percent(edu.masterPlus, totalEdu)))

        rows.add(listOf())
        rows.add(listOf("پایان شناسنامه — محرمانه / استفاده داخلی مرکز"))

        return XlsxWriter.Sheet(
            name = "۱-شناسنامه",
            rows = rows,
            headerRowIndices = headers,
            colWidths = listOf(28.0, 36.0, 22.0, 28.0)
        )
    }

    // ───────────────── شیت ۲: شرکت‌کنندگان ─────────────────

    private fun sheetParticipants(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("فهرست شرکت‌کنندگان — ${courseLabel(w)}"))
        rows.add(
            listOf(
                "عنوان", nz(w.title, "کارگاه"),
                "تاریخ", JalaliDate.formatDate(w.dateMillis),
                "آموزشگر", nz(w.trainerName, "—")
            )
        )
        rows.add(listOf())

        val header = listOf(
            "ردیف",
            "نام و نام خانوادگی",
            "کد ملی",
            "تاریخ تولد",
            "سن تقریبی",
            "مدرک تحصیلی",
            "تلفن",
            "نمره پیش",
            "نمره پس",
            "تغییر",
            "وضعیت تغییر",
            "حضور",
            "گواهی",
            "یادداشت"
        )
        rows.add(header)
        val headerRow = rows.size

        parts.forEachIndexed { index, p ->
            val pre = p.preTestScore.trim().toDoubleOrNull()
            val post = p.postTestScore.trim().toDoubleOrNull()
            val deltaStr: String
            val deltaLabel: String
            if (pre != null && post != null) {
                val d = post - pre
                deltaStr = if (d > 0) "+%.1f".format(d) else "%.1f".format(d)
                deltaLabel = when {
                    d > 0.05 -> "بهبود"
                    d < -0.05 -> "کاهش"
                    else -> "ثابت"
                }
            } else {
                deltaStr = "—"
                deltaLabel = "ناقص"
            }
            rows.add(
                listOf(
                    (index + 1).toString(),
                    p.fullName,
                    p.nationalId.ifBlank { "—" },
                    p.birthDateText.ifBlank { "—" },
                    approxAge(p.birthDateText)?.toString() ?: "—",
                    p.education.ifBlank { "—" },
                    p.phone.ifBlank { "—" },
                    p.preTestScore.ifBlank { "—" },
                    p.postTestScore.ifBlank { "—" },
                    deltaStr,
                    deltaLabel,
                    if (p.attended) "حاضر" else "غایب",
                    if (p.certificateIssued) "صادر شد" else "—",
                    p.participantNotes.ifBlank { "—" }
                )
            )
        }

        // جمع‌بندی ته جدول
        rows.add(listOf())
        rows.add(
            listOf(
                "",
                "جمع",
                "",
                "",
                "",
                "",
                "",
                avgCol(parts.map { it.preTestScore }),
                avgCol(parts.map { it.postTestScore }),
                "",
                "",
                parts.count { it.attended }.toString() + " حاضر",
                parts.count { it.certificateIssued }.toString() + " گواهی",
                ""
            )
        )

        return XlsxWriter.Sheet(
            name = "۲-شرکت‌کنندگان",
            rows = rows,
            headerRowIndices = setOf(0, headerRow),
            colWidths = listOf(8.0, 22.0, 14.0, 12.0, 10.0, 14.0, 14.0, 10.0, 10.0, 10.0, 12.0, 10.0, 10.0, 16.0)
        )
    }

    // ───────────────── شیت ۳: تحلیل نمرات ─────────────────

    private fun sheetScoreAnalysis(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        val headers = mutableSetOf<Int>()
        rows.add(listOf("تحلیل نمرات پیش‌آزمون و پس‌آزمون"))
        headers.add(0)
        rows.add(listOf("کارگاه", nz(w.title, "—"), "تاریخ", JalaliDate.formatDate(w.dateMillis)))
        rows.add(listOf())

        rows.add(listOf("نام", "پیش", "پس", "تغییر", "وضعیت"))
        headers.add(rows.size - 1)

        // اولویت با حاضرین دارای نمره کامل؛ در غیر این صورت همه جفت‌های کامل
        val source = parts.filter { it.attended }.ifEmpty { parts }
        val paired = source.mapNotNull { p ->
            val a = p.preTestScore.trim().toDoubleOrNull()
            val b = p.postTestScore.trim().toDoubleOrNull()
            if (a != null && b != null) Triple(p.fullName, a, b) else null
        }.sortedByDescending { it.third - it.second }

        paired.forEach { (name, pre, post) ->
            val d = post - pre
            rows.add(
                listOf(
                    name,
                    "%.1f".format(pre),
                    "%.1f".format(post),
                    if (d > 0) "+%.1f".format(d) else "%.1f".format(d),
                    when {
                        d > 0.05 -> "بهبود"
                        d < -0.05 -> "کاهش"
                        else -> "ثابت"
                    }
                )
            )
        }

        if (paired.isEmpty()) {
            rows.add(listOf("—", "داده جفت کامل پیش/پس موجود نیست", "", "", ""))
        }

        rows.add(listOf())
        rows.add(listOf("افراد بدون نمره کامل"))
        headers.add(rows.size - 1)
        val incomplete = parts.filter {
            it.preTestScore.trim().toDoubleOrNull() == null || it.postTestScore.trim().toDoubleOrNull() == null
        }
        if (incomplete.isEmpty()) {
            rows.add(listOf("همه شرکت‌کنندگان نمره پیش و پس دارند (یا لیست خالی است)"))
        } else {
            incomplete.forEach { p ->
                rows.add(
                    listOf(
                        p.fullName,
                        "پیش: ${p.preTestScore.ifBlank { "خالی" }}",
                        "پس: ${p.postTestScore.ifBlank { "خالی" }}"
                    )
                )
            }
        }

        val stats = scoreStats(parts)
        rows.add(listOf())
        rows.add(listOf("جمع‌بندی آماری"))
        headers.add(rows.size - 1)
        rows.add(listOf("میانگین تغییر", fmt(stats.avgDelta)))
        rows.add(listOf("درصد بهبود", if (stats.pairedCount == 0) "—" else "%.1f%%".format(stats.improved * 100.0 / stats.pairedCount)))
        rows.add(listOf("بیشترین پیشرفت", paired.maxByOrNull { it.third - it.second }?.let { "${it.first} (${"%.1f".format(it.third - it.second)})" } ?: "—"))
        rows.add(listOf("کمترین پیشرفت", paired.minByOrNull { it.third - it.second }?.let { "${it.first} (${"%.1f".format(it.third - it.second)})" } ?: "—"))

        return XlsxWriter.Sheet(
            name = "۴-تحلیل نمرات",
            rows = rows,
            headerRowIndices = headers,
            colWidths = listOf(26.0, 14.0, 14.0, 12.0, 12.0)
        )
    }


    // ───────────────── شیت: حضور و غیاب ─────────────────

    private fun sheetAttendance(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        val headers = mutableSetOf<Int>()
        rows.add(listOf("فهرست حضور و غیاب — ${nz(w.title, "کارگاه")}"))
        headers.add(0)
        rows.add(listOf("تاریخ", JalaliDate.formatDate(w.dateMillis), "آموزشگر", nz(w.trainerName, "—")))
        rows.add(listOf())
        rows.add(listOf("ردیف", "نام و نام خانوادگی", "تلفن", "وضعیت حضور", "امضا"))
        headers.add(rows.size - 1)

        val present = parts.filter { it.attended }
        val absent = parts.filter { !it.attended }

        present.forEachIndexed { i, p ->
            rows.add(listOf((i + 1).toString(), p.fullName, p.phone.ifBlank { "—" }, "حاضر", ""))
        }
        if (present.isEmpty()) {
            rows.add(listOf("—", "هیچ حاضری ثبت نشده", "", "", ""))
        }

        rows.add(listOf())
        rows.add(listOf("غایبین"))
        headers.add(rows.size - 1)
        rows.add(listOf("ردیف", "نام و نام خانوادگی", "تلفن", "وضعیت"))
        headers.add(rows.size - 1)
        absent.forEachIndexed { i, p ->
            rows.add(listOf((i + 1).toString(), p.fullName, p.phone.ifBlank { "—" }, "غایب"))
        }
        if (absent.isEmpty()) {
            rows.add(listOf("—", "غایبی ثبت نشده", "", ""))
        }

        rows.add(listOf())
        rows.add(listOf("جمع حاضر", present.size.toString(), "جمع غایب", absent.size.toString(), "کل", parts.size.toString()))

        return XlsxWriter.Sheet(
            name = "۳-حضور و غیاب",
            rows = rows,
            headerRowIndices = headers,
            colWidths = listOf(8.0, 26.0, 16.0, 14.0, 16.0)
        )
    }

    // ───────────────── شیت: گواهی‌ها ─────────────────

    private fun sheetCertificates(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        val headers = mutableSetOf<Int>()
        val issued = parts.filter { it.certificateIssued }
        val pending = parts.filter { it.attended && !it.certificateIssued }

        rows.add(listOf("گزارش صدور گواهی پایان دوره"))
        headers.add(0)
        rows.add(listOf("کارگاه", nz(w.title, "—"), "تاریخ", JalaliDate.formatDate(w.dateMillis)))
        rows.add(listOf("مرکز", nz(w.centerName, "—"), "آموزشگر", nz(w.trainerName, "—")))
        rows.add(listOf())

        rows.add(listOf("گواهی صادر شده (${issued.size})"))
        headers.add(rows.size - 1)
        rows.add(listOf("ردیف", "نام و نام خانوادگی", "کد ملی", "تلفن", "تاریخ صدور (پیشنهادی)"))
        headers.add(rows.size - 1)
        issued.forEachIndexed { i, p ->
            rows.add(
                listOf(
                    (i + 1).toString(),
                    p.fullName,
                    p.nationalId.ifBlank { "—" },
                    p.phone.ifBlank { "—" },
                    JalaliDate.formatDate(w.dateMillis)
                )
            )
        }
        if (issued.isEmpty()) {
            rows.add(listOf("—", "هنوز گواهی‌ای ثبت نشده", "", "", ""))
        }

        rows.add(listOf())
        rows.add(listOf("حاضر بدون گواهی — در انتظار صدور (${pending.size})"))
        headers.add(rows.size - 1)
        rows.add(listOf("ردیف", "نام و نام خانوادگی", "کد ملی", "تلفن"))
        headers.add(rows.size - 1)
        pending.forEachIndexed { i, p ->
            rows.add(listOf((i + 1).toString(), p.fullName, p.nationalId.ifBlank { "—" }, p.phone.ifBlank { "—" }))
        }
        if (pending.isEmpty()) {
            rows.add(listOf("—", "موردی نیست", "", ""))
        }

        return XlsxWriter.Sheet(
            name = "۵-گواهی‌ها",
            rows = rows,
            headerRowIndices = headers,
            colWidths = listOf(8.0, 26.0, 16.0, 16.0, 18.0)
        )
    }

    // ───────────────── شیت ۴: گزارش کمی ─────────────────

    private fun sheetQuantitative(w: Workshop, parts: List<WorkshopParticipant>): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        val headers = mutableSetOf<Int>()
        val family = isFamilyCourse(w)

        rows.add(
            listOf(
                if (family) "جدول گزارش کمی دوره‌های آموزش زندگی خانواده"
                else "جدول گزارش کمی دوره‌های آموزشی پیش از ازدواج / عمومی"
            )
        )
        headers.add(0)

        rows.add(
            listOf(
                "نام استان", nz(w.province, "خوزستان"),
                "نام شهرستان", nz(w.city, "اهواز"),
                "نام مرکز", nz(w.centerName, "گل سرخ")
            )
        )
        rows.add(
            listOf(
                "عنوان دوره", nz(w.title, "کارگاه"),
                "مدت زمان", formatDuration(w.durationHours),
                "آموزشگر", nz(w.trainerName, "—")
            )
        )
        rows.add(
            listOf(
                "تاریخ برگزاری", JalaliDate.formatDateTime(w.dateMillis),
                "تعداد شرکت‌کننده", parts.size.toString(),
                "حاضر", parts.count { it.attended }.toString()
            )
        )
        rows.add(listOf("سرفصل‌ها", nz(w.topics, w.title)))
        rows.add(listOf())

        rows.add(listOf("وضعیت تحصیلات فراگیران"))
        headers.add(rows.size - 1)
        val edu = countEducation(parts)
        rows.add(listOf("زیر دیپلم", "دیپلم", "لیسانس/کاردانی", "فوق‌لیسانس و بالاتر", "جمع"))
        headers.add(rows.size - 1)
        rows.add(
            listOf(
                edu.underDiploma.toString(),
                edu.diploma.toString(),
                edu.bachelor.toString(),
                edu.masterPlus.toString(),
                parts.size.toString()
            )
        )

        rows.add(listOf())
        rows.add(listOf("بازه سنی تقریبی (بر اساس سال تولد)"))
        headers.add(rows.size - 1)
        val ages = parts.mapNotNull { approxAge(it.birthDateText) }
        rows.add(listOf("تعداد با سن مشخص", ages.size.toString()))
        if (ages.isNotEmpty()) {
            rows.add(listOf("میانگین سن", "%.1f".format(ages.average())))
            rows.add(listOf("کمینه / بیشینه سن", "${ages.minOrNull()} / ${ages.maxOrNull()}"))
        }

        return XlsxWriter.Sheet(
            name = "۶-گزارش کمی",
            rows = rows,
            headerRowIndices = headers,
            colWidths = listOf(22.0, 20.0, 20.0, 22.0, 14.0)
        )
    }

    // ───────────────── شیت ۵: سایر اقدامات ─────────────────

    private fun sheetOtherActions(w: Workshop): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("سایر اقدامات و پیگیری پس از کارگاه"))
        rows.add(listOf("عنوان کارگاه", nz(w.title, "—")))
        rows.add(listOf())
        rows.add(listOf("ردیف", "اقدام", "مسئول", "مهلت", "انجام شد؟", "توضیح"))
        val header = rows.size
        repeat(8) { i ->
            rows.add(listOf((i + 1).toString(), "", "", "", "", ""))
        }
        rows.add(listOf())
        rows.add(listOf("یادداشت کارگاه (از سامانه)", w.notes.ifBlank { "—" }))
        return XlsxWriter.Sheet(
            name = "۷-اقدامات",
            rows = rows,
            headerRowIndices = setOf(0, header),
            colWidths = listOf(8.0, 28.0, 16.0, 14.0, 12.0, 24.0)
        )
    }

    // ───────────────── شیت ۶ و ۷: نظرسنجی ─────────────────

    private fun sheetSurvey16(w: Workshop): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("فرم نظرسنجی جامع کارگاه (۱۶ گویه)"))
        rows.add(listOf("عنوان", nz(w.title, "—"), "تاریخ", JalaliDate.formatDate(w.dateMillis)))
        rows.add(listOf())
        rows.add(listOf("محور", "گویه", "کاملاً موافق", "موافق", "مخالف", "کاملاً مخالف"))
        val header = rows.size
        val items = listOf(
            "محتوا" to "محتوای کارگاه با نیاز من مرتبط بود.",
            "محتوا" to "مطالب به‌صورت کاربردی ارائه شد.",
            "محتوا" to "حجم مطالب با زمان کارگاه متناسب بود.",
            "مدرس" to "مدرس بر موضوع تسلط داشت.",
            "مدرس" to "پاسخگویی مدرس به پرسش‌ها مناسب بود.",
            "مدرس" to "بیان مدرس روان و قابل فهم بود.",
            "سازماندهی" to "زمان‌بندی کارگاه منظم بود.",
            "سازماندهی" to "امکانات و مکان برگزاری مناسب بود.",
            "سازماندهی" to "اطلاع‌رسانی قبل از کارگاه کافی بود.",
            "نتیجه" to "آموخته‌ها در زندگی/رابطه قابل استفاده‌اند.",
            "نتیجه" to "انگیزه من برای ادامه مسیر افزایش یافت.",
            "نتیجه" to "این کارگاه را به دیگران پیشنهاد می‌کنم.",
            "تعامل" to "فضای کارگاه امن و محترمانه بود.",
            "تعامل" to "امکان مشارکت فراگیران فراهم بود.",
            "تعامل" to "تمرین‌ها و فعالیت‌ها مفید بودند.",
            "کلی" to "در مجموع از کارگاه رضایت دارم."
        )
        items.forEach { (axis, q) -> rows.add(listOf(axis, q, "", "", "", "")) }
        rows.add(listOf())
        rows.add(listOf("پیشنهادها و توضیحات تکمیلی:"))
        rows.add(listOf("", "", "", "", "", ""))
        rows.add(listOf("", "", "", "", "", ""))
        return XlsxWriter.Sheet(
            name = "۸-نظرسنجی جامع",
            rows = rows,
            headerRowIndices = setOf(0, header),
            colWidths = listOf(14.0, 52.0, 14.0, 12.0, 12.0, 14.0)
        )
    }

    private fun sheetSurvey4(w: Workshop): XlsxWriter.Sheet {
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("فرم نظرسنجی کوتاه (۴ ساعته)"))
        rows.add(listOf("عنوان", nz(w.title, "—"), "تاریخ", JalaliDate.formatDate(w.dateMillis)))
        rows.add(listOf())
        rows.add(listOf("محور", "گویه", "کاملاً موافق", "موافق", "مخالف", "کاملاً مخالف"))
        val header = rows.size
        val items = listOf(
            "محتوا" to "محتوای کارگاه کاربردی بود.",
            "مدرس" to "تسلط علمی مدرس مناسب بود.",
            "مکان" to "مکان و امکانات برگزاری مناسب بود.",
            "نتیجه" to "این کارگاه روش خوبی برای یادگیری بود."
        )
        items.forEach { (axis, q) -> rows.add(listOf(axis, q, "", "", "", "")) }
        rows.add(listOf())
        rows.add(listOf("پیشنهاد شما برای بهبود کارگاه‌های بعدی چیست؟"))
        rows.add(listOf("", "", "", "", "", ""))
        return XlsxWriter.Sheet(
            name = "۹-نظرسنجی کوتاه",
            rows = rows,
            headerRowIndices = setOf(0, header),
            colWidths = listOf(14.0, 52.0, 14.0, 12.0, 12.0, 14.0)
        )
    }

    // ───────────────── helpers ─────────────────

    private fun statusFa(s: String) = when (s.uppercase()) {
        "DRAFT" -> "پیش‌نویس"
        "DONE" -> "برگزار شد"
        "CANCELLED" -> "لغو شده"
        else -> "برنامه‌ریزی‌شده"
    }

    private fun courseLabel(w: Workshop): String = when {
        isFamilyCourse(w) -> "زندگی خانواده"
        w.title.contains("ازدواج") || w.title.contains("پیش از") -> "پیش از ازدواج"
        w.category.isNotBlank() -> w.category
        else -> w.title.ifBlank { "کارگاه آموزشی" }
    }

    private fun isFamilyCourse(w: Workshop): Boolean =
        w.title.contains("خانواده") || w.topics.contains("خانواده") || w.category.contains("خانواده")

    private fun formatDuration(hours: Int): String =
        if (hours <= 0) "۴ ساعت" else "$hours ساعت"

    private fun nz(s: String, fallback: String) = s.trim().ifBlank { fallback }

    private fun percent(n: Int, total: Int): String =
        if (total <= 0) "—" else "%.1f%%".format(n * 100.0 / total)

    private fun fmt(v: Double?): String =
        if (v == null || v.isNaN()) "—" else "%.2f".format(v)

    private fun avgCol(values: List<String>): String {
        val nums = values.mapNotNull { it.trim().toDoubleOrNull() }
        return if (nums.isEmpty()) "—" else "%.1f".format(nums.average())
    }

    private data class ScoreStats(
        val preCount: Int,
        val postCount: Int,
        val pairedCount: Int,
        val avgPre: Double?,
        val avgPost: Double?,
        val avgDelta: Double?,
        val minPre: Double?,
        val maxPre: Double?,
        val minPost: Double?,
        val maxPost: Double?,
        val improved: Int,
        val same: Int,
        val declined: Int,
        val deltaStd: Double?
    )

    private fun scoreStats(parts: List<WorkshopParticipant>): ScoreStats {
        val pre = parts.mapNotNull { it.preTestScore.trim().toDoubleOrNull() }
        val post = parts.mapNotNull { it.postTestScore.trim().toDoubleOrNull() }
        val paired = parts.mapNotNull { p ->
            val a = p.preTestScore.trim().toDoubleOrNull()
            val b = p.postTestScore.trim().toDoubleOrNull()
            if (a != null && b != null) a to b else null
        }
        val deltas = paired.map { it.second - it.first }
        val improved = deltas.count { it > 0.05 }
        val declined = deltas.count { it < -0.05 }
        val same = deltas.size - improved - declined
        val std = if (deltas.size >= 2) {
            val mean = deltas.average()
            sqrt(deltas.map { (it - mean) * (it - mean) }.average())
        } else null
        return ScoreStats(
            preCount = pre.size,
            postCount = post.size,
            pairedCount = paired.size,
            avgPre = pre.takeIf { it.isNotEmpty() }?.average(),
            avgPost = post.takeIf { it.isNotEmpty() }?.average(),
            avgDelta = deltas.takeIf { it.isNotEmpty() }?.average(),
            minPre = pre.minOrNull(),
            maxPre = pre.maxOrNull(),
            minPost = post.minOrNull(),
            maxPost = post.maxOrNull(),
            improved = improved,
            same = same,
            declined = declined,
            deltaStd = std
        )
    }

    private data class EduCount(
        val underDiploma: Int,
        val diploma: Int,
        val bachelor: Int,
        val masterPlus: Int
    )

    private fun countEducation(parts: List<WorkshopParticipant>): EduCount {
        var under = 0
        var dip = 0
        var bs = 0
        var ms = 0
        parts.forEach { p ->
            val t = p.education.trim()
            when {
                t.contains("فوق") && (t.contains("لیسانس") || t.contains("ارشد")) ||
                    t.contains("دکت") || t.contains("ارشد") -> ms++
                t.contains("لیسانس") || t.contains("کارشناسی") ||
                    t.contains("کاردانی") || t.contains("فوق دیپلم") -> bs++
                t.contains("دیپلم") -> dip++
                t.isBlank() -> under++
                else -> under++
            }
        }
        return EduCount(under, dip, bs, ms)
    }

    private fun approxAge(birth: String): Int? {
        val year = Regex("""(13\d{2}|14\d{2})""").find(birth)?.value?.toIntOrNull() ?: return null
        val nowY = try {
            JalaliDate.fromMillis(System.currentTimeMillis()).year
        } catch (_: Exception) {
            1404
        }
        return (nowY - year).takeIf { it in 5..100 }
    }
}
