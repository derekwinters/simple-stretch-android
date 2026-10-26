package com.derekwinters.stretch.store

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.derekwinters.stretch.ui.goals.GoalsContent
import com.derekwinters.stretch.ui.home.HomeContent
import com.derekwinters.stretch.ui.schedule.ScheduleEditContent
import com.derekwinters.stretch.ui.session.SessionContent
import com.derekwinters.stretch.ui.theme.StretchTheme
import com.derekwinters.stretch.ui.trends.TrendsContent
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Renders the Google Play phone
 * screenshots from [ShowcaseFixture] on the JVM.
 *
 * - `./gradlew verifyRoborazziDebug` (what CI runs) compares each shot with the committed image
 *   in fastlane/metadata/android/en-US/images/phoneScreenshots/ and fails on any change or on a
 *   missing image; the actual and `_compare` images land in app/build/outputs/roborazzi/.
 * - `./gradlew recordRoborazziDebug` re-records them, deliberately, for a pull request.
 * - Under plain `./gradlew test` each shot is still composed and its caption checked, but no
 *   image is written or compared.
 *
 * The application is a plain [Application], not StretchApp, so nothing starts the database or
 * the alarms (STORE-SHOT-006 invariant).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class StoreScreenshotTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val composeRule = createComposeRule()

    /**
     * The compose rule's host activity is declared by ui-test-manifest only in debug builds;
     * registering it here lets the release unit tests compose the shots too.
     */
    private val hostActivity = object : ExternalResource() {
        override fun before() {
            shadowOf(context.packageManager)
                .addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity).around(composeRule)

    private lateinit var savedLocale: Locale
    private lateinit var savedTimeZone: TimeZone

    /** STORE-SHOT-006: en-US, the fixture's zone, the 12-hour clock and no animations. */
    @Before
    fun freezeEnvironment() {
        savedLocale = Locale.getDefault()
        savedTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ShowcaseFixture.zone))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun restoreEnvironment() {
        Locale.setDefault(savedLocale)
        TimeZone.setDefault(savedTimeZone)
    }

    // STORE-SHOT-001: home, today's reminders and goal progress (HOME-002, HOME-006). Light.
    @Test
    fun home() = shoot(StoreShot.HOME) {
        HomeContent(
            state = ShowcaseFixture.home(),
            canNotify = true,
            canExact = true,
            clock = ShowcaseFixture.clock,
            onEditSchedule = {},
            onNewSchedule = {},
            onOpenStretches = {},
            onOpenSkips = {},
            onOpenGoals = {},
            onOpenTrends = {},
            onStretchNow = {},
            onOpenSession = {},
            onSetTodaySkipped = {},
            onSetScheduleEnabled = { _, _ -> },
        )
    }

    // STORE-SHOT-002: the session from a reminder, some stretches checked (SESS-001 to SESS-003). Dark.
    @Test
    fun session() = shoot(StoreShot.SESSION) {
        SessionContent(
            layout = ShowcaseFixture.sessionLayout(),
            topHeading = ShowcaseFixture.sessionHeading(context),
            checked = ShowcaseFixture.sessionChecked,
            canSave = true,
            onBack = {},
            onSave = {},
            onToggle = {},
        )
    }

    // STORE-SHOT-003: daily goals, partway done (GOAL-001, GOAL-003). Light.
    @Test
    fun goals() = shoot(StoreShot.GOALS) {
        GoalsContent(
            state = ShowcaseFixture.goals(),
            onBack = {},
            onTap = { _, _ -> },
            onNewStretch = {},
        )
    }

    // STORE-SHOT-004: the schedule editor on a repeating schedule (SCHED-011, SCHED-012). Dark.
    @Test
    fun schedule() {
        val form = ShowcaseFixture.scheduleForm()
        shoot(StoreShot.SCHEDULE) {
            ScheduleEditContent(
                form = form,
                stretches = ShowcaseFixture.stretches,
                isNew = false,
                loading = false,
                canSave = form.isComplete,
                onBack = {},
                onSave = {},
                onDelete = {},
                onCreateStretch = { _, _ -> },
            )
        }
    }

    // STORE-SHOT-005: weekly trends with the streak (TREND-004 to TREND-006). Light.
    @Test
    fun trends() = shoot(StoreShot.TRENDS) {
        TrendsContent(
            state = ShowcaseFixture.trends(),
            onBack = {},
            onOpenDay = {},
            onSetKind = {},
            onPrevious = {},
            onNext = {},
        )
    }

    /**
     * Checks the caption fits (STORE-SHOT-014 invariant: a long one fails here, it is never
     * shrunk), composes the shot with [screen] in the shot's fixed theme, dynamic colour off
     * (STORE-SHOT-006), and captures it as the shot's file (STORE-SHOT-007).
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shoot(shot: StoreShot, screen: @Composable () -> Unit) {
        val caption = Captions.of(shot)
        CaptionFit.require(caption, CaptionMeasure.lines(context, caption))

        composeRule.setContent {
            StoreShotFrame(caption) {
                StretchTheme(darkTheme = shot.dark, dynamicColor = false) { screen() }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(StoreShotSpec.TAG)
            .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${shot.fileName}")
    }
}
