/**
 * 本文件提供每日利息应用的 Compose 主页、可交互日历、当日事件列表和本金录入弹窗。
 */
package com.dailyinterest.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 页面使用的浅灰背景色。 */
private val appBackground = Color(0xFFF6F7F9)

/** 页面主文字使用的深色。 */
private val ink = Color(0xFF1D2228)

/** 次级文字使用的灰色。 */
private val mutedInk = Color(0xFF7B828C)

/** 有日息的日期和本金金额使用的红色。 */
private val interestRed = Color(0xFFE14B5E)

/** 有日息日期使用的浅红背景色。 */
private val interestDayBackground = Color(0xFFFFE8EB)

/** 提现本金气泡使用的绿色文字。 */
private val withdrawalGreen = Color(0xFF16865B)

/** 本金变动气泡使用的圆角主体和下尖角一体化形状。 */
private val principalChangeBubbleShape = GenericShape { size, _ ->
    val tailHeight = size.height * 0.24f
    val bodyBottom = size.height - tailHeight
    val cornerRadius = bodyBottom * 0.28f
    val tailBaseX = size.width * 0.32f
    val tailTipX = size.width * 0.03f

    moveTo(cornerRadius, 0f)
    lineTo(size.width - cornerRadius, 0f)
    quadraticBezierTo(size.width, 0f, size.width, cornerRadius)
    lineTo(size.width, bodyBottom - cornerRadius)
    quadraticBezierTo(size.width, bodyBottom, size.width - cornerRadius, bodyBottom)
    lineTo(tailBaseX, bodyBottom)
    lineTo(tailTipX, size.height)
    lineTo(0f, bodyBottom - cornerRadius)
    lineTo(0f, cornerRadius)
    quadraticBezierTo(0f, 0f, cornerRadius, 0f)
    close()
}

/** 日息金额使用的深黄色。 */
private val interestYellow = Color(0xFFB97D00)

/** 悬浮操作按钮使用的亮黄色。 */
private val accentYellow = Color(0xFFFFC400)

/** 分隔线使用的浅灰色。 */
private val dividerColor = Color(0xFFE7EAEE)

/** Compose 组件使用的浅色配色方案。 */
private val dailyInterestColorScheme = lightColorScheme(
    primary = accentYellow,
    onPrimary = ink,
    secondary = interestRed,
    background = appBackground,
    onBackground = ink,
    surface = Color.White,
    onSurface = ink,
    outline = dividerColor,
)

/**
 * 渲染每日利息应用的完整主页。
 */
