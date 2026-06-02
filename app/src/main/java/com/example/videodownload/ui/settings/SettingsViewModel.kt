package com.example.videodownload.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videodownload.data.SettingsDataStore
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * yt-dlp 更新状态
 */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Updating : UpdateState()
    data class Success(val version: String) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

/**
 * 设置页 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val saveLocation = settingsDataStore.saveLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val preferredQuality = settingsDataStore.preferredQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.QUALITY_BEST)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

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

    /**
     * 手动更新 yt-dlp
     */
    fun updateYoutubeDl() {
        if (_updateState.value is UpdateState.Updating) return
        _updateState.value = UpdateState.Updating

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        getApplication(),
                        YoutubeDL.UpdateChannel.STABLE
                    )
                }
                Log.d("SettingsViewModel", "yt-dlp updated, status: $result")
                _updateState.value = UpdateState.Success("更新成功 (状态: $result)")
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "yt-dlp update failed", e)
                _updateState.value = UpdateState.Error(e.message ?: "未知错误")
            }
        }
    }
}
