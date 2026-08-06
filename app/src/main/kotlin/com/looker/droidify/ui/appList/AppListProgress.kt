package com.looker.droidify.ui.appList

import com.looker.droidify.installer.model.InstallState
import com.looker.droidify.network.DataSize
import com.looker.droidify.service.DownloadService

/**
 * What a row is currently doing. Deliberately narrower than the app detail screen's `Status`:
 * a list row reports work in progress and nothing else — an error or a finished install is the
 * notification's business, and the row simply goes quiet again.
 */
sealed interface ItemStatus {
    data object Queued : ItemStatus
    data object Connecting : ItemStatus
    data class Downloading(val read: DataSize, val total: DataSize?) : ItemStatus
    data object PendingInstall : ItemStatus
    data object Installing : ItemStatus
}

/**
 * The download queue and the install queue, seen as one thing.
 *
 * [awaitingInstall] is the seam between them. A finished download leaves
 * [DownloadService.State.Success] standing as the current item while the file waits for
 * [com.looker.droidify.installer.InstallManager] to pick it up, and during an "update all" that
 * wait lasts as long as the install ahead of it — the row would otherwise look idle at exactly
 * the moment it is between two queues. The set is fed by the fragment, which drops a package the
 * moment the installer reports any state of its own, so a failed install (whose entry the session
 * receiver removes again) cannot leave a row stuck on "queued" forever.
 */
data class AppListProgress(
    val download: DownloadService.DownloadState = DownloadService.DownloadState(),
    val installs: Map<String, InstallState> = emptyMap(),
    val awaitingInstall: Set<String> = emptySet(),
) {

    infix fun statusOf(packageName: String): ItemStatus? {
        if (packageName.isEmpty()) return null
        val current = download.currentItem
        if (current.packageName == packageName) {
            when (current) {
                is DownloadService.State.Connecting -> return ItemStatus.Connecting
                is DownloadService.State.Downloading -> return ItemStatus.Downloading(
                    read = current.read,
                    total = current.total,
                )

                else -> Unit
            }
        }
        when (installs[packageName]) {
            InstallState.Installing -> return ItemStatus.Installing
            InstallState.Pending -> return ItemStatus.PendingInstall
            // Installed and Failed both stay in the map after the fact; they are not work.
            else -> Unit
        }
        if (packageName in awaitingInstall) return ItemStatus.PendingInstall
        return if (packageName in download.queue) ItemStatus.Queued else null
    }

    /** True while anything at all is queued, downloading or installing. Drives the button. */
    val isWorking: Boolean
        get() = download.currentItem is DownloadService.State.Connecting ||
            download.currentItem is DownloadService.State.Downloading ||
            // The queue carries an empty-string sentinel across the download/install hand-off.
            download.queue.any { it.isNotEmpty() } ||
            awaitingInstall.isNotEmpty() ||
            installs.values.any {
                it == InstallState.Pending || it == InstallState.Installing
            }
}
