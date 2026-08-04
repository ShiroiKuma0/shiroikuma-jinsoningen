/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the Fragment that hosts the UI page.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.ui.jinsoningen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.looker.droidify.compose.jinsoningen.JinsoningenUiScreen
import com.looker.droidify.compose.theme.DroidifyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app is still mid-migration from Fragments to Compose, so — exactly like upstream's own
 * `SettingsFragment` — the UI page is a Compose screen inside a `ComposeView`.
 */
@AndroidEntryPoint
class JinsoningenUiFragment : Fragment() {

    companion object {
        fun newInstance() = JinsoningenUiFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DroidifyTheme {
                    JinsoningenUiScreen(
                        onBackClick = { activity?.onBackPressedDispatcher?.onBackPressed() },
                    )
                }
            }
        }
    }
}
