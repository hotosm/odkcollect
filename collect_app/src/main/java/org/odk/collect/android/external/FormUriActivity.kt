package org.odk.collect.android.external

import android.content.ContentResolver
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.R
import org.odk.collect.android.activities.FormFillingActivity
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.instancemanagement.InstanceDeleter
import org.odk.collect.android.instancemanagement.canBeEdited
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.utilities.ApplicationConstants
import org.odk.collect.android.utilities.ContentUriHelper
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.async.Scheduler
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.strings.R.string
import java.io.File
import javax.inject.Inject

/**
 * This class serves as a firewall for starting form filling. It should be used to do that
 * rather than [FormFillingActivity] directly as it ensures that the required data is valid.
 */
class FormUriActivity : ComponentActivity() {

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    @Inject
    lateinit var projectsRepository: ProjectsRepository

    @Inject
    lateinit var formsRepositoryProvider: FormsRepositoryProvider

    @Inject
    lateinit var instanceRepositoryProvider: InstancesRepositoryProvider

    @Inject
    lateinit var settingsProvider: SettingsProvider

    @Inject
    lateinit var scheduler: Scheduler

    private var formFillingAlreadyStarted = false

    private val openForm =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setResult(it.resultCode, it.data)
            finish()
        }

    private val formUriViewModel by viewModels<FormUriViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return FormUriViewModel(
                    intent.data,
                    scheduler,
                    projectsRepository,
                    projectsDataService,
                    contentResolver,
                    formsRepositoryProvider,
                    instanceRepositoryProvider,
                    resources
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerUtils.getComponent(this).inject(this)
        setContentView(R.layout.circular_progress_indicator)

        formUriViewModel.formUriValidationResult.observe(this) {
            when (it) {
                is Valid -> {
                    if (savedInstanceState?.getBoolean(FORM_FILLING_ALREADY_STARTED) != true) {
                        startForm(it.uri)
                    }
                }

                is Invalid -> {
                    displayErrorDialog(it.message)
                }
            }
        }
    }

    private fun startForm(uri: Uri) {
        formFillingAlreadyStarted = true
        openForm.launch(
            Intent(this, FormFillingActivity::class.java).apply {
                action = intent.action
                data = uri
                intent.extras?.let { sourceExtras -> putExtras(sourceExtras) }
                if (!canFormBeEdited(uri)) {
                    putExtra(
                        ApplicationConstants.BundleKeys.FORM_MODE,
                        ApplicationConstants.FormModes.VIEW_SENT
                    )
                }
            }
        )
    }

    private fun displayErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    private fun canFormBeEdited(uri: Uri): Boolean {
        val uriMimeType = contentResolver.getType(uri)

        val formEditingEnabled = if (uriMimeType == InstancesContract.CONTENT_ITEM_TYPE) {
            val instance = instanceRepositoryProvider.get().get(ContentUriHelper.getIdFromUri(uri))
            instance!!.canBeEdited(settingsProvider)
        } else {
            true
        }

        return formEditingEnabled
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(FORM_FILLING_ALREADY_STARTED, formFillingAlreadyStarted)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val FORM_FILLING_ALREADY_STARTED = "FORM_FILLING_ALREADY_STARTED"
    }
}

