package com.derekwinters.stretch.widget

/**
 * The launcher icon's colours, the one Kotlin definition of them (HOME-006 invariant). They are
 * the `launcher_icon_figure` and `launcher_icon_mat` colour resources that
 * `drawable/ic_launcher_foreground.xml` draws with; `LauncherIconColorsTest` keeps the two equal.
 */
object LauncherIconColors {
    /** The figure's body and head. */
    const val FIGURE: Int = 0xFF1361A7.toInt()

    /** The yoga mat. */
    const val MAT: Int = 0xFF818589.toInt()
}
