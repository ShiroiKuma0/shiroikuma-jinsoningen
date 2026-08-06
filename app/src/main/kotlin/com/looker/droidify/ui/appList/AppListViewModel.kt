package com.looker.droidify.ui.appList

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.looker.droidify.database.CursorOwner.Request.Available
import com.looker.droidify.database.CursorOwner.Request.Installed
import com.looker.droidify.database.CursorOwner.Request.Updates
import com.looker.droidify.database.Database
import com.looker.droidify.datastore.SettingsRepository
import com.looker.droidify.datastore.get
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.installer.InstallManager
import com.looker.droidify.installer.model.InstallState
import com.looker.droidify.model.ProductItem
import com.looker.droidify.model.ProductItem.Section.All
import com.looker.droidify.service.Connection
import com.looker.droidify.service.DownloadService
import com.looker.droidify.utility.common.extension.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@HiltViewModel
class AppListViewModel
@Inject constructor(
    settingsRepository: SettingsRepository,
    installManager: InstallManager,
) : ViewModel() {

    private val skipSignatureStream = settingsRepository
        .get { ignoreSignature }
        .asStateFlow(false)

    private val sortOrderFlow = settingsRepository
        .get { sortOrder }
        .asStateFlow(SortOrder.UPDATED)

    private val sections = MutableStateFlow<ProductItem.Section>(All)

    val state = combine(
        skipSignatureStream,
        sortOrderFlow,
        sections,
    ) { skipSignature, sortOrder, section ->
        AppListState(
            sections = section,
            sortOrder = sortOrder,
            skipSignatureCheck = skipSignature,
        )
    }.asStateFlow(AppListState())

    val reposStream = Database.RepositoryAdapter
        .getAllStream()
        .asStateFlow(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val showUpdateAllButton = skipSignatureStream.flatMapLatest { skip ->
        Database.ProductAdapter
            .getUpdatesStream(skip)
            .map { it.isNotEmpty() }
    }.asStateFlow(false)

    /** Which packages the installer currently holds, keyed the way a list row knows itself. */
    val installStates: StateFlow<Map<String, InstallState>> = installManager.state
        .map { states -> states.mapKeys { (packageName, _) -> packageName.name } }
        .asStateFlow(emptyMap())

    private val _downloadState = MutableStateFlow(DownloadService.DownloadState())
    val downloadState: StateFlow<DownloadService.DownloadState> = _downloadState.asStateFlow()

    private var downloadStateJob: Job? = null

    /**
     * The download service reports progress per read, far faster than a list can usefully repaint —
     * sampled here rather than in the fragment so every tab pays for it once.
     */
    @OptIn(FlowPreview::class)
    val downloadConnection = Connection(
        serviceClass = DownloadService::class.java,
        onBind = { _, binder ->
            downloadStateJob?.cancel()
            downloadStateJob = viewModelScope.launch {
                binder.downloadState
                    .sample(PROGRESS_SAMPLING)
                    .collect { _downloadState.value = it }
            }
        },
        onUnbind = { _, _ ->
            downloadStateJob?.cancel()
            downloadStateJob = null
            _downloadState.value = DownloadService.DownloadState()
        },
    )

    fun setSection(newSection: ProductItem.Section) {
        viewModelScope.launch {
            sections.emit(newSection)
        }
    }

    companion object {
        private const val PROGRESS_SAMPLING = 200L
    }
}

data class AppListState(
    val sections: ProductItem.Section = All,
    val sortOrder: SortOrder = SortOrder.UPDATED,
    val skipSignatureCheck: Boolean = false,
) {
    fun toRequest(source: AppListFragment.Source, searchQuery: String) = when (source) {
        AppListFragment.Source.AVAILABLE -> Available(
            searchQuery = searchQuery,
            section = sections,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )

        AppListFragment.Source.INSTALLED -> Installed(
            searchQuery = searchQuery,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )

        AppListFragment.Source.UPDATES -> Updates(
            searchQuery = searchQuery,
            order = sortOrder,
            skipSignatureCheck = skipSignatureCheck,
        )
    }
}
