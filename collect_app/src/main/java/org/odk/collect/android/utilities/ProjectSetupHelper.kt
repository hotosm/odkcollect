package org.odk.collect.android.utilities

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.mainmenu.MainMenuActivity
import org.odk.collect.android.projects.DuplicateProjectConfirmationDialog
import org.odk.collect.android.projects.ProjectCreator
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.projects.SettingsConnectionMatcher
import org.odk.collect.androidshared.ui.ToastUtils
import org.odk.collect.androidshared.ui.ToastUtils.showShortToast
import org.odk.collect.androidshared.utils.CompressionUtils
import org.odk.collect.settings.importing.SettingsImportingResult
import timber.log.Timber

/**
 * Helper class to set up a project from a deeplink URI.
 * It takes following parameters:
 * - activity: It is the AppCompatActivity instance. It will be used to start the MainMenuActivity.
 * - settingsConnectionMatcher: It is the SettingsConnectionMatcher instance. It is used to check for duplicate project
 * - projectCreator: It is the ProjectCreator instance. It is used to create a new project.
 * - projectsDataService: It is the ProjectsDataService instance. It is used to check if the project is already a current project.
 */
class ProjectSetupHelper(
    private val activity: AppCompatActivity,
    private val settingsConnectionMatcher: SettingsConnectionMatcher,
    private val projectCreator: ProjectCreator,
    private val projectsDataService: ProjectsDataService,
) :
    DuplicateProjectConfirmationDialog.DuplicateProjectConfirmationListener {

    /**
     * Method to set up a project from a deeplink URI.
     */
    fun initiateSetup(uri: Uri) {
        // Get the project settings from the URI
        // Here we are replacing the "space" with "+" because the
        // "+" char is replaced by "space" while coming through the deep link
        val projectSettingJsonStr = uri.getQueryParameter("data")?.replace(" ", "+")

        // If the URI doesn't contain a settings parameter, return
        if (projectSettingJsonStr == null) {
            showShortToast(
                activity.applicationContext,
                "Uri doesn't contain a settings data"
            )
            return
        }

        // Decompress the project settings configuration string if valid
        // It will try to decompress the settings data from the query parameter
        // If the settings data is invalid, show a toast and return
        // If the settings data is valid, create the project
        val settingsJson = try {
            CompressionUtils.decompress(projectSettingJsonStr)
        } catch (e: Exception) {
            showShortToast(
                activity.applicationContext,
                "Invalid project configuration data"
            )
            null
        } ?: return

        // Create the project
        createProjectOrError(settingsJson)
    }

    /**
     * Method to check the duplicate project and create a new project if needed.
     */
    private fun createProjectOrError(settingsJson: String) {
        settingsConnectionMatcher.getProjectWithMatchingConnection(settingsJson)?.let { uuid ->
            // Switch to project if it is not already a current project
            try {
                // Checking if project is already a current project
                val alreadyCurrentProject = projectsDataService.getCurrentProject().uuid == uuid

                // If not current project, switch to it
                if (!alreadyCurrentProject) switchToProject(uuid)

                // Else just navigate to the project
                else navigateToProject()

            } catch (e: Exception) {
                Timber.e("Error occurred while getting current project: %s", e.message)
            }

        } ?: run {
            createProject(settingsJson)
        }
    }

    /**
     * Method to create a new project.
     */
    override fun createProject(settingsJson: String) {
        when (projectCreator.createNewProject(settingsJson)) {
            SettingsImportingResult.SUCCESS -> {
                Analytics.log(AnalyticsEvents.DEEPLINK_CREATE_PROJECT)

                ActivityUtils.startActivityAndCloseAllOthers(
                    activity,
                    MainMenuActivity::class.java
                )
                ToastUtils.showLongToast(
                    activity.applicationContext,
                    activity.getString(
                        org.odk.collect.strings.R.string.switched_project,
                        projectsDataService.getCurrentProject().name
                    )
                )
            }

            SettingsImportingResult.INVALID_SETTINGS -> ToastUtils.showLongToast(
                activity.applicationContext,
                "Invalid project configuration data"
            )

            SettingsImportingResult.GD_PROJECT -> ToastUtils.showLongToast(
                activity.applicationContext,
                activity.getString(
                    org.odk.collect.strings.R.string.settings_with_gd_protocol
                )
            )
        }
    }

    /**
     * Method to switch to an existing project.
     */
    override fun switchToProject(uuid: String) {
        projectsDataService.setCurrentProject(uuid)
        ActivityUtils.startActivityAndCloseAllOthers(activity, MainMenuActivity::class.java)
        ToastUtils.showLongToast(
            activity.applicationContext,
            activity.getString(
                org.odk.collect.strings.R.string.switched_project,
                projectsDataService.getCurrentProject().name
            )
        )
    }

    /**
     * Method to handle navigation to the project.
     */
    private fun navigateToProject() {
        ActivityUtils.startActivityAndCloseAllOthers(activity, MainMenuActivity::class.java)
    }
}