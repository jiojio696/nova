package com.example.data.repository

import android.net.Uri
import android.text.Html
import com.example.BuildConfig
import com.example.data.api.GoogleCustomSearchApi
import com.example.data.api.OpenAiApi
import com.example.data.api.OpenAiChatRequest
import com.example.data.api.OpenAiMessage
import com.example.data.api.StructuredNovaAnswerDto
import com.example.data.api.WikimediaSearchApi
import com.example.data.models.AnswerLanguage
import com.example.data.models.SearchResultItem
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class SearchOutcome(
    val results: List<SearchResultItem>,
    val providerLabel: String,
    val durationMs: Long,
    val warning: String? = null
)

sealed class AiAnswerOutcome {
    data class Success(
        val shortAnswer: String,
        val fullExplanation: String,
        val insufficientInfo: Boolean,
        val durationMs: Long
    ) : AiAnswerOutcome()

    data class MissingApiKey(val message: String) : AiAnswerOutcome()
    data class Failure(val message: String, val durationMs: Long) : AiAnswerOutcome()
}

data class ApiConfigStatus(
    val openAiConfigured: Boolean,
    val googleCustomSearchConfigured: Boolean
)

class NovaApiRepository(private val moshi: Moshi) {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "NovaAI-Android/1.0")
                .build()
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }
        .build()

    private val googleSearchApi: GoogleCustomSearchApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleCustomSearchApi::class.java)

    private val wikiIdApi: WikimediaSearchApi = Retrofit.Builder()
        .baseUrl("https://id.wikipedia.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikimediaSearchApi::class.java)

    private val wikiEnApi: WikimediaSearchApi = Retrofit.Builder()
        .baseUrl("https://en.wikipedia.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WikimediaSearchApi::class.java)

    private val openAiApi: OpenAiApi = Retrofit.Builder()
        .baseUrl("https://api.groq.com/openai/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenAiApi::class.java)

    private val structuredAdapter = moshi.adapter(StructuredNovaAnswerDto::class.java)

    fun getConfigStatus(): ApiConfigStatus = ApiConfigStatus(
        openAiConfigured = isRealKey(BuildConfig.OPENAI_API_KEY),
        googleCustomSearchConfigured = isRealKey(BuildConfig.GOOGLE_SEARCH_API_KEY) &&
            isRealKey(BuildConfig.GOOGLE_SEARCH_CX)
    )

    private fun isRealKey(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isNotEmpty() &&
            !trimmed.startsWith("YOUR_") &&
            !trimmed.startsWith("MY_") &&
            !trimmed.equals("null", ignoreCase = true)
    }

    /**
     * Queries Google Programmable Search Engine (Custom Search JSON API) when configured.
     * Falls back cleanly to official Wikimedia Search API (Indonesian + English) when CSE credentials
     * are not configured, avoiding any unauthorized HTML scraping.
     */
    suspend fun searchWeb(question: String, language: AnswerLanguage): SearchOutcome =
        withContext(Dispatchers.IO) {
            val startNs = System.nanoTime()
            val cleanedQuery = buildSearchQuery(question)
            val cseKey = BuildConfig.GOOGLE_SEARCH_API_KEY.trim()
            val cseCx = BuildConfig.GOOGLE_SEARCH_CX.trim()

            if (isRealKey(cseKey) && isRealKey(cseCx)) {
                try {
                    val response = googleSearchApi.search(
                        apiKey = cseKey,
                        searchEngineId = cseCx,
                        query = cleanedQuery,
                        num = 5,
                        hostLang = language.code
                    )
                    if (response.isSuccessful) {
                        val items = response.body()?.items.orEmpty().mapNotNull { item ->
                            val url = item.link?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            SearchResultItem(
                                title = item.title?.trim().orEmpty().ifBlank { url },
                                snippet = item.snippet?.trim().orEmpty(),
                                url = url,
                                displayDomain = item.displayLink?.trim().orEmpty()
                                    .ifBlank { extractDomain(url) }
                            )
                        }
                        val ms = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                        if (items.isNotEmpty()) {
                            return@withContext SearchOutcome(
                                results = items,
                                providerLabel = "Google Search API",
                                durationMs = ms
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to official Wikimedia API provider
                }
            }

            // Secondary official Search API provider (Wikimedia Search API — no HTML scraping)
            val wikiResults = mutableListOf<SearchResultItem>()
            try {
                val primaryWiki = if (language == AnswerLanguage.INDONESIAN) wikiIdApi else wikiEnApi
                val primaryHost = if (language == AnswerLanguage.INDONESIAN) "id.wikipedia.org" else "en.wikipedia.org"
                val primaryResp = primaryWiki.search(query = cleanedQuery, limit = 4)
                if (primaryResp.isSuccessful) {
                    primaryResp.body()?.query?.search.orEmpty().forEach { hit ->
                        val encodedTitle = Uri.encode(hit.title.replace(" ", "_"))
                        wikiResults.add(
                            SearchResultItem(
                                title = hit.title,
                                snippet = stripHtml(hit.snippet),
                                url = "https://$primaryHost/wiki/$encodedTitle",
                                displayDomain = primaryHost
                            )
                        )
                    }
                }

                if (wikiResults.size < 2) {
                    val secondaryWiki = if (language == AnswerLanguage.INDONESIAN) wikiEnApi else wikiIdApi
                    val secondaryHost = if (language == AnswerLanguage.INDONESIAN) "en.wikipedia.org" else "id.wikipedia.org"
                    val secondaryResp = secondaryWiki.search(query = cleanedQuery, limit = 3)
                    if (secondaryResp.isSuccessful) {
                        secondaryResp.body()?.query?.search.orEmpty().forEach { hit ->
                            val encodedTitle = Uri.encode(hit.title.replace(" ", "_"))
                            wikiResults.add(
                                SearchResultItem(
                                    title = hit.title,
                                    snippet = stripHtml(hit.snippet),
                                    url = "https://$secondaryHost/wiki/$encodedTitle",
                                    displayDomain = secondaryHost
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Network may be offline; return empty results with warning
            }

            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            SearchOutcome(
                results = wikiResults.take(5),
                providerLabel = if (isRealKey(cseKey) && isRealKey(cseCx)) {
                    "Wikimedia API (Fallback)"
                } else {
                    "Wikimedia Search API"
                },
                durationMs = durationMs,
                warning = if (wikiResults.isEmpty()) {
                    "Tidak ditemukan hasil pencarian langsung untuk kueri ini."
                } else null
            )
        }

    /**
     * Synthesizes a grounded answer via Groq OpenAI-compatible Chat Completions API
     * using BuildConfig.OPENAI_API_KEY (containing the user's Groq API key).
     * Never fabricates mock AI responses when OPENAI_API_KEY is absent.
     */
    suspend fun generateAnswer(
        question: String,
        sources: List<SearchResultItem>,
        language: AnswerLanguage
    ): AiAnswerOutcome = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY.trim()
        if (!isRealKey(apiKey)) {
            return@withContext AiAnswerOutcome.MissingApiKey(
                "OPENAI_API_KEY (Groq) belum dikonfigurasi. Tambahkan API key Groq Anda ke variabel OPENAI_API_KEY melalui panel Secrets di AI Studio."
            )
        }

        val startNs = System.nanoTime()
        val langInstruction = when (language) {
            AnswerLanguage.INDONESIAN -> "Jawab dalam Bahasa Indonesia yang jelas, akurat, dan ringkas."
            AnswerLanguage.ENGLISH -> "Answer in clear, accurate, and concise English."
        }

        val sourcesBlock = if (sources.isEmpty()) {
            "Tidak ada snippet pencarian eksternal yang tersedia."
        } else {
            sources.mapIndexed { idx, item ->
                "[${idx + 1}] ${item.title} (${item.displayDomain})\nSnippet: ${item.snippet}\nURL: ${item.url}"
            }.joinToString("\n\n")
        }

        val systemPrompt = """
            Kamu adalah NOVA AI Answer Engine untuk Android Floating Assistant.
            Tugasmu:
            1. Pahami pertanyaan pengguna hasil OCR layar (perbaiki typo ringan OCR secara implisit).
            2. Analisis hasil pencarian yang diberikan, saring informasi tidak relevan, dan susun jawaban yang akurat.
            3. Jika informasi pencarian tidak cukup atau tidak pasti, akui dengan jujur dan set "insufficient_info": true.
            4. $langInstruction
            5. WAJIB kembalikan HANYA JSON valid dengan struktur persis:
            {
              "short_answer": "Jawaban langsung dan ringkas dalam 1-2 kalimat.",
              "full_explanation": "Penjelasan lengkap, langkah/konteks, dan rujukan poin sumber [1], [2] jika ada.",
              "insufficient_info": false
            }
        """.trimIndent()

        val userPrompt = """
            Pertanyaan:
            $question

            Hasil Pencarian:
            $sourcesBlock
        """.trimIndent()

        try {
            val response = openAiApi.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = OpenAiChatRequest(
                    model = "openai/gpt-oss-120b",
                    messages = listOf(
                        OpenAiMessage(role = "system", content = systemPrompt),
                        OpenAiMessage(role = "user", content = userPrompt)
                    ),
                    temperature = 0.2,
                    maxTokens = 1024
                )
            )
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

            if (!response.isSuccessful) {
                val code = response.code()
                val msg = when (code) {
                    401 -> "OPENAI_API_KEY (Groq) tidak valid atau kedaluwarsa (HTTP 401)."
                    429 -> "Batas kuota/rate limit Groq tercapai (HTTP 429). Coba beberapa saat lagi."
                    else -> "Permintaan Groq API gagal (HTTP $code)."
                }
                return@withContext AiAnswerOutcome.Failure(msg, durationMs)
            }

            val rawContent = response.body()?.choices?.firstOrNull()?.message?.content?.trim()
            if (rawContent.isNullOrBlank()) {
                return@withContext AiAnswerOutcome.Failure(
                    "Groq API tidak mengembalikan isi jawaban.",
                    durationMs
                )
            }

            val jsonCandidate = extractJsonObject(rawContent)
            val parsed = runCatching { structuredAdapter.fromJson(jsonCandidate) }.getOrNull()
            val shortAns = parsed?.shortAnswer?.trim().takeIf { !it.isNullOrBlank() }
                ?: rawContent.lineSequence().firstOrNull()?.take(240).orEmpty()
            val fullExp = parsed?.fullExplanation?.trim().takeIf { !it.isNullOrBlank() }
                ?: rawContent

            AiAnswerOutcome.Success(
                shortAnswer = shortAns,
                fullExplanation = fullExp,
                insufficientInfo = parsed?.insufficientInfo ?: false,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            AiAnswerOutcome.Failure(
                "Gagal terhubung ke Groq API: ${e.localizedMessage ?: "Periksa koneksi internet Anda."}",
                durationMs
            )
        }
    }

    private fun buildSearchQuery(question: String): String {
        return question
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
    }

    private fun extractDomain(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault(url)
    }

    private fun stripHtml(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    private fun extractJsonObject(raw: String): String {
        val cleaned = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace > firstBrace) {
            cleaned.substring(firstBrace, lastBrace + 1)
        } else {
            cleaned
        }
    }
}