@Composable
fun DailyInterestApp() {
    val context = LocalContext.current
    val today = rememberCurrentDate()
    val store = remember { PrincipalChangeStore(context.applicationContext) }
    var configuration by remember { mutableStateOf(store.loadConfiguration()) }
    val initialMonthPage = remember {
        val initialMonth = initialDisplayMonth()
        (initialMonth.year - InterestCalculator.firstSupportedMonth.year) * 12 +
            initialMonth.monthValue - InterestCalculator.firstSupportedMonth.monthValue
    }
    val monthPagerState = rememberPagerState(
        initialPage = initialMonthPage,
        pageCount = {
            (InterestCalculator.lastSupportedMonth.year - InterestCalculator.firstSupportedMonth.year) * 12 +
                InterestCalculator.lastSupportedMonth.monthValue -
                InterestCalculator.firstSupportedMonth.monthValue + 1
        },
    )
    val coroutineScope = rememberCoroutineScope()
    var isEntryDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isInterestRateDialogVisible by rememberSaveable { mutableStateOf(false) }
    var selectedDateEpochDay by rememberSaveable { mutableStateOf(InterestCalculator.clampDate(today).toEpochDay()) }
    // 翻月后优先选中当月今日，其他月份选中一日，避免事件日期与当前月份不符。
    LaunchedEffect(monthPagerState.settledPage) {
        val month = InterestCalculator.firstSupportedMonth.plusMonths(monthPagerState.settledPage.toLong())
        if (YearMonth.from(LocalDate.ofEpochDay(selectedDateEpochDay)) != month) {
            selectedDateEpochDay = if (YearMonth.from(today) == month) today.toEpochDay() else month.atDay(1).toEpochDay()
        }
    }
    val schedule = remember(configuration) {
        InterestCalculator.createSchedule(
            principalChanges = configuration.principalChanges,
            annualInterestRate = configuration.annualInterestRate,
            interestRateChanges = configuration.interestRateChanges,
        )
    }
    val summary = remember(schedule, today) {
        InterestCalculator.calculateSummary(schedule, today)
    }
    val currentAnnualInterestRate = remember(configuration, today) {
        InterestCalculator.currentAnnualInterestRate(
            date = today,
            annualInterestRate = configuration.annualInterestRate,
            interestRateChanges = configuration.interestRateChanges,
        )
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { targetFile ->
            runCatching {
                context.contentResolver.openOutputStream(targetFile)
                    ?.bufferedWriter()
                    ?.use { writer -> writer.write(store.serializeConfiguration(configuration)) }
                    ?: error("无法写入文件")
            }.onSuccess {
                Toast.makeText(context, "配置已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { sourceFile ->
            runCatching {
                val fileContent = context.contentResolver.openInputStream(sourceFile)
                    ?.bufferedReader()
                    ?.use { reader -> reader.readText() }
                    ?: error("无法读取文件")
                store.parseConfiguration(fileContent) ?: error("文件格式不正确")
            }.onSuccess { importedConfiguration ->
                store.saveConfiguration(importedConfiguration)
                configuration = importedConfiguration
                Toast.makeText(context, "配置已导入", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导入失败，请选择有效配置文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DailyInterestTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground),
        ) {
            InterestTopBar(
                summary = summary,
                annualInterestRate = currentAnnualInterestRate,
                onAddInterestRateChange = { isInterestRateDialogVisible = true },
                onImport = {
                    importLauncher.launch(arrayOf("*/*"))
                },
                onExport = {
                    // 使用点击导出时的本机年月日时分作为文件名后缀。
                    exportLauncher.launch("每日利息配置_${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))}.json")
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 白色日历卡片弹性占用剩余高度，内容较多时允许在卡片内纵向滚动。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 2.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    // 日历内容使用可滚动分页，悬浮按钮固定在卡片右下角。
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // 横向分页支持手势滑动和月份按钮两种翻页方式。
                        HorizontalPager(
                            state = monthPagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val month = InterestCalculator.firstSupportedMonth.plusMonths(page.toLong())
                            val monthTotals = remember(schedule, month) {
                                InterestCalculator.calculateMonthTotals(schedule, month)
                            }
                            CalendarSection(
                                month = month,
                                schedule = schedule,
                                monthTotals = monthTotals,
                                today = today,
                                selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay),
                                principalChanges = configuration.principalChanges,
                                interestRateChanges = configuration.interestRateChanges,
                                annualInterestRate = configuration.annualInterestRate,
                                onPreviousMonth = {
                                    if (page > 0) {
                                        coroutineScope.launch {
                                            monthPagerState.animateScrollToPage(page - 1)
                                        }
                                    }
                                },
                                onNextMonth = {
                                    if (page < monthPagerState.pageCount - 1) {
                                        coroutineScope.launch {
                                            monthPagerState.animateScrollToPage(page + 1)
                                        }
                                    }
                                },
                                onDateSelected = { date -> selectedDateEpochDay = date.toEpochDay() },
                            )
                        }
                    }

                    // 右下角悬浮按钮用于新增一笔本金变动。
                    FloatingActionButton(
                        onClick = { isEntryDialogVisible = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 24.dp)
                            .size(60.dp),
                        containerColor = accentYellow,
                        contentColor = ink,
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "记录本金变动",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
        }

        if (isEntryDialogVisible) {
            // 本金录入弹窗独立覆盖在主页之上。
            PrincipalChangeDialog(
                initialDate = InterestCalculator.clampDate(today),
                onDismiss = { isEntryDialogVisible = false },
                onSave = { date, amount ->
                    val updatedConfiguration = configuration.copy(
                        principalChanges = configuration.principalChanges + PrincipalChange(
                            date = date,
                            amount = amount,
                        ),
                    )
                    store.saveConfiguration(updatedConfiguration)
                    configuration = updatedConfiguration
                    isEntryDialogVisible = false
                },
            )
        }

        if (isInterestRateDialogVisible) {
            // 年利率变更弹窗独立覆盖在主页之上。
            InterestRateChangeDialog(
                initialDate = InterestCalculator.clampDate(today),
                initialRate = currentAnnualInterestRate,
                onDismiss = { isInterestRateDialogVisible = false },
                onSave = { date, rate ->
                    val updatedConfiguration = configuration.copy(
                        interestRateChanges = configuration.interestRateChanges + InterestRateChange(date, rate),
                    )
                    store.saveConfiguration(updatedConfiguration)
                    configuration = updatedConfiguration
                    isInterestRateDialogVisible = false
                },
            )
        }
    }
}

/**
 * 渲染包含金额摘要和配置文件菜单的应用顶部信息区。
 *
 * @param summary 当前日期的金额摘要。
 * @param annualInterestRate 当前配置使用的年利率。
 * @param onAddInterestRateChange 用户新增年利率变更时的回调。
 * @param onImport 用户选择从文件导入时的回调。
 * @param onExport 用户选择导出配置时的回调。
 */
@Composable
private fun InterestTopBar(
    summary: InterestSummary,
    annualInterestRate: BigDecimal,
    onAddInterestRateChange: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var isMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF43B45), Color(0xFFFF6A00)),
                ),
            ),
    ) {
        // 顶栏保留应用名称和配置文件操作入口。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 20.dp, end = 8.dp, top = 28.dp),
        ) {
            Text(
                text = "每日利息",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { isMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "配置文件菜单",
                        tint = Color.White,
                    )
                }
                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("记录利率变更") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.DateRange,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onAddInterestRateChange()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("从文件导入") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onImport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出到文件") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onExport()
                        },
                    )
                }
            }
        }
        // 总金额固定在信息区中央，资产明细独立贴右显示。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .padding(top = 24.dp, bottom = 24.dp),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "总金额",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                )
                Text(
                    text = InterestCalculator.formatTotalAmount(summary.total),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "年利率 ${InterestCalculator.formatAnnualInterestRate(annualInterestRate)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }
            // 右侧四行小字展示本金和累计利息明细。
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 4.dp, y = 8.dp)
                    .width(72.dp)
                    .padding(end = 6.dp),
            ) {
                Text(
                    text = "本金",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = InterestCalculator.formatTotalAmount(summary.principal),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = "累计利息",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = InterestCalculator.formatTotalAmount(summary.accumulatedInterest),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/**
 * 保存并在跨午夜后刷新当前系统日期。
 *
 * @return 会在日期变化后自动更新的当前日期状态值。
 */
@Composable
private fun rememberCurrentDate(): LocalDate {
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay((nextDay.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()).coerceAtLeast(1_000L))
            currentDate = LocalDate.now()
        }
    }

    return currentDate
}

