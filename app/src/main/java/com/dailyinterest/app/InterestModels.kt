/**
 * 本文件定义本金变动、每日利息和汇总结果所使用的数据模型。
 */
package com.dailyinterest.app

import java.math.BigDecimal
import java.time.LocalDate

/**
 * 表示某一天新增或取出的本金记录。
 *
 * @property date 本金生效日期。
 * @property amount 本金金额，正数表示注入，负数表示取出。
 */
data class PrincipalChange(
    val date: LocalDate,
    val amount: BigDecimal,
)

/**
 * 表示从某一天开始生效的年利率变更记录。
 *
 * @property date 年利率生效日期。
 * @property annualInterestRate 从生效日期起使用的年利率小数值。
 */
data class InterestRateChange(
    val date: LocalDate,
    val annualInterestRate: BigDecimal,
)

/**
 * 表示可保存、导入和导出的完整利息配置。
 *
 * @property name 配置显示名称。
 * @property annualInterestRate 配置使用的年利率。
 * @property principalChanges 配置包含的全部本金变动记录。
 * @property interestRateChanges 配置包含的全部年利率变更记录。
 */
data class InterestConfiguration(
    val name: String,
    val annualInterestRate: BigDecimal,
    val principalChanges: List<PrincipalChange>,
    val interestRateChanges: List<InterestRateChange> = emptyList(),
)

/**
 * 表示某一自然日结束时的本金和利息计算结果。
 *
 * @property date 结果所属日期。
 * @property principalChange 当日合并后的本金变动。
 * @property closingPrincipal 加上当日变动后的本金余额。
 * @property dailyInterest 当日按照生效年利率计算的利息。
 * @property accumulatedInterest 从支持范围起累计的利息。
 */
data class DailyInterestRecord(
    val date: LocalDate,
    val principalChange: BigDecimal,
    val closingPrincipal: BigDecimal,
    val dailyInterest: BigDecimal,
    val accumulatedInterest: BigDecimal,
)

/**
 * 表示截至指定日期的总金额摘要。
 *
 * @property asOfDate 摘要的计算截止日期。
 * @property principal 截止日的本金余额。
 * @property accumulatedInterest 截止日的累计利息。
 * @property total 本金与累计利息之和。
 */
data class InterestSummary(
    val asOfDate: LocalDate,
    val principal: BigDecimal,
    val accumulatedInterest: BigDecimal,
    val total: BigDecimal,
)

/**
 * 表示某个月内显示在日历底部的汇总数据。
 *
 * @property interestTotal 该月每日利息的合计。
 * @property principalChangeTotal 该月本金变动的合计。
 */
data class MonthTotals(
    val interestTotal: BigDecimal,
    val principalChangeTotal: BigDecimal,
)
