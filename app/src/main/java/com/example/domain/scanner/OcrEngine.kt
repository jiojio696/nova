package com.example.domain.scanner

import android.graphics.Bitmap
import com.example.data.models.OcrEvaluation
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class OcrResult {
    data class Success(val evaluation: OcrEvaluation) : OcrResult()
    data class Unreadable(val reason: String, val durationMs: Long) : OcrResult()
}

class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Recognizes Indonesian & English text using Google ML Kit Text Recognition,
     * preserving top-to-bottom block/line order, removing stray noise, and reporting
     * genuine ML Kit confidence metrics (never synthetic confidence values).
     */
    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val startNs = System.nanoTime()
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizeTask(inputImage)
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)

            val orderedBlocks = visionText.textBlocks.sortedWith(
                compareBy<Text.TextBlock> { it.boundingBox?.top ?: Int.MAX_VALUE }
                    .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
            )

            val cleanedLines = mutableListOf<String>()
            val confidences = mutableListOf<Float>()
            var totalWords = 0

            for (block in orderedBlocks) {
                val orderedLines = block.lines.sortedWith(
                    compareBy<Text.Line> { it.boundingBox?.top ?: Int.MAX_VALUE }
                        .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE }
                )
                for (line in orderedLines) {
                    val lineText = cleanNoise(line.text)
                    if (lineText.isNotBlank()) {
                        cleanedLines.add(lineText)
                        val elements = line.elements
                        totalWords += elements.size
                        for (el in elements) {
                            val conf = el.confidence
                            if (conf != null && conf in 0f..1f) {
                                confidences.add(conf)
                            }
                        }
                    }
                }
            }

            val assembledQuestion = reconstructQuestion(cleanedLines)
            val alphanumericCount = assembledQuestion.count { it.isLetterOrDigit() }

            if (assembledQuestion.isBlank() || alphanumericCount < 3) {
                return@withContext OcrResult.Unreadable(
                    reason = "Teks tidak terdeteksi atau terlalu buram pada area pilihan. Silakan pindai ulang dengan kotak lebih jelas.",
                    durationMs = durationMs
                )
            }

            val avgConfidence = if (confidences.isNotEmpty()) {
                confidences.average().toFloat()
            } else {
                null
            }

            if (avgConfidence != null && avgConfidence < 0.35f && alphanumericCount < 10) {
                return@withContext OcrResult.Unreadable(
                    reason = "Kejelasan teks terlalu rendah (${(avgConfidence * 100).toInt()}%). Mohon pindai ulang area pertanyaan.",
                    durationMs = durationMs
                )
            }

            OcrResult.Success(
                OcrEvaluation(
                    rawText = visionText.text.trim(),
                    cleanedQuestion = assembledQuestion,
                    blockCount = orderedBlocks.size,
                    lineCount = cleanedLines.size,
                    wordCount = totalWords.coerceAtLeast(assembledQuestion.split(Regex("\\s+")).size),
                    averageConfidence = avgConfidence,
                    ocrDurationMs = durationMs
                )
            )
        } catch (e: Exception) {
            val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            OcrResult.Unreadable(
                reason = "Gagal membaca gambar melalui ML Kit OCR: ${e.localizedMessage ?: "Kesalahan tidak diketahui"}.",
                durationMs = durationMs
            )
        }
    }

    private suspend fun recognizeTask(inputImage: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }
                .addOnFailureListener { error ->
                    if (cont.isActive) cont.resumeWithException(error)
                }
        }

    private fun cleanNoise(line: String): String {
        val trimmed = line.replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "").trim()
        // Ignore isolated single-symbol UI artifacts (e.g., "|", "•", "×")
        if (trimmed.length == 1 && !trimmed[0].isLetterOrDigit()) return ""
        return trimmed.replace(Regex("\\s{2,}"), " ")
    }

    private fun reconstructQuestion(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        val builder = StringBuilder()
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (index == 0) {
                builder.append(line)
            } else {
                val prevEndsHyphen = builder.endsWith("-")
                val startsChoiceOption = Regex("^([A-Ea-e][.)]|\\d+[.)]|[-•])\\s+.*").matches(line)
                when {
                    prevEndsHyphen -> {
                        builder.deleteCharAt(builder.length - 1)
                        builder.append(line)
                    }
                    startsChoiceOption -> {
                        builder.append("\n").append(line)
                    }
                    else -> {
                        builder.append(" ").append(line)
                    }
                }
            }
        }
        return builder.toString().trim()
    }
}