/**
 * 应用 Compose 主题，使页面在所有设备尺寸上保持统一的原生浅色外观。
 *
 * @param content 需要使用主题渲染的页面内容。
 */
@Composable
private fun DailyInterestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = dailyInterestColorScheme,
        content = content,
    )
}


/**
 * 渲染页面下部可翻月的利息日历区域。
 *
 * @param month 当前显示的月份。
 * @param schedule 全部支持日期的逐日利息结果。
 * @param monthTotals 当前月份的利息与本金变动汇总。
 * @param today 会自动刷新的当前系统日期。
 * @param selectedDate 当前选中的日期。
 * @param principalChanges 全部本金变动记录。
 * @param interestRateChanges 全部年利率变更记录。
 * @param annualInterestRate 配置的初始年利率。
 * @param onPreviousMonth 用户请求查看上一个月时的回调。
 * @param onNextMonth 用户请求查看下一个月时的回调。
 * @param onDateSelected 用户点击日期时的回调。
 */
@Composable
private fun CalendarSection(
    month: YearMonth,
    schedule: Map<LocalDate, DailyInterestRecord>,
    monthTotals: MonthTotals,
    today: LocalDate,
    selectedDate: LocalDate,
    principalChanges: List<PrincipalChange>,
    interestRateChanges: List<InterestRateChange>,
    annualInterestRate: BigDecimal,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 92.dp),
    ) {
            Text(
                text = "利息日历",
                color = ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 当前月份的利息和存入金额紧跟日历标题显示。
            MonthTotalRow(monthTotals = monthTotals)
            Spacer(modifier = Modifier.height(6.dp))
            MonthNavigation(
                month = month,
                canGoPrevious = month > InterestCalculator.firstSupportedMonth,
                canGoNext = month < InterestCalculator.lastSupportedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
            Spacer(modifier = Modifier.height(8.dp))
            WeekdayHeader()
            Spacer(modifier = Modifier.height(4.dp))
            // 固定六行的日期网格区域。
            CalendarGrid(
                month = month,
                schedule = schedule,
                today = today,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SelectedDateEvents(
                selectedDate = selectedDate,
                principalChanges = principalChanges,
                interestRateChanges = interestRateChanges,
                annualInterestRate = annualInterestRate,
            )
    }
}

/**
 * 展示所选日期的利率变更与逐笔买入、赎回事件，无记录时显示空状态。
 *
 * @param selectedDate 当前选中的日期。
 * @param principalChanges 全部本金变动记录，保留同日多笔记录。
 * @param interestRateChanges 全部年利率变更记录，同日按保存顺序展示。
 * @param annualInterestRate 配置的初始年利率，用于显示首次变更前的利率。
 */
@Composable
private fun SelectedDateEvents(
    selectedDate: LocalDate,
    principalChanges: List<PrincipalChange>,
    interestRateChanges: List<InterestRateChange>,
    annualInterestRate: BigDecimal,
) {
    val dailyPrincipalChanges = principalChanges.filter { it.date == selectedDate }
    val dailyRateChanges = interestRateChanges.filter { it.date == selectedDate }
    // 同日多次调整沿用保存顺序，首笔调整从此前最后一次生效的利率开始展示。
    val previousRate = interestRateChanges
        .asReversed()
        .filter { it.date < selectedDate }
        .maxByOrNull { it.date }
        ?.annualInterestRate ?: annualInterestRate

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${InterestCalculator.formatDate(selectedDate)} · 当日事件",
            color = ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (dailyRateChanges.isEmpty() && dailyPrincipalChanges.isEmpty()) {
            Text(text = "当日暂无事件", color = mutedInk, fontSize = 12.sp)
        }
        dailyRateChanges.forEachIndexed { index, change ->
            DateEventRow(
                title = "利率变更",
                detail = "${InterestCalculator.formatAnnualInterestRate(if (index == 0) previousRate else dailyRateChanges[index - 1].annualInterestRate)} → ${InterestCalculator.formatAnnualInterestRate(change.annualInterestRate)}",
                eventColor = interestYellow,
            )
        }
        dailyPrincipalChanges.forEach { change ->
            DateEventRow(
                title = if (change.amount.signum() >= 0) "买入" else "赎回",
                detail = "${InterestCalculator.formatSignedAmount(change.amount)} 元",
                eventColor = if (change.amount.signum() >= 0) interestRed else withdrawalGreen,
            )
        }
    }
}

/**
 * 使用弹性行布局显示事件名称与数值，长文本自动换行。
 *
 * @param title 事件名称。
 * @param detail 事件金额或变更前后的年利率。
 * @param eventColor 用于区分不同事件的颜色。
 */
@Composable
private fun DateEventRow(title: String, detail: String, eventColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(eventColor.copy(alpha = 0.07f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(5.dp).background(eventColor, CircleShape))
        Text(text = title, modifier = Modifier.weight(1f), color = ink, fontSize = 12.sp)
        Text(
            text = detail,
            modifier = Modifier.weight(2f),
            color = eventColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * 渲染月份标题和受范围限制的前后翻月按钮。
 *
 * @param month 当前显示的月份。
 * @param canGoPrevious 是否允许翻到上一个月。
 * @param canGoNext 是否允许翻到下一个月。
 * @param onPreviousMonth 点击上一月按钮时的回调。
 * @param onNextMonth 点击下一月按钮时的回调。
 */
@Composable
private fun MonthNavigation(
    month: YearMonth,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousMonth,
            enabled = canGoPrevious,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "上一个月",
                tint = if (canGoPrevious) ink else dividerColor,
            )
        }
        Text(
            text = "${month.year}年${month.monthValue}月",
            modifier = Modifier.weight(1f),
            color = ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = onNextMonth,
            enabled = canGoNext,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下一个月",
                tint = if (canGoNext) ink else dividerColor,
            )
        }
    }
}

