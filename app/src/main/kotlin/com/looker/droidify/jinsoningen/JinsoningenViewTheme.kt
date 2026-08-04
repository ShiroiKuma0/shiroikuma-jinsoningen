/*
 * 白い熊 人造人間 (shiroikuma-jinsoningen) fork: the house look for the LEGACY View screens.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.looker.droidify.jinsoningen

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * The app is still half View-based: the app list, app detail, repositories and favourites are
 * Fragments with ViewBinding, and only Settings and the UI page are Compose. Those legacy screens
 * take their colours from **Material theme attributes** — through upstream's own
 * `Context.getColorFromAttr` in code, and through a handful of `?attr/…` references in XML.
 *
 * An Android theme is resource-backed, so arbitrary runtime colours cannot become theme
 * attributes. Instead this object supplies the same colours two ways, from the one mapping in
 * [attrColor]:
 *
 *  * upstream's `getColorFromAttr` is patched to consult it, which redirects every colour the
 *    legacy Kotlin resolves;
 *  * [install] adds a `LayoutInflater.Factory2` to the Activity, so **every** view inflated
 *    anywhere — including RecyclerView rows created long after the screen opened, and dialogs —
 *    is tinted the moment it is created.
 *
 * Together those two cover the legacy tree without touching each adapter.
 */
object JinsoningenViewTheme {

    // ------------------------------------------------------------------ mapping

    /**
     * The one place a Material/framework colour attribute becomes a knob.
     *
     * @return the ARGB colour to use, or null when the attribute is not one we own (the caller
     *         then falls back to the real theme lookup, so anything unmapped still resolves).
     */
    fun attrColor(context: Context, attrRes: Int): Int? {
        val ui = JinsoningenUi.get(context)
        return when (attrRes) {
            // colorPrimary / colorAccent / colorError are AppCompat's declarations; the rest are
            // Material 3's. Same merged resource ids either way — only the R class differs.
            AppCompatR.attr.colorPrimary,
            MaterialR.attr.colorSecondary,
            MaterialR.attr.colorTertiary,
            AppCompatR.attr.colorAccent,
            android.R.attr.colorPrimary,
            android.R.attr.colorAccent,
            MaterialR.attr.colorPrimaryInverse,
            -> ui.accentColor

            MaterialR.attr.colorOnPrimary,
            MaterialR.attr.colorOnSecondary,
            MaterialR.attr.colorOnTertiary,
            -> ui.background

            MaterialR.attr.colorPrimaryContainer,
            MaterialR.attr.colorSecondaryContainer,
            MaterialR.attr.colorTertiaryContainer,
            MaterialR.attr.colorSurfaceContainer,
            MaterialR.attr.colorSurfaceContainerHigh,
            MaterialR.attr.colorSurfaceContainerHighest,
            MaterialR.attr.colorSurfaceContainerLow,
            MaterialR.attr.colorSurfaceContainerLowest,
            MaterialR.attr.colorSurfaceVariant,
            MaterialR.attr.colorSurface,
            MaterialR.attr.colorSurfaceBright,
            MaterialR.attr.colorSurfaceDim,
            -> ui.surface

            android.R.attr.colorBackground,
            MaterialR.attr.backgroundColor,
            -> ui.background

            MaterialR.attr.colorOnPrimaryContainer,
            MaterialR.attr.colorOnSecondaryContainer,
            MaterialR.attr.colorOnTertiaryContainer,
            MaterialR.attr.colorOnSurface,
            MaterialR.attr.colorOnBackground,
            android.R.attr.textColorPrimary,
            -> ui.textColor

            MaterialR.attr.colorOnSurfaceVariant,
            android.R.attr.textColorSecondary,
            android.R.attr.textColorTertiary,
            -> ui.textDimColor

            MaterialR.attr.colorOutline -> ui.borderColor
            MaterialR.attr.colorOutlineVariant -> ui.dividerColor

            AppCompatR.attr.colorError,
            MaterialR.attr.colorErrorContainer,
            -> ui.warnColor

            MaterialR.attr.colorOnError,
            MaterialR.attr.colorOnErrorContainer,
            -> ui.background

            else -> null
        }
    }

