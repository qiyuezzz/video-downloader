package com.example.videodownload.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.data.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val saveLocation = settingsDataStore.saveLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferredQuality = settingsDataStore.preferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_BEST)

    /**
     * 更新保存目录
     */
    fun setSaveLocation(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setSaveLocation(uri)
        }
    }

    /**
     * 更新首选画质
     */
    fun setPreferredQuality(quality: String) {
        viewModelScope.launch {
            settingsDataStore.setPreferredQuality(quality)
        }
    }
}