/**
 * 渲染日历顶部从周日到周六的七列表头。
 */
@Composable
private fun WeekdayHeader() {
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { weekday ->
            Text(
                text = weekday,
                modifier = Modifier.weight(1f),
                color = mutedInk,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 渲染固定六行七列的日历网格，防止翻月时布局跳动。
 *
 * @param month 当前显示的月份。
 * @param schedule 全部支持日期的逐日利息结果。
 * @param today 当前系统日期，用于描边提示。
 * @param selectedDate 当前选中的日期。
 * @param onDateSelected 用户点击日期时的回调。
 */
@Composable
private fun CalendarGrid(
    month: YearMonth,
    schedule: Map<LocalDate, DailyInterestRecord>,
    today: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val calendarDates = remember(month) { createCalendarDates(month) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / 7
        val cellHeight = cellWidth / 0.92f

        // 日期格本身保持固定尺寸，不为本金气泡预留任何空间。
        Column(modifier = Modifier.fillMaxWidth()) {
            calendarDates.chunked(7).forEach { weekDates ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekDates.forEach { date ->
                        CalendarDayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            month = month,
                            record = schedule[date],
                            isToday = date == today,
                            today = today,
                            isSelected = date == selectedDate,
                            onClick = { onDateSelected(date) },
                        )
                    }
                }
            }
        }

        // 本金气泡通过网格坐标覆盖在日期格上方，不参与日期格的测量和排列。
        calendarDates.forEachIndexed { index, date ->
            val principalChange = schedule[date]?.principalChange?.takeIf {
                date.year == month.year &&
                    date.monthValue == month.monthValue &&
                    date <= today &&
                    it.signum() != 0
            }
            if (principalChange != null) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = cellWidth * (index % 7),
                            y = cellHeight * (index / 7),
                        )
                        .width(cellWidth)
                        .height(cellHeight),
                ) {
                    PrincipalChangeBubble(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-8).dp),
                        amount = principalChange,
                    )
                }
            }
        }
    }
}

