package org.odk.collect.android.injection.config

import org.odk.collect.android.entities.EntitiesRepositoryProvider
import org.odk.collect.android.formmanagement.FormSourceProvider
import org.odk.collect.android.projects.ProjectDependencyFactory
import org.odk.collect.android.projects.ProjectDependencyModule
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.android.utilities.SavepointsRepositoryProvider
import org.odk.collect.settings.SettingsProvider

class ProjectDependencyModuleFactory(
    private val settingsProvider: SettingsProvider,
    private val formsRepositoryProvider: FormsRepositoryProvider,
    private val instancesRepositoryProvider: InstancesRepositoryProvider,
    private val storagePathProvider: StoragePathProvider,
    private val changeLockProvider: ChangeLockProvider,
    private val formSourceProvider: FormSourceProvider,
    private val savepointsRepositoryProvider: SavepointsRepositoryProvider,
    private val entitiesRepositoryProvider: EntitiesRepositoryProvider,
) : ProjectDependencyFactory<ProjectDependencyModule> {
    override fun create(projectId: String): ProjectDependencyModule {
        return ProjectDependencyModule(
            projectId,
            ProjectDependencyFactory.from { settingsProvider.getUnprotectedSettings(projectId) },
            ProjectDependencyFactory.from { formsRepositoryProvider.get(projectId) },
            ProjectDependencyFactory.from { instancesRepositoryProvider.get(projectId) },
            storagePathProvider,
            changeLockProvider,
            ProjectDependencyFactory.from { formSourceProvider.get(projectId) },
            ProjectDependencyFactory.from { savepointsRepositoryProvider.get(projectId) },
            ProjectDependencyFactory.from { entitiesRepositoryProvider.get(projectId) }
        )
    }
}
