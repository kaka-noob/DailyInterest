/**
 * 本文件使用应用私有 JSON 文件保存完整利息配置，并提供 JSON 导入导出支持。
 */
package com.dailyinterest.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 提供利息配置文件的本地读取、保存和 JSON 编解码能力，不依赖网络或数据库服务。
 *
 * @param context 用于获取应用私有文件目录与旧版迁移数据的上下文。
 */
class PrincipalChangeStore(context: Context) {

    /** 应用私有目录中的当前配置文件。 */
    private val configurationFile = File(
        context.applicationContext.filesDir,
        CONFIGURATION_FILE_NAME,
    )

    /** 仅用于首次升级时迁移旧版 SharedPreferences 数据。 */
    private val legacyPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * 读取当前保存的利息配置。
     *
     * 首次使用时会创建默认配置文件；旧版本仅保存的本金记录会被迁移到该文件。
     *
     * @return 可用于主页计算的完整利息配置。
     */
    fun loadConfiguration(): InterestConfiguration {
        readConfigurationFile()?.let { return it }

        val initialConfiguration = InterestConfiguration(
            name = DEFAULT_CONFIGURATION_NAME,
            annualInterestRate = InterestCalculator.defaultAnnualInterestRate,
            principalChanges = loadLegacyChanges(),
            interestRateChanges = emptyList(),
        )
        saveConfiguration(initialConfiguration)
        return initialConfiguration
    }

    /**
     * 保存完整利息配置到应用私有 JSON 文件。
     *
     * @param configuration 需要持久化的配置数据。
     */
    fun saveConfiguration(configuration: InterestConfiguration) {
        configurationFile.writeText(serializeConfiguration(configuration), Charsets.UTF_8)
    }

    /**
     * 从应用私有 JSON 文件读取有效的完整配置。
     *
     * @return 文件存在且格式正确时返回配置，否则返回 null。
     */
    private fun readConfigurationFile(): InterestConfiguration? {
        return runCatching {
            configurationFile.readText(Charsets.UTF_8)
        }.getOrNull()?.let(::parseConfiguration)
    }

    /**
     * 将利息配置编码为可导出的 JSON 文本。
     *
     * @param configuration 需要导出的完整配置。
     * @return 带缩进的 JSON 文本。
     */
    fun serializeConfiguration(configuration: InterestConfiguration): String {
        return JSONObject().apply {
            put(KEY_VERSION, CONFIGURATION_VERSION)
            put(KEY_NAME, configuration.name)
            put(KEY_ANNUAL_INTEREST_RATE, configuration.annualInterestRate.toPlainString())
            put(KEY_PRINCIPAL_CHANGES, encodeChanges(configuration.principalChanges))
            put(KEY_INTEREST_RATE_CHANGES, encodeInterestRateChanges(configuration.interestRateChanges))
        }.toString(JSON_INDENT_SPACES)
    }

    /**
     * 解析用户选择的 JSON 文件内容。
     *
     * @param serializedConfiguration 导入文件中的原始 JSON 文本。
     * @return 内容有效时返回完整配置，格式错误时返回 null。
     */
    fun parseConfiguration(serializedConfiguration: String): InterestConfiguration? {
        return runCatching {
            val savedConfiguration = JSONObject(serializedConfiguration)
            require(savedConfiguration.optInt(KEY_VERSION, 0) == CONFIGURATION_VERSION)
            val annualInterestRate = savedConfiguration
                .optString(KEY_ANNUAL_INTEREST_RATE)
                .toBigDecimalOrNull()
                ?.takeIf { it.signum() >= 0 }
                ?: error("年利率无效")
            val savedItems = savedConfiguration.optJSONArray(KEY_PRINCIPAL_CHANGES)
                ?: error("本金记录缺失")
            InterestConfiguration(
                name = savedConfiguration
                    .optString(KEY_NAME, DEFAULT_CONFIGURATION_NAME)
                    .trim()
                    .ifBlank { DEFAULT_CONFIGURATION_NAME }
                    .take(MAX_CONFIGURATION_NAME_LENGTH),
                annualInterestRate = annualInterestRate,
                principalChanges = decodeChanges(savedItems),
                interestRateChanges = decodeInterestRateChanges(
                    savedConfiguration.optJSONArray(KEY_INTEREST_RATE_CHANGES) ?: JSONArray(),
                ),
            )
        }.getOrNull()
    }