/**
 * 渲染一个日期格中的日期和日息内容。
 *
 * @param modifier 用于分配七列等宽布局的修饰符。
 * @param date 当前日期格对应的日期。
 * @param month 当前显示的月份。
 * @param record 当前日期的计息记录。
 * @param isToday 当前日期格是否为系统当天。
 * @param today 当前系统日期，用于隐藏未来日期的日息。
 * @param isSelected 当前日期格是否为用户选中的日期。
 * @param onClick 点击当前日期格时的回调。
 */
@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate,
    month: YearMonth,
    record: DailyInterestRecord?,
    isToday: Boolean,
    today: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isCurrentMonth = date.year == month.year && date.monthValue == month.monthValue
    val isFutureDate = date > today
    val dailyInterest = record?.dailyInterest?.takeIf {
        isCurrentMonth && !isFutureDate && it.signum() != 0
    }
    val hasInterest = dailyInterest != null
    val dateColor = when {
        !isCurrentMonth -> dividerColor
        hasInterest -> interestRed
        else -> ink
    }
    // 选中日期使用动画颜色过渡，点击时同时保留 Compose 默认水波纹反馈。
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isCurrentMonth && hasInterest -> Color(0xFFFFCDD5)
            isSelected && isCurrentMonth -> Color(0xFFFFEDAA)
            hasInterest -> interestDayBackground
            else -> Color.Transparent
        },
        label = "日期选中背景色",
    )

    Surface(
        modifier = modifier
            .aspectRatio(0.92f)
            .padding(1.dp)
            // 波纹裁剪在日期格内，保留原有尺寸，避免窄屏日期格被最小触摸尺寸撑开。
            .clip(RoundedCornerShape(6.dp))
            .semantics { selected = isSelected && isCurrentMonth }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = ink),
                enabled = isCurrentMonth,
                onClick = onClick,
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp),
        border = when {
            isSelected && isCurrentMonth -> BorderStroke(1.dp, accentYellow)
            isToday && isCurrentMonth -> BorderStroke(1.dp, ink)
            else -> null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 1.dp, top = 2.dp, end = 1.dp, bottom = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = if (isToday && isCurrentMonth) "今日" else date.dayOfMonth.toString(),
                color = dateColor,
                fontSize = if (isToday && isCurrentMonth) 10.sp else 12.sp,
                fontWeight = if (isToday && isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
            if (dailyInterest != null) {
                Text(
                    text = InterestCalculator.formatCompactDailyInterestAmount(dailyInterest),
                    color = interestYellow,
                    fontSize = 8.sp,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 渲染悬浮在日期格顶部的本金变动聊天气泡。
 *
 * @param modifier 用于控制气泡在日期格中的位置。
 * @param amount 当日汇总的本金变动金额。
 */
@Composable
private fun PrincipalChangeBubble(
    modifier: Modifier,
    amount: BigDecimal,
) {
    val amountColor = if (amount.signum() >= 0) interestRed else withdrawalGreen

    Surface(
        modifier = modifier,
        color = Color.White,
        shape = principalChangeBubbleShape,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = InterestCalculator.formatCompactSignedAmount(amount),
            modifier = Modifier.padding(start = 4.dp, top = 1.dp, end = 4.dp, bottom = 4.dp),
            color = amountColor,
            fontSize = 7.sp,
            lineHeight = 7.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * 渲染当前月份的利息和本金变动汇总。
 *
 * @param monthTotals 当前显示月份的汇总数据。
 */
@Composable
private fun MonthTotalRow(monthTotals: MonthTotals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonthTotalMetric(
            label = "本月利息",
            value = InterestCalculator.formatSignedAmount(monthTotals.interestTotal),
            valueColor = interestYellow,
        )
        Box(modifier = Modifier.padding(end = 12.dp)) {
            MonthTotalMetric(
                label = "本月本金变动",
                value = InterestCalculator.formatSignedAmount(monthTotals.principalChangeTotal),
                valueColor = if (monthTotals.principalChangeTotal.signum() >= 0) {
                    interestRed
                } else {
                    withdrawalGreen
                },
            )
        }
    }
}

/**
 * 渲染月度汇总区域中的单个数值。
 *
 * @param label 汇总字段名称。
 * @param value 汇总字段数值。
 * @param valueColor 汇总字段数值颜色。
 */
@Composable
private fun MonthTotalMetric(
    label: String,
    value: String,
    valueColor: Color,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            color = mutedInk,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 渲染新增本金变动的弹窗，并允许继续打开日期选择器。
 *
 * @param initialDate 弹窗第一次打开时预选的日期。
 * @param onDismiss 用户取消录入时的回调。
 * @param onSave 用户提交有效日期和金额时的回调。
 */
@Composable
private fun PrincipalChangeDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (LocalDate, BigDecimal) -> Unit,
) {
    var selectedDateEpochDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var isWithdrawal by rememberSaveable { mutableStateOf(false) }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var isDatePickerVisible by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "记录本金",
                color = ink,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { isDatePickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = InterestCalculator.formatDate(selectedDate))
                }
                Spacer(modifier = Modifier.height(10.dp))
                // 通过切换收入或支出，避免用户手动输入负号。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("收入" to false, "支出" to true).forEach { (label, withdrawal) ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isWithdrawal = withdrawal },
                            color = if (isWithdrawal == withdrawal) accentYellow else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentYellow),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = ink,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.length <= 13) {
                            amountText = input
                            amountError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    isError = amountError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentYellow,
                        unfocusedBorderColor = accentYellow,
                        focusedLabelColor = interestYellow,
                        unfocusedLabelColor = interestYellow,
                    ),
                )
                if (amountError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = amountError.orEmpty(),
                        color = interestRed,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = InterestCalculator.parseAmount(amountText)
                    if (amount == null) {
                        amountError = "请输入非零的两位小数金额"
                    } else {
                        onSave(selectedDate, if (isWithdrawal) amount.abs().negate() else amount.abs())
                    }
                },
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )

    if (isDatePickerVisible) {
        DateSelectionDialog(
            selectedDate = selectedDate,
            onDismiss = { isDatePickerVisible = false },
            onDateSelected = { date ->
                selectedDateEpochDay = date.toEpochDay()
                isDatePickerVisible = false
            },
        )
    }
}

