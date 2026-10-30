package app.investwatch.data.db

import app.investwatch.core.Criterion
import app.investwatch.core.CriterionState
import app.investwatch.core.DividendPayment
import app.investwatch.core.DividendYear
import app.investwatch.core.EventMetric
import app.investwatch.core.Source
import app.investwatch.core.StatusMetric
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Вложенные списки хранятся в базе как JSON. Модели ядра намеренно без
 * аннотаций сериализации, поэтому здесь свои маленькие формы для записи.
 */
object Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CriterionDto(
        val code: String,
        val state: String,
        val note: String = "",
        val fromOverride: Boolean = false,
    )

    @Serializable
    private data class NamedValueDto(val name: String, val value: String)

    @Serializable
    private data class YearDto(val year: Int, val total: Double)

    @Serializable
    private data class PaymentDto(
        val recordDate: String,
        val amount: Double,
        val yieldPct: Double? = null,
    )

    @Serializable
    private data class SourceDto(val title: String, val url: String)

    fun criteriaToJson(items: List<Criterion>): String = json.encodeToString(
        ListSerializer(CriterionDto.serializer()),
        items.map { CriterionDto(it.code, it.state.raw, it.note, it.fromOverride) },
    )

    fun criteriaFromJson(text: String?): List<Criterion> = decode(text) {
        json.decodeFromString(ListSerializer(CriterionDto.serializer()), it)
            .map { dto -> Criterion(dto.code, CriterionState.from(dto.state), dto.note, dto.fromOverride) }
    }

    fun statusMetricsToJson(items: List<StatusMetric>): String = json.encodeToString(
        ListSerializer(NamedValueDto.serializer()),
        items.map { NamedValueDto(it.name, it.value) },
    )

    fun statusMetricsFromJson(text: String?): List<StatusMetric> = decode(text) {
        json.decodeFromString(ListSerializer(NamedValueDto.serializer()), it)
            .map { dto -> StatusMetric(dto.name, dto.value) }
    }

    fun eventMetricsToJson(items: List<EventMetric>): String = json.encodeToString(
        ListSerializer(NamedValueDto.serializer()),
        items.map { NamedValueDto(it.name, it.value) },
    )

    fun eventMetricsFromJson(text: String?): List<EventMetric> = decode(text) {
        json.decodeFromString(ListSerializer(NamedValueDto.serializer()), it)
            .map { dto -> EventMetric(dto.name, dto.value) }
    }

    fun sourcesToJson(items: List<Source>): String = json.encodeToString(
        ListSerializer(SourceDto.serializer()),
        items.map { SourceDto(it.title, it.url) },
    )

    fun sourcesFromJson(text: String?): List<Source> = decode(text) {
        json.decodeFromString(ListSerializer(SourceDto.serializer()), it)
            .map { dto -> Source(dto.title, dto.url) }
    }

    fun yearsToJson(items: List<DividendYear>): String = json.encodeToString(
        ListSerializer(YearDto.serializer()),
        items.map { YearDto(it.year, it.total) },
    )

    fun yearsFromJson(text: String?): List<DividendYear> = decode(text) {
        json.decodeFromString(ListSerializer(YearDto.serializer()), it)
            .map { dto -> DividendYear(dto.year, dto.total) }
    }

    fun paymentsToJson(items: List<DividendPayment>): String = json.encodeToString(
        ListSerializer(PaymentDto.serializer()),
        items.map { PaymentDto(it.recordDate, it.amount, it.yieldPct) },
    )

    fun paymentsFromJson(text: String?): List<DividendPayment> = decode(text) {
        json.decodeFromString(ListSerializer(PaymentDto.serializer()), it)
            .map { dto -> DividendPayment(dto.recordDate, dto.amount, dto.yieldPct) }
    }

    fun stringsToJson(items: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), items)

    fun stringsFromJson(text: String?): List<String> = decode(text) {
        json.decodeFromString(ListSerializer(String.serializer()), it)
    }

    /** Повреждённое значение в базе не должно ронять экран. */
    private fun <T> decode(text: String?, block: (String) -> List<T>): List<T> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching { block(text) }.getOrDefault(emptyList())
    }
}