    /**
     * 将本金记录转换为 JSON 数组。
     *
     * @param changes 需要保存或导出的本金记录。
     * @return 可嵌入配置 JSON 的记录数组。
     */
    private fun encodeChanges(changes: List<PrincipalChange>): JSONArray {
        return JSONArray().apply {
            changes.forEach { change ->
                put(
                    JSONObject().apply {
                        put(KEY_DATE, change.date.toString())
                        put(KEY_AMOUNT, change.amount.toPlainString())
                    },
                )
            }
        }
    }

    /**
     * 将 JSON 数组转换为有效且按日期排序的本金记录。
     *
     * @param savedItems 配置 JSON 中的本金记录数组。
     * @return 可用于计息的本金记录列表。
     */
    private fun decodeChanges(savedItems: JSONArray): List<PrincipalChange> {
        return buildList {
            repeat(savedItems.length()) { index ->
                decodeChange(savedItems.optJSONObject(index))?.let(::add)
            }
        }.sortedBy { it.date }
    }

    /** 将年利率变更记录转换为 JSON 数组。 */
    private fun encodeInterestRateChanges(changes: List<InterestRateChange>): JSONArray {
        return JSONArray().apply {
            changes.forEach { change ->
                put(JSONObject().apply {
                    put(KEY_DATE, change.date.toString())
                    put(KEY_ANNUAL_INTEREST_RATE, change.annualInterestRate.toPlainString())
                })
            }
        }
    }

    /** 将 JSON 数组转换为有效且按日期排序的年利率变更记录。 */
    private fun decodeInterestRateChanges(savedItems: JSONArray): List<InterestRateChange> {
        return buildList {
            repeat(savedItems.length()) { index ->
                val savedItem = savedItems.optJSONObject(index) ?: return@repeat
                val date = runCatching { java.time.LocalDate.parse(savedItem.optString(KEY_DATE)) }.getOrNull()
                val rate = savedItem.optString(KEY_ANNUAL_INTEREST_RATE).toBigDecimalOrNull()
                if (date != null && rate != null && rate.signum() >= 0 && InterestCalculator.isSupportedDate(date)) {
                    add(InterestRateChange(date, rate))
                }
            }
        }.sortedBy { it.date }
    }

    /**
     * 读取旧版本保存的本金记录，避免升级后丢失本地数据。
     *
     * @return 旧记录转换后的本金变动列表。
     */
    private fun loadLegacyChanges(): List<PrincipalChange> {
        return runCatching {
            decodeChanges(JSONArray(legacyPreferences.getString(KEY_LEGACY_CHANGES, "[]") ?: "[]"))
        }.getOrElse { emptyList() }
    }

    /**
     * 将单条 JSON 数据转换成有效的本金变动模型。
     *
     * @param savedItem 本地存储中的单条 JSON 数据。
     * @return 数据合法时返回本金变动，否则返回 null。
     */
    private fun decodeChange(savedItem: JSONObject?): PrincipalChange? {
        val dateParts = savedItem?.optString(KEY_DATE)?.split("-") ?: return null
        val date = runCatching {
            require(dateParts.size == 3)
            java.time.LocalDate.of(
                dateParts[0].toInt(),
                dateParts[1].toInt(),
                dateParts[2].toInt(),
            )
        }.getOrNull() ?: return null
        val amount = savedItem
            ?.optString(KEY_AMOUNT)
            ?.toBigDecimalOrNull()
            ?.takeIf { it.signum() != 0 }
            ?: return null
        if (!InterestCalculator.isSupportedDate(date)) {
            return null
        }

        return PrincipalChange(date = date, amount = amount)
    }

    /**
     * 保存本地配置时使用的文件、旧版迁移偏好和 JSON 字段名称。
     */
    private companion object {
        const val PREFERENCES_NAME = "daily_interest_preferences"
        const val CONFIGURATION_FILE_NAME = "daily_interest_configuration.json"
        const val KEY_LEGACY_CHANGES = "principal_changes"
        const val KEY_VERSION = "version"
        const val KEY_NAME = "name"
        const val KEY_ANNUAL_INTEREST_RATE = "annualRate"
        const val KEY_PRINCIPAL_CHANGES = "principalChanges"
        const val KEY_INTEREST_RATE_CHANGES = "interestRateChanges"
        const val KEY_DATE = "date"
        const val KEY_AMOUNT = "amount"
        const val CONFIGURATION_VERSION = 1
        const val DEFAULT_CONFIGURATION_NAME = "每日利息"
        const val MAX_CONFIGURATION_NAME_LENGTH = 40
        const val JSON_INDENT_SPACES = 2
    }
}