/**
 * 渲染年利率变更弹窗，保存后从指定日期开始使用新的年利率。
 *
 * @param initialDate 弹窗第一次打开时预选的日期。
 * @param initialRate 当前默认年利率小数值。
 * @param onDismiss 用户取消录入时的回调。
 * @param onSave 用户提交有效日期和百分比利率时的回调。
 */
@Composable
private fun InterestRateChangeDialog(
    initialDate: LocalDate,
    initialRate: BigDecimal,
    onDismiss: () -> Unit,
    onSave: (LocalDate, BigDecimal) -> Unit,
) {
    var selectedDateEpochDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var rateText by rememberSaveable {
        mutableStateOf(initialRate.movePointRight(2).stripTrailingZeros().toPlainString())
    }
    var rateError by rememberSaveable { mutableStateOf<String?>(null) }
    var isDatePickerVisible by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "记录利率变更", color = ink, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { isDatePickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Filled.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = InterestCalculator.formatDate(selectedDate))
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { input ->
                        if (input.length <= 8) {
                            rateText = input
                            rateError = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("年利率（百分比）") },
                    placeholder = { Text("例如 3") },
                    suffix = { Text("%") },
                    singleLine = true,
                    isError = rateError != null,
                    // 聚焦与未聚焦时均保留清晰的黄色边框和标签。
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentYellow,
                        unfocusedBorderColor = accentYellow,
                        focusedLabelColor = interestYellow,
                        unfocusedLabelColor = interestYellow,
                    ),
                )
                rateError?.let { error ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = error, color = interestRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rate = rateText.trim().toBigDecimalOrNull()
                        ?.takeIf { it.signum() >= 0 && it <= BigDecimal("100") }
                    if (rate == null) {
                        rateError = "请输入 0 到 100 之间的年利率"
                    } else {
                        onSave(selectedDate, rate.movePointLeft(2))
                    }
                },
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )

    if (isDatePickerVisible) {
        DateSelectionDialog(
            selectedDate = selectedDate,
            onDismiss = { isDatePickerVisible = false },
            onDateSelected = { date ->
                selectedDateEpochDay = date.toEpochDay()
                isDatePickerVisible = false
            },
        )
    }
}

