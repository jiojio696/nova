package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.models.AnswerLanguage
import com.example.data.models.NovaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nova_display_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val BUBBLE_SIZE_DP = intPreferencesKey("bubble_size_dp")
        val CONFIRM_OCR = booleanPreferencesKey("confirm_ocr_before_search")
        val DEFAULT_EXPANDED = booleanPreferencesKey("default_expanded_panel")
        val ANSWER_LANGUAGE = stringPreferencesKey("answer_language")
        val SAVE_HISTORY = booleanPreferencesKey("save_history_locally")
    }

    val settingsFlow: Flow<NovaSettings> = context.novaSettingsDataStore.data.map { prefs ->
        val langCode = prefs[Keys.ANSWER_LANGUAGE] ?: AnswerLanguage.INDONESIAN.code
        val language = AnswerLanguage.entries.firstOrNull { it.code == langCode }
            ?: AnswerLanguage.INDONESIAN

        NovaSettings(
            bubbleOpacity = (prefs[Keys.BUBBLE_OPACITY] ?: 0.92f).coerceIn(0.45f, 1.0f),
            bubbleSizeDp = (prefs[Keys.BUBBLE_SIZE_DP] ?: 58).coerceIn(48, 72),
            confirmOcrBeforeSearch = prefs[Keys.CONFIRM_OCR] ?: true,
            defaultExpandedPanel = prefs[Keys.DEFAULT_EXPANDED] ?: false,
            answerLanguage = language,
            saveHistoryLocally = prefs[Keys.SAVE_HISTORY] ?: true
        )
    }

    suspend fun updateBubbleOpacity(value: Float) {
        context.novaSettingsDataStore.edit { it[Keys.BUBBLE_OPACITY] = value.coerceIn(0.45f, 1.0f) }
    }

    suspend fun updateBubbleSizeDp(sizeDp: Int) {
        context.novaSettingsDataStore.edit { it[Keys.BUBBLE_SIZE_DP] = sizeDp.coerceIn(48, 72) }
    }

    suspend fun updateConfirmOcrBeforeSearch(enabled: Boolean) {
        context.novaSettingsDataStore.edit { it[Keys.CONFIRM_OCR] = enabled }
    }

    suspend fun updateDefaultExpandedPanel(expanded: Boolean) {
        context.novaSettingsDataStore.edit { it[Keys.DEFAULT_EXPANDED] = expanded }
    }

    suspend fun updateAnswerLanguage(language: AnswerLanguage) {
        context.novaSettingsDataStore.edit { it[Keys.ANSWER_LANGUAGE] = language.code }
    }

    suspend fun updateSaveHistoryLocally(enabled: Boolean) {
        context.novaSettingsDataStore.edit { it[Keys.SAVE_HISTORY] = enabled }
    }
}