    /** [attrColor] as a [ColorStateList], for the `getColorFromAttr` patch. */
    fun attrColorStateList(context: Context, attrRes: Int): ColorStateList? =
        attrColor(context, attrRes)?.let { ColorStateList.valueOf(it) }

    // ------------------------------------------------------------------ install

    /**
     * Installs the tinting inflater. **Call before `super.onCreate`** — a `Factory2` can only be
     * set once per `LayoutInflater`, and AppCompat sets its own during `super.onCreate`. We
     * delegate creation to AppCompat's delegate and only paint what it hands back, so AppCompat's
     * own widget substitution (`Button` → `AppCompatButton`, …) is preserved.
     */
    fun install(activity: AppCompatActivity) {
        LayoutInflaterCompat.setFactory2(
            activity.layoutInflater,
            object : LayoutInflater.Factory2 {
                override fun onCreateView(
                    parent: View?,
                    name: String,
                    context: Context,
                    attrs: AttributeSet,
                ): View? = activity.delegate.createView(parent, name, context, attrs)
                    ?.also { paint(it) }

                override fun onCreateView(
                    name: String,
                    context: Context,
                    attrs: AttributeSet,
                ): View? = onCreateView(null, name, context, attrs)
            },
        )
    }

    /** Window chrome — the ground everything else sits on. */
    fun applyWindow(activity: Activity) {
        val ui = JinsoningenUi.get(activity)
        activity.window.decorView.setBackgroundColor(ui.background)
    }

    /**
     * Paints an already-built hierarchy. The inflater covers views created from XML; this covers
     * roots handed to us whole (a Fragment's `onViewCreated`) and anything built in code.
     */
    fun paintTree(view: View) {
        paint(view)
        if (view is ViewGroup) view.children.forEach { paintTree(it) }
    }

    // -------------------------------------------------------------------- paint

