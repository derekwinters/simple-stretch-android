package com.derekwinters.stretch.store

import android.app.Application
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Renders the Google Play phone
 * screenshots from [ShowcaseFixture] on the JVM.
 *
 * - `./gradlew verifyRoborazziDebug` (what CI runs) compares each shot with the committed image
 *   in fastlane/metadata/android/en-US/images/phoneScreenshots/ and fails on any change or on a
 *   missing image; the actual and `_compare` images land in app/build/outputs/roborazzi/.
 * - `./gradlew recordRoborazziDebug` re-records them, only when a person asks: locally, or via
 *   the `record-store-screenshots` label on a pull request (STORE-SHOT-009).
 * - Under plain `./gradlew test` each shot is still composed and its caption checked, but no
 *   image is written or compared.
 *
 * The application is a plain [Application], not StretchApp, so nothing starts the database or
 * the alarms (STORE-SHOT-006 invariant); the frozen environment is [ShowcaseComposeTest]'s and
 * each screen is [ShowcaseShotScreen], shared with the feature graphic (STORE-GRAPHIC-007).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class StoreScreenshotTest : ShowcaseComposeTest() {

    // STORE-SHOT-001: home, today's reminders and goal progress (HOME-002, HOME-006). Light.
    @Test
    fun home() = shoot(StoreShot.HOME)

    // STORE-SHOT-002: the session from a reminder, some stretches checked (SESS-001 to SESS-003). Dark.
    @Test
    fun session() = shoot(StoreShot.SESSION)

    // STORE-SHOT-003: daily goals, partway done (GOAL-001, GOAL-003). Light.
    @Test
    fun goals() = shoot(StoreShot.GOALS)

    // STORE-SHOT-004: the schedule editor on a repeating schedule (SCHED-011, SCHED-012). Dark.
    @Test
    fun schedule() = shoot(StoreShot.SCHEDULE)

    // STORE-SHOT-005: weekly trends with the streak (TREND-004 to TREND-006). Light.
    @Test
    fun trends() = shoot(StoreShot.TRENDS)

    /**
     * Checks the caption fits (STORE-SHOT-014 invariant: a long one fails here, it is never
     * shrunk), composes the shot with its screen in the shot's fixed theme, dynamic colour off
     * (STORE-SHOT-006), and captures it as the shot's file (STORE-SHOT-007).
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shoot(shot: StoreShot) {
        val caption = Captions.of(shot)
        CaptionFit.require(caption, CaptionMeasure.lines(context, caption))

        composeRule.setContent {
            StoreShotFrame(caption) { ShowcaseShotScreen(shot, context) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StoreShotSpec.TAG)
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${shot.fileName}")
    }
}
