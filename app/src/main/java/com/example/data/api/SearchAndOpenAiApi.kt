package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// ---------- Google Programmable Search (Custom Search JSON API) ----------

@JsonClass(generateAdapter = true)
data class GoogleSearchResponse(
    @Json(name = "items") val items: List<GoogleSearchItem>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleSearchItem(
    @Json(name = "title") val title: String? = null,
    @Json(name = "snippet") val snippet: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "displayLink") val displayLink: String? = null
)

interface GoogleCustomSearchApi {
    @GET("customsearch/v1")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,
        @Query("num") num: Int = 5,
        @Query("hl") hostLang: String = "id"
    ): Response<GoogleSearchResponse>
}

// ---------- Wikimedia Official Search API (Zero-key official API fallback) ----------

@JsonClass(generateAdapter = true)
data class WikiSearchResponse(
    @Json(name = "query") val query: WikiSearchQuery? = null
)

@JsonClass(generateAdapter = true)
data class WikiSearchQuery(
    @Json(name = "search") val search: List<WikiSearchHit>? = null
)

@JsonClass(generateAdapter = true)
data class WikiSearchHit(
    @Json(name = "pageid") val pageId: Long = 0L,
    @Json(name = "title") val title: String = "",
    @Json(name = "snippet") val snippet: String = ""
)

interface WikimediaSearchApi {
    @GET("w/api.php")
    suspend fun search(
        @Query("action") action: String = "query",
        @Query("list") list: String = "search",
        @Query("srsearch") query: String,
        @Query("srlimit") limit: Int = 4,
        @Query("utf8") utf8: Int = 1,
        @Query("format") format: String = "json"
    ): Response<WikiSearchResponse>
}

// ---------- OpenAI Chat Completions API ----------

@JsonClass(generateAdapter = true)
data class OpenAiChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenAiMessage>,
    @Json(name = "temperature") val temperature: Double = 0.2,
    @Json(name = "max_tokens") val maxTokens: Int = 700,
    @Json(name = "response_format") val responseFormat: OpenAiResponseFormat? = OpenAiResponseFormat("json_object")
)

@JsonClass(generateAdapter = true)
data class OpenAiResponseFormat(
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class OpenAiMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAiChatResponse(
    @Json(name = "choices") val choices: List<OpenAiChoice>? = null
)

@JsonClass(generateAdapter = true)
data class OpenAiChoice(
    @Json(name = "message") val message: OpenAiMessage? = null
)

@JsonClass(generateAdapter = true)
data class StructuredNovaAnswerDto(
    @Json(name = "short_answer") val shortAnswer: String? = null,
    @Json(name = "full_explanation") val fullExplanation: String? = null,
    @Json(name = "insufficient_info") val insufficientInfo: Boolean? = null
)

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenAiChatRequest
    ): Response<OpenAiChatResponse>
}
