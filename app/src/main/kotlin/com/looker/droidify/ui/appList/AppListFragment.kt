package com.looker.droidify.ui.appList

import android.database.Cursor
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.looker.droidify.database.CursorOwner
import com.looker.droidify.databinding.RecyclerViewWithFabBinding
import com.looker.droidify.installer.model.InstallState
import com.looker.droidify.model.ProductItem
import com.looker.droidify.service.DownloadService
import com.looker.droidify.ui.tabsFragment.TabsFragment
import com.looker.droidify.utility.common.Scroller
import com.looker.droidify.utility.common.extension.dp
import com.looker.droidify.utility.common.extension.systemBarsMargin
import com.looker.droidify.utility.common.extension.systemBarsPadding
import com.looker.droidify.utility.extension.mainActivity
import com.looker.droidify.utility.getParcelableCompat
import com.looker.droidify.widget.RecyclerFastScroller
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.looker.droidify.R.string as stringRes

@AndroidEntryPoint
class AppListFragment() : Fragment(), CursorOwner.Callback {
    companion object {
        private const val STATE_LAYOUT_MANAGER = "layoutManager"

        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_SEARCH_QUERY = "search_query"

        /** How long the button holds its working state on nothing but the tap. */
        private const val UPDATE_ALL_HOLD = 8_000L
    }

    enum class Source(
        val titleResId: Int,
        val sections: Boolean,
        val updateAll: Boolean,
    ) {
        AVAILABLE(stringRes.available, true, false),
        INSTALLED(stringRes.installed, false, false),
        UPDATES(stringRes.updates, false, true),
    }

    constructor(source: Source) : this() {
        arguments = Bundle().apply {
            putString(EXTRA_SOURCE, source.name)
        }
    }

    val source by lazy { Source.valueOf(requireArguments().getString(EXTRA_SOURCE)!!) }

    private val viewModel: AppListViewModel by viewModels()

    private var _binding: RecyclerViewWithFabBinding? = null
    private val binding get() = _binding!!

    private lateinit var recyclerView: RecyclerView
    private lateinit var appListAdapter: AppListAdapter
    private var scroller: Scroller? = null
    private var shortAnimationDuration: Int = 0
    private var layoutManagerState: Parcelable? = null

    private var searchQuery: String = ""

    private var progress = AppListProgress()

    /** Packages whose file is downloaded and whose install the installer has not claimed yet. */
    private val awaitingInstall = mutableSetOf<String>()

    private var updateAllRequested = false
    private var updateAllRequestJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = RecyclerViewWithFabBinding.inflate(inflater, container, false)

        shortAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        searchQuery = savedInstanceState?.getString(EXTRA_SEARCH_QUERY).orEmpty()

        val viewModel = viewModel
        viewModel.downloadConnection.bind(requireContext())

        recyclerView = binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            isMotionEventSplittingEnabled = false
            setHasFixedSize(true)
            recycledViewPool.setMaxRecycledViews(AppListAdapter.ViewType.PRODUCT.ordinal, 30)
            appListAdapter = AppListAdapter(source, mainActivity::navigateProduct)
            adapter = appListAdapter
            systemBarsPadding()
            RecyclerFastScroller(this)
        }
        with(binding.updateAll) {
            if (source.updateAll) {
                setOnClickListener { startUpdateAll() }
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.showUpdateAllButton.collect {
                        isVisible = it
                    }
                }
                systemBarsMargin(16.dp)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        layoutManagerState = savedInstanceState?.getParcelableCompat(STATE_LAYOUT_MANAGER)
        mainActivity.cursorOwner.attach(
            callback = this,
            request = viewModel.state.value.toRequest(source, searchQuery),
        )
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reposStream.collect { repos ->
                        appListAdapter.repositories = repos.associateBy { it.id }
                    }
                }
                launch {
                    viewModel.state.collect {
                        mainActivity.cursorOwner.attach(
                            callback = this@AppListFragment,
                            request = it.toRequest(source, searchQuery),
                        )
                    }
                }
                launch {
                    viewModel.downloadState.collect(::onDownloadState)
                }
                launch {
                    viewModel.installStates.collect(::onInstallStates)
                }
            }
        }
    }

    private fun startUpdateAll() {
        (parentFragment as? TabsFragment)?.updateAll()
        // Reading the settings and the database takes a moment before the first download reports
        // in. The button says so immediately, and lets go on its own if nothing picks the work up.
        updateAllRequested = true
        updateFabState()
        updateAllRequestJob?.cancel()
        updateAllRequestJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(UPDATE_ALL_HOLD)
            updateAllRequested = false
            updateFabState()
        }
    }

    private fun onDownloadState(state: DownloadService.DownloadState) {
        if (state.currentItem is DownloadService.State.Success) {
            awaitingInstall += state.currentItem.packageName
        }
        applyProgress(progress.copy(download = state, awaitingInstall = awaitingInstall.toSet()))
    }

    private fun onInstallStates(states: Map<String, InstallState>) {
        // Once the installer holds a package, its own state is what the row should show — and a
        // failed install, whose entry the receiver drops again, no longer leaves the row hanging.
        awaitingInstall.removeAll(states.keys)
        applyProgress(progress.copy(installs = states, awaitingInstall = awaitingInstall.toSet()))
    }

    private fun applyProgress(newProgress: AppListProgress) {
        progress = newProgress
        appListAdapter.progress = newProgress
        if (newProgress.isWorking) {
            updateAllRequested = false
            updateAllRequestJob?.cancel()
            updateAllRequestJob = null
        }
        updateFabState()
    }

    private fun updateFabState() {
        if (!source.updateAll) return
        val fab = _binding?.updateAll ?: return
        val working = updateAllRequested || progress.isWorking
        fab.setText(if (working) stringRes.updating_all else stringRes.update_all)
        // A second tap would re-queue everything mid-flight.
        fab.isEnabled = !working
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val managerState = layoutManagerState ?: recyclerView.layoutManager?.onSaveInstanceState()
        if (managerState != null) {
            outState.putParcelable(STATE_LAYOUT_MANAGER, managerState)
        }
        outState.putString(EXTRA_SEARCH_QUERY, searchQuery)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateAllRequestJob?.cancel()
        updateAllRequestJob = null
        viewModel.downloadConnection.unbind(requireContext())
        _binding = null
        scroller = null
        mainActivity.cursorOwner.detach(this)
    }

    override fun onCursorData(request: CursorOwner.Request, cursor: Cursor?) {
        appListAdapter.cursor = cursor
        appListAdapter.emptyText = when {
            cursor == null -> ""
            searchQuery.isNotEmpty() -> {
                getString(stringRes.no_matching_applications_found)
            }

            else -> when (source) {
                Source.AVAILABLE -> getString(stringRes.no_applications_available)
                Source.INSTALLED -> getString(stringRes.no_applications_installed)
                Source.UPDATES -> getString(stringRes.all_applications_up_to_date)
            }
        }
        layoutManagerState?.let {
            layoutManagerState = null
            recyclerView.layoutManager?.onRestoreInstanceState(it)
        }
    }

    fun setSearchQuery(newSearchQuery: String) {
        if (view != null) {
            searchQuery = newSearchQuery
            mainActivity.cursorOwner.attach(
                callback = this,
                request = viewModel.state.value.toRequest(source, searchQuery),
            )
        }
    }

    fun setSection(section: ProductItem.Section) {
        viewModel.setSection(section)
    }
}
