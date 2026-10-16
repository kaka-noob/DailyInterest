/**
 * 本文件负责在固定日期范围内计算每天的本金、利息和金额展示格式。
 */
package com.dailyinterest.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 集中处理配置年利率下的逐日计息规则。
 */
object InterestCalculator {

    /** 新建配置使用的默认年利率，3 个点即 3%。 */
    val defaultAnnualInterestRate: BigDecimal = BigDecimal("0.03")

    /** 日历和数据记录允许使用的最早日期。 */
    val firstSupportedDate: LocalDate = LocalDate.of(2026, 1, 1)

    /** 日历和数据记录允许使用的最晚日期。 */
    val lastSupportedDate: LocalDate = LocalDate.of(2029, 12, 31)

    /** 日历允许翻到的首月。 */
    val firstSupportedMonth: YearMonth = YearMonth.from(firstSupportedDate)

    /** 日历允许翻到的末月。 */
    val lastSupportedMonth: YearMonth = YearMonth.from(lastSupportedDate)

    /** 金额小数位数。 */
    private const val MONEY_SCALE = 2

    /** 内部累计日息时保留的小数位数，避免每日舍入放大年化误差。 */
    private const val INTEREST_CALCULATION_SCALE = 10

    /** 用于日历紧凑金额显示的万元单位。 */
    private val tenThousand: BigDecimal = BigDecimal("10000")

    /** 用于日历紧凑金额显示的亿元单位。 */
    private val hundredMillion: BigDecimal = BigDecimal("100000000")

    /** 用于校验输入金额格式的正则表达式。 */
    private val moneyPattern: Regex = Regex("[+-]?(\\d+)(\\.\\d{1,2})?")

    /** 用于中文日期展示的格式化器。 */
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    /**
     * 为全部支持日期生成按日计息结果。
     *
     * 当日的本金变动会立即参与当日利息计算，利息会以高精度累计，并在展示时四舍五入。
     *
     * @param principalChanges 用户保存的所有本金变动。
     * @param annualInterestRate 当前配置使用的年利率。
     * @param interestRateChanges 按生效日期排列的年利率变更记录。
     * @return 以日期为键的逐日计算结果。
     */
    fun createSchedule(
        principalChanges: List<PrincipalChange>,
        annualInterestRate: BigDecimal,
        interestRateChanges: List<InterestRateChange> = emptyList(),
    ): Map<LocalDate, DailyInterestRecord> {
        val changesByDate = principalChanges
            .filter { isSupportedDate(it.date) }
            .groupBy { it.date }
            .mapValues { (_, changes) -> sumAmounts(changes) }
        val schedule = linkedMapOf<LocalDate, DailyInterestRecord>()
        var currentDate = firstSupportedDate
        var closingPrincipal = BigDecimal.ZERO.setScale(MONEY_SCALE)
        var accumulatedInterest = BigDecimal.ZERO.setScale(MONEY_SCALE)
        val rateChangesByDate = interestRateChanges
            .filter { isSupportedDate(it.date) && it.annualInterestRate.signum() >= 0 }
            .sortedBy { it.date }
            .groupBy { it.date }
            .mapValues { (_, changes) -> changes.last().annualInterestRate }
        var currentAnnualInterestRate = annualInterestRate

        while (!currentDate.isAfter(lastSupportedDate)) {
            rateChangesByDate[currentDate]?.let { currentAnnualInterestRate = it }
            val principalChange = changesByDate[currentDate] ?: BigDecimal.ZERO.setScale(MONEY_SCALE)
            closingPrincipal = closingPrincipal
                .add(principalChange)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            val dailyInterest = calculateDailyInterest(
                principal = closingPrincipal,
                year = currentDate.year,
                annualInterestRate = currentAnnualInterestRate,
            )
            accumulatedInterest = accumulatedInterest
                .add(dailyInterest)
                .setScale(INTEREST_CALCULATION_SCALE, RoundingMode.HALF_UP)
            schedule[currentDate] = DailyInterestRecord(
                date = currentDate,
                principalChange = principalChange,
                closingPrincipal = closingPrincipal,
                dailyInterest = dailyInterest,
                accumulatedInterest = accumulatedInterest,
            )
            currentDate = currentDate.plusDays(1)
        }

        return schedule
    }

