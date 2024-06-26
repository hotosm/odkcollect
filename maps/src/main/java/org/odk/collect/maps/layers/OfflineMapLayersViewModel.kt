package org.odk.collect.maps.layers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.odk.collect.androidshared.system.copyToFile
import org.odk.collect.androidshared.system.getFileExtension
import org.odk.collect.androidshared.system.getFileName
import org.odk.collect.async.Scheduler
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.TempFiles
import java.io.File

class OfflineMapLayersViewModel(
    private val referenceLayerRepository: ReferenceLayerRepository,
    private val scheduler: Scheduler,
    private val settingsProvider: SettingsProvider
) : ViewModel() {
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _existingLayers = MutableLiveData<Pair<List<ReferenceLayer>, String?>>()
    val existingLayers: LiveData<Pair<List<ReferenceLayer>, String?>> = _existingLayers

    private val _layersToImport = MutableLiveData<List<ReferenceLayer>>()
    val layersToImport: LiveData<List<ReferenceLayer>> = _layersToImport

    private lateinit var tempLayersDir: File

    init {
        loadExistingLayers()
    }

    private fun loadExistingLayers() {
        _isLoading.value = true
        scheduler.immediate(
            background = {
                val layers = referenceLayerRepository.getAll()
                val selectedLayerId =
                    settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_REFERENCE_LAYER)

                _isLoading.postValue(false)
                _existingLayers.postValue(Pair(layers, selectedLayerId))
            },
            foreground = { }
        )
    }

    fun loadLayersToImport(uris: List<Uri>, context: Context) {
        _isLoading.value = true
        scheduler.immediate(
            background = {
                tempLayersDir = TempFiles.createTempDir().also {
                    it.deleteOnExit()
                }
                val layers = mutableListOf<ReferenceLayer>()
                uris.forEach { uri ->
                    if (uri.getFileExtension(context) == MbtilesFile.FILE_EXTENSION) {
                        uri.getFileName(context)?.let { fileName ->
                            val layerFile = File(tempLayersDir, fileName).also { file ->
                                uri.copyToFile(context, file)
                            }
                            layers.add(ReferenceLayer(layerFile.absolutePath, layerFile, MbtilesFile.readName(layerFile) ?: layerFile.name))
                        }
                    }
                }
                _isLoading.postValue(false)
                _layersToImport.postValue(layers)
            },
            foreground = { }
        )
    }

    fun importNewLayers(shared: Boolean) {
        _isLoading.value = true
        scheduler.immediate(
            background = {
                tempLayersDir.listFiles()?.forEach {
                    referenceLayerRepository.addLayer(it, shared)
                }
                tempLayersDir.delete()
                _isLoading.postValue(false)
            },
            foreground = {
                loadExistingLayers()
            }
        )
    }

    fun saveSelectedLayer() {
        val selectedLayerId = existingLayers.value?.second
        settingsProvider.getUnprotectedSettings().save(ProjectKeys.KEY_REFERENCE_LAYER, selectedLayerId)
    }

    fun changeSelectedLayerId(selectedLayerId: String?) {
        _existingLayers.postValue(_existingLayers.value?.copy(second = selectedLayerId))
    }
}