/**
 * 渲染仅允许选择 2026 至 2029 年日期的原生日期选择弹窗。
 *
 * @param selectedDate 当前预选日期。
 * @param onDismiss 用户取消日期选择时的回调。
 * @param onDateSelected 用户确认有效日期时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionDialog(
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        yearRange = InterestCalculator.firstSupportedDate.year..InterestCalculator.lastSupportedDate.year,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        val pickedDate = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        if (InterestCalculator.isSupportedDate(pickedDate)) {
                            onDateSelected(pickedDate)
                        }
                    }
                },
            ) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * 生成固定六周的日历日期列表，使每个月的网格高度稳定。
 *
 * @param month 需要生成网格的月份。
 * @return 从周日开始的 42 个连续日期。
 */
private fun createCalendarDates(month: YearMonth): List<LocalDate> {
    val firstDate = month.atDay(1)
    val leadingDateCount = firstDate.dayOfWeek.value % 7
    return List(42) { index ->
        firstDate.minusDays(leadingDateCount.toLong()).plusDays(index.toLong())
    }
}

/**
 * 获取应用首次打开时应展示的受支持月份。
 *
 * @return 系统当前月份，超出支持范围时返回最接近的边界月份。
 */
private fun initialDisplayMonth(): YearMonth {
    return YearMonth.from(InterestCalculator.clampDate(LocalDate.now()))
}
