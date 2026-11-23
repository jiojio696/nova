package com.example.data.repository

import com.example.data.database.ScanHistoryDao
import com.example.data.database.ScanHistoryEntity
import com.example.data.models.NovaAnswerResult
import com.example.data.models.PipelineTimings
import com.example.data.models.ScanSourceType
import com.example.data.models.SearchResultItem
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@JsonClass(generateAdapter = true)
data class PersistedSourceDto(
    val title: String,
    val snippet: String,
    val url: String,
    val displayDomain: String
)

class HistoryRepository(
    private val dao: ScanHistoryDao,
    private val moshi: Moshi
) {
    private val sourceListType = Types.newParameterizedType(List::class.java, PersistedSourceDto::class.java)
    private val sourceAdapter = moshi.adapter<List<PersistedSourceDto>>(sourceListType)

    val allHistory: Flow<List<NovaAnswerResult>> = dao.observeAllHistory().map { entities ->
        entities.map { entity -> entity.toDomain() }
    }

    suspend fun saveResult(result: NovaAnswerResult): Long {
        val dtos = result.sources.map {
            PersistedSourceDto(it.title, it.snippet, it.url, it.displayDomain)
        }
        val entity = ScanHistoryEntity(
            question = result.question,
            shortAnswer = result.shortAnswer,
            fullExplanation = result.fullExplanation,
            sourcesJson = sourceAdapter.toJson(dtos),
            searchProviderLabel = result.searchProviderLabel,
            sourceType = result.sourceType.name,
            insufficientInfo = result.insufficientInfo,
            cropMs = result.timings.cropMs,
            ocrMs = result.timings.ocrMs,
            searchMs = result.timings.searchMs,
            aiMs = result.timings.aiMs,
            timestamp = result.timestamp
        )
        return dao.insert(entity)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    private fun ScanHistoryEntity.toDomain(): NovaAnswerResult {
        val parsedSources = runCatching {
            sourceAdapter.fromJson(sourcesJson).orEmpty().map {
                SearchResultItem(it.title, it.snippet, it.url, it.displayDomain)
            }
        }.getOrDefault(emptyList())

        val sourceEnum = ScanSourceType.entries.firstOrNull { it.name == sourceType }
            ?: ScanSourceType.SCAN_AREA

        return NovaAnswerResult(
            id = id,
            question = question,
            shortAnswer = shortAnswer,
            fullExplanation = fullExplanation,
            sources = parsedSources,
            searchProviderLabel = searchProviderLabel,
            sourceType = sourceEnum,
            insufficientInfo = insufficientInfo,
            timings = PipelineTimings(
                cropMs = cropMs,
                ocrMs = ocrMs,
                searchMs = searchMs,
                aiMs = aiMs
            ),
            timestamp = timestamp
        )
    }
}
