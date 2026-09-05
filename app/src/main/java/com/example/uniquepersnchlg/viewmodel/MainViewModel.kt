package com.example.uniquepersnchlg.viewmodel



import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uniquepersnchlg.data.ProcessingState
import com.example.uniquepersnchlg.pipeline.VideoProcessor


import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val processor = VideoProcessor(application.applicationContext)

    val state: StateFlow<ProcessingState> = processor.state

    var selectedVideoUri: Uri? = null
        private set
    var selectedVideoLabel: String = ""
        private set

    fun onVideoSelected(uri: Uri, label: String) {
        selectedVideoUri = uri
        selectedVideoLabel = label
        processor.reset()
    }

    fun startProcessing() {
        val uri = selectedVideoUri ?: return
        viewModelScope.launch {
            processor.process(uri, selectedVideoLabel)
        }
    }

    fun reset() = processor.reset()

    override fun onCleared() {
        super.onCleared()
        processor.close()
    }
}