private class FormUriViewModel(
    private val uri: Uri?,
    scheduler: Scheduler,
    private val projectsRepository: ProjectsRepository,
    private val projectsDataService: ProjectsDataService,
    private val contentResolver: ContentResolver,
    private val formsRepositoryProvider: FormsRepositoryProvider,
    private val instancesRepositoryProvider: InstancesRepositoryProvider,
    private val resources: Resources
) : ViewModel() {


    // This is just for handling and reassigning uri
    // When the FormUriActivity is started via a browsable link in
    // the format "odkcollect://form/<form_id>"
    private var _uri: Uri? = uri

    private val _formUriValidationResult = MutableLiveData<FormUriValidationResult>()
    val formUriValidationResult: LiveData<FormUriValidationResult> = _formUriValidationResult

    init {
        scheduler.immediate(
            background = {
                // If from the browsable link in the format "odkcollect://form/<form_id>",
                // We will modify the uri to make it valid for the FormFillingActivity
                if (uri?.scheme == "odkcollect" && uri.host == "form") {

                    // Get the form id from the uri
                    val formId = ContentUriHelper.getIdFromUri(uri)

                    // If the form id is not valid i.e. -1, return with error message
                    if (formId == -1L) {
                        return@immediate "${resources.getString(string.wrong_project_selected_for_form)} And conform that it is downloaded."
                    }

                    // Getting currently active project id
                    val currentProjectId = try {
                        projectsDataService.getCurrentProject().uuid
                    } catch (e: Exception) {
                        null
                    }

                    // Else build the new uri
                    // We will modify the uri to make it valid for the FormFillingActivity
                    val tempUri = FormsContract.getUri(null)
                    val builder = Uri.Builder()
                        .scheme(tempUri.scheme)
                        .authority(tempUri.authority)
                        .appendPath(tempUri.lastPathSegment)
                        .appendPath(ContentUriHelper.getIdFromUri(uri).toString())

                    // If projectId is not present in the uri, add it
                    if (!uri.queryParameterNames.contains("projectId")) {
                        builder.appendQueryParameter("projectId", currentProjectId)
                    }

                    // add all the query parameters from the uri
                    uri.queryParameterNames?.forEach { key ->
                        builder.appendQueryParameter(key, uri.getQueryParameter(key))
                    }

                    // Build the new uri
                    _uri = builder.build()
                }

                assertProjectListNotEmpty() ?: assertCurrentProjectUsed() ?: assertValidUri()
                ?: assertFormExists() ?: assertFormNotEncrypted()
            },
            foreground = {
                _formUriValidationResult.value = if (it == null) {
                    Valid(_uri!!)
                } else {
                    Invalid(it)
                }
            }
        )
    }

    private fun assertProjectListNotEmpty(): String? {
        val projects = try {
            projectsRepository.getAll()
        } catch (e: Exception) {
            emptyList()
        }
        return if (projects.isEmpty()) {
            resources.getString(string.app_not_configured)
        } else {
            null
        }
    }

    private fun assertCurrentProjectUsed(): String? {
        val uriProjectId = _uri?.getQueryParameter("projectId")
        val projectId = if (uriProjectId != null) uriProjectId else {
            val projects = projectsRepository.getAll()
            val firstProject = projects.first()
            firstProject.uuid
        }

        val currentProjectId = try {
            projectsDataService.getCurrentProject().uuid
        } catch (e: Exception) {
            null
        }

        return if (projectId != currentProjectId) {
            resources.getString(string.wrong_project_selected_for_form)
        } else {
            null
        }
    }

    private fun assertValidUri(): String? {
        val isUriValid = _uri?.let {
            val uriMimeType = contentResolver.getType(it)
            if (uriMimeType == null) {
                false
            } else {
                uriMimeType == FormsContract.CONTENT_ITEM_TYPE || uriMimeType == InstancesContract.CONTENT_ITEM_TYPE
            }
        } ?: false

        return if (!isUriValid) {
            resources.getString(string.unrecognized_uri)
        } else {
            null
        }
    }

    private fun assertFormExists(): String? {
        val uriMimeType = contentResolver.getType(_uri!!)

        return if (uriMimeType == FormsContract.CONTENT_ITEM_TYPE) {
            val formExists =
                formsRepositoryProvider.get().get(ContentUriHelper.getIdFromUri(_uri!!))
                    ?.let {
                        File(it.formFilePath).exists()
                    } ?: false

            if (formExists) {
                null
            } else {
                resources.getString(string.bad_uri)
            }
        } else {
            val instance =
                instancesRepositoryProvider.get().get(ContentUriHelper.getIdFromUri(_uri!!))
            if (instance == null) {
                resources.getString(string.bad_uri)
            } else if (!File(instance.instanceFilePath).exists()) {
                Analytics.log(AnalyticsEvents.OPEN_DELETED_INSTANCE)
                InstanceDeleter(
                    instancesRepositoryProvider.get(),
                    formsRepositoryProvider.get()
                ).delete(instance.dbId)
                resources.getString(string.instance_deleted_message)
            } else {
                val candidateForms = formsRepositoryProvider.get()
                    .getAllByFormIdAndVersion(instance.formId, instance.formVersion)

                if (candidateForms.isEmpty()) {
                    val version = if (instance.formVersion == null) {
                        ""
                    } else {
                        "\n${resources.getString(string.version)} ${instance.formVersion}"
                    }

                    resources.getString(
                        string.parent_form_not_present,
                        "${instance.formId}$version"
                    )
                } else if (candidateForms.filter { !it.isDeleted }.size > 1) {
                    resources.getString(string.survey_multiple_forms_error)
                } else {
                    null
                }
            }
        }
    }

    private fun assertFormNotEncrypted(): String? {
        val uriMimeType = contentResolver.getType(_uri!!)

        return if (uriMimeType == InstancesContract.CONTENT_ITEM_TYPE) {
            val instance =
                instancesRepositoryProvider.get().get(ContentUriHelper.getIdFromUri(_uri!!))
            if (instance!!.canEditWhenComplete()) {
                null
            } else {
                resources.getString(string.encrypted_form)
            }
        } else {
            null
        }
    }
}

/**
 * Represents the result of validating a form URI.
 *It can either be a [Valid] containing the valid URI,
 * or an [Invalid] with an error message.
 */
private sealed class FormUriValidationResult

/**
 * Represents a successfully validated form URI.
 * @property uri The valid URI.
 */
private data class Valid(val uri: Uri) : FormUriValidationResult()

/**
 * Represents an invalid form URI with an associated error message.
 * @property message The error message explaining why the URI is invalid.
 */
private data class Invalid(val message: String) : FormUriValidationResult()