    /**
     * One view, by type. Deliberately conservative: it sets colours, the typeface and the text
     * scale, and never touches layout or visibility — a screen must still work if a knob is odd.
     */
    private fun paint(view: View) {
        val context = view.context ?: return
        val ui = JinsoningenUi.get(context)

        when (view) {
            is MaterialToolbar -> {
                view.setBackgroundColor(ui.background)
                view.setTitleTextColor(ui.accentColor)
                view.setSubtitleTextColor(ui.textDimColor)
                view.overflowIcon?.tintWith(ui.iconColor)
                view.navigationIcon?.tintWith(ui.iconColor)
            }

            is AppBarLayout -> view.setBackgroundColor(ui.background)

            is TabLayout -> {
                view.setBackgroundColor(ui.background)
                view.setTabTextColors(ui.textDimColor, ui.accentColor)
                view.setSelectedTabIndicatorColor(ui.accentColor)
            }

            is MaterialDivider -> {
                view.dividerColor = ui.dividerColor
                // 0 means "draw nothing" everywhere in this app, dividers included.
                view.dividerThickness = ui.dividerWidth.dpToPx(context)
            }

            is MaterialCardView -> {
                view.setCardBackgroundColor(ui.surface)
                view.strokeColor = ui.borderColor
                view.strokeWidth = ui.borderWidth.dpToPx(context)
                view.radius = ui.cornerRadius.dpToPx(context).toFloat()
            }

            is ExtendedFloatingActionButton -> {
                view.backgroundTintList = ColorStateList.valueOf(ui.accentColor)
                view.setTextColor(ui.background)
                view.iconTint = ColorStateList.valueOf(ui.background)
                applyType(view, ui, ui.background)
            }

            is MaterialButton -> {
                // An outlined button keeps a transparent ground; a filled one takes the accent.
                if (view.strokeWidth > 0 || view.backgroundTintList == null) {
                    view.strokeColor = ColorStateList.valueOf(ui.borderColor)
                    view.strokeWidth = maxOf(ui.borderWidth, 1).dpToPx(context)
                    view.backgroundTintList = ColorStateList.valueOf(ui.background)
                    view.setTextColor(ui.accentColor)
                    view.iconTint = ColorStateList.valueOf(ui.accentColor)
                    applyType(view, ui, ui.accentColor)
                } else {
                    view.backgroundTintList = ColorStateList.valueOf(ui.accentColor)
                    view.setTextColor(ui.background)
                    view.iconTint = ColorStateList.valueOf(ui.background)
                    applyType(view, ui, ui.background)
                }
                view.cornerRadius = ui.cornerRadius.dpToPx(context)
            }

            is LinearProgressIndicator -> {
                view.setIndicatorColor(ui.accentColor)
                view.trackColor = ui.accentColor.withAlpha(0.25f)
            }

            is ProgressBar -> {
                view.indeterminateTintList = ColorStateList.valueOf(ui.accentColor)
                view.progressTintList = ColorStateList.valueOf(ui.accentColor)
            }

            is TextInputLayout -> {
                view.boxStrokeColor = ui.borderColor
                view.defaultHintTextColor = ColorStateList.valueOf(ui.textDimColor)
                view.hintTextColor = ColorStateList.valueOf(ui.accentColor)
                view.setBoxCornerRadii(
                    ui.cornerRadius.toFloat(),
                    ui.cornerRadius.toFloat(),
                    ui.cornerRadius.toFloat(),
                    ui.cornerRadius.toFloat(),
                )
            }

            is CompoundButton -> {
                // Switches, checkboxes and radios all land here.
                view.buttonTintList = ColorStateList.valueOf(ui.accentColor)
                view.setTextColor(ui.textColor)
                applyType(view, ui, ui.textColor)
            }

            is EditText -> {
                view.setTextColor(ui.textColor)
                view.setHintTextColor(ui.textDimColor)
                view.backgroundTintList = ColorStateList.valueOf(ui.borderColor)
                applyType(view, ui, ui.textColor)
            }

            is Button -> {
                view.setTextColor(ui.accentColor)
                applyType(view, ui, ui.accentColor)
            }

            is TextView -> {
                view.setTextColor(ui.textColor)
                view.setLinkTextColor(ui.accentColor)
                applyType(view, ui, ui.textColor)
            }

            is ImageView -> view.imageTintList?.let {
                view.imageTintList = ColorStateList.valueOf(ui.iconColor)
            }

            is RecyclerView -> view.setBackgroundColor(ui.background)
        }
    }

    /**
     * Typeface, weight, italics and letter spacing from the knobs.
     *
     * The **size** is deliberately left alone: legacy layouts size their text in `sp` for a
     * reason (a row height, an ellipsised label), and rewriting every one of them from a single
     * body-size knob breaks list rows. The Compose screens honour the size knobs; here the knobs
     * that apply are the family, the weight, the slant and the colour.
     */
    private fun applyType(view: TextView, ui: JinsoningenUiState, color: Int) {
        JinsoningenFonts.typeface(view.context, ui.fontFamilyId)?.let { typeface ->
            view.typeface = typeface
        }
        view.setTextColor(color)
        if (ui.fontItalic) {
            view.setTypeface(view.typeface, android.graphics.Typeface.ITALIC)
        }
        if (ui.letterSpacing != 0) view.letterSpacing = ui.letterSpacing / 100f
    }

    // ------------------------------------------------------------------ helpers

    private fun Drawable.tintWith(color: Int) {
        mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    private fun Int.withAlpha(fraction: Float): Int = Color.argb(
        (Color.alpha(this) * fraction).toInt().coerceIn(0, 255),
        Color.red(this),
        Color.green(this),
        Color.blue(this),
    )

    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