    /**
     * 计算截至指定日期应展示的总金额。
     *
     * @param schedule 已生成的逐日计息结果。
     * @param date 希望查看的日期，超出范围时会自动限制到支持范围。
     * @return 指定日期的本金、累计利息和总金额。
     */
    fun calculateSummary(
        schedule: Map<LocalDate, DailyInterestRecord>,
        date: LocalDate,
    ): InterestSummary {
        val asOfDate = clampDate(date)
        val record = schedule.getValue(asOfDate)
        return InterestSummary(
            asOfDate = asOfDate,
            principal = record.closingPrincipal,
            accumulatedInterest = record.accumulatedInterest,
            total = record.closingPrincipal.add(record.accumulatedInterest),
        )
    }

    /**
     * 获取指定日期实际生效的年利率。
     *
     * @param date 需要查询的日期。
     * @param annualInterestRate 初始年利率。
     * @param interestRateChanges 年利率变更记录。
     * @return 指定日期生效的年利率。
     */
    fun currentAnnualInterestRate(
        date: LocalDate,
        annualInterestRate: BigDecimal,
        interestRateChanges: List<InterestRateChange>,
    ): BigDecimal {
        return interestRateChanges
            .asReversed()
            .filter { isSupportedDate(it.date) && it.date <= clampDate(date) && it.annualInterestRate.signum() >= 0 }
            .maxByOrNull { it.date }
            ?.annualInterestRate
            ?: annualInterestRate
    }

    /**
     * 计算某个月需要展示的利息和本金变动合计。
     *
     * @param schedule 已生成的逐日计息结果。
     * @param month 需要统计的月份。
     * @return 该月的利息与本金变动合计。
     */
    fun calculateMonthTotals(
        schedule: Map<LocalDate, DailyInterestRecord>,
        month: YearMonth,
    ): MonthTotals {
        val firstDate = maxOf(month.atDay(1), firstSupportedDate)
        val lastDate = minOf(month.atEndOfMonth(), lastSupportedDate)
        var interestTotal = BigDecimal.ZERO.setScale(MONEY_SCALE)
        var principalChangeTotal = BigDecimal.ZERO.setScale(MONEY_SCALE)
        var currentDate = firstDate

        while (!currentDate.isAfter(lastDate)) {
            schedule[currentDate]?.let { record ->
                interestTotal = interestTotal.add(record.dailyInterest)
                principalChangeTotal = principalChangeTotal.add(record.principalChange)
            }
            currentDate = currentDate.plusDays(1)
        }

        return MonthTotals(
            interestTotal = interestTotal,
            principalChangeTotal = principalChangeTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        )
    }

    /**
     * 判断指定日期是否在应用支持的四年范围中。
     *
     * @param date 需要判断的日期。
     * @return 日期可用于记录和日历时返回 true。
     */
    fun isSupportedDate(date: LocalDate): Boolean {
        return !date.isBefore(firstSupportedDate) && !date.isAfter(lastSupportedDate)
    }

    /**
     * 将任意日期限制在应用支持的四年范围中。
     *
     * @param date 原始日期。
     * @return 可用于计算和展示的日期。
     */
    fun clampDate(date: LocalDate): LocalDate {
        return date.coerceIn(firstSupportedDate, lastSupportedDate)
    }

    /**
     * 解析用户输入的本金金额。
     *
     * @param rawAmount 输入框中的原始文本。
     * @return 合法且非零的两位小数金额，不合法时返回 null。
     */
    fun parseAmount(rawAmount: String): BigDecimal? {
        val normalizedInput = rawAmount.trim()
        if (!moneyPattern.matches(normalizedInput)) {
            return null
        }

        return normalizedInput
            .toBigDecimalOrNull()
            ?.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            ?.takeIf { it.signum() != 0 }
    }

