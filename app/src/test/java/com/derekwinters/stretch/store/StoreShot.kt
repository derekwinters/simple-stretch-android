package com.derekwinters.stretch.store

/**
 * The five store screenshots in listing order (STORE-SHOT-001 to STORE-SHOT-005): their shot ID,
 * their file in fastlane's phoneScreenshots folder (STORE-SHOT-007) and whether the screen inside
 * the frame is in the dark theme.
 */
enum class StoreShot(val id: String, val fileName: String, val dark: Boolean) {
    HOME("STORE-SHOT-001", "1_home.png", dark = false),
    SESSION("STORE-SHOT-002", "2_session.png", dark = true),
    GOALS("STORE-SHOT-003", "3_goals.png", dark = false),
    SCHEDULE("STORE-SHOT-004", "4_schedule.png", dark = true),
    TRENDS("STORE-SHOT-005", "5_trends.png", dark = false),
}

/** STORE-SHOT-006: the Robolectric device every store shot and caption measurement runs on. */
object StoreShotDevice {
    /**
     * Pinned rather than the target SDK: Robolectric runs SDK 35 and later only on Java 21, and
     * the build runs on Java 17.
     */
    const val SDK = 34

    /** en-US, a 411 x 891 dp screen at density 3 (xxhdpi). */
    const val QUALIFIERS = "en-rUS-w411dp-h891dp-xxhdpi"
}