    /**
     * 将日期格式化为中文完整日期。
     *
     * @param date 需要展示的日期。
     * @return 例如“2026年8月15日”的日期文本。
     */
    fun formatDate(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    /**
     * 将配置年利率格式化为界面展示文本。
     *
     * @param annualInterestRate 当前配置使用的年利率。
     * @return 例如“3.00%”的年利率文本。
     */
    fun formatAnnualInterestRate(annualInterestRate: BigDecimal): String {
        return annualInterestRate
            .movePointRight(2)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            .toPlainString()
            .plus("%")
    }

    /**
     * 将金额格式化为总金额使用的带千分位文本。
     *
     * @param amount 需要展示的金额。
     * @return 带千分位且最多两位小数的金额文本。
     */
    fun formatTotalAmount(amount: BigDecimal): String {
        return formatGroupedAmount(amount)
    }

    /**
     * 将金额格式化为始终含有正负号的带千分位文本。
     *
     * @param amount 需要展示的金额。
     * @return 例如“+1,000”或“-88.2”的金额文本。
     */
    fun formatSignedAmount(amount: BigDecimal): String {
        val normalizedAmount = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        val sign = if (normalizedAmount.signum() >= 0) "+" else "-"
        return "$sign${formatGroupedAmount(normalizedAmount.abs())}"
    }

    /**
     * 将金额格式化为适合窄日历单元格的始终带正负号文本。
     *
     * @param amount 需要展示的金额。
     * @return 金额较大时使用万元单位，避免在日历格内溢出。
     */
    fun formatCompactSignedAmount(amount: BigDecimal): String {
        return formatCompactAmount(amount, MONEY_SCALE)
    }

    /**
     * 将日息格式化为适合窄日历单元格的最多两位小数正负号文本。
     *
     * @param amount 需要展示的日息金额。
     * @return 最多保留两位小数的日息文本，金额较大时使用万元或亿元单位。
     */
    fun formatCompactDailyInterestAmount(amount: BigDecimal): String {
        return formatCompactAmount(amount, MONEY_SCALE)
    }

    /**
     * 根据给定小数位数将金额压缩为适合日历单元格的正负号文本。
     *
     * @param amount 需要展示的金额。
     * @param displayScale 小数显示精度。
     * @return 金额较大时使用万元或亿元单位的紧凑文本。
     */
    private fun formatCompactAmount(amount: BigDecimal, displayScale: Int): String {
        val normalizedAmount = amount.setScale(displayScale, RoundingMode.HALF_UP)
        val sign = if (normalizedAmount.signum() >= 0) "+" else "-"
        val absoluteAmount = normalizedAmount.abs()
        if (absoluteAmount >= hundredMillion) {
            val compactAmount = absoluteAmount
                .divide(hundredMillion, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            return "$sign${compactAmount}亿"
        }
        if (absoluteAmount >= tenThousand) {
            val compactAmount = absoluteAmount
                .divide(tenThousand, 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            return "$sign${compactAmount}万"
        }

        return "$sign${absoluteAmount.stripTrailingZeros().toPlainString()}"
    }

    /**
     * 汇总同一天的多笔本金金额。
     *
     * @param changes 同一天的本金记录。
     * @return 四舍五入到分的本金合计。
     */
    private fun sumAmounts(changes: List<PrincipalChange>): BigDecimal {
        return changes.fold(BigDecimal.ZERO) { total, change ->
            total.add(change.amount)
        }.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * 计算指定本金在某一年中的单日利息。
     *
     * @param principal 当日计息本金。
     * @param year 所属年份，用于区分平年和闰年天数。
     * @param annualInterestRate 当前配置使用的年利率。
     * @return 按内部累计精度四舍五入后的日利息。
     */
    private fun calculateDailyInterest(
        principal: BigDecimal,
        year: Int,
        annualInterestRate: BigDecimal,
    ): BigDecimal {
        return principal
            .multiply(annualInterestRate)
            .divide(
                BigDecimal.valueOf(daysInYear(year).toLong()),
                INTEREST_CALCULATION_SCALE,
                RoundingMode.HALF_UP,
            )
    }

    /**
     * 获取指定年份的实际天数。
     *
     * @param year 需要判断的年份。
     * @return 闰年返回 366，平年返回 365。
     */
    private fun daysInYear(year: Int): Int {
        return if (Year.isLeap(year.toLong())) 366 else 365
    }

    /**
     * 按最多两位小数和千分位格式化绝对金额。
     *
     * @param amount 需要格式化的非负金额。
     * @return 带千分位且最多两位小数的金额文本。
     */
    private fun formatGroupedAmount(amount: BigDecimal): String {
        return DecimalFormat("#,##0.##").format(amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
    }
}
