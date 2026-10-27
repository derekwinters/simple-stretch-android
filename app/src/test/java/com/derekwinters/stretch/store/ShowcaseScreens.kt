package com.derekwinters.stretch.store

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.derekwinters.stretch.ui.goals.GoalsContent
import com.derekwinters.stretch.ui.home.HomeContent
import com.derekwinters.stretch.ui.schedule.ScheduleEditContent
import com.derekwinters.stretch.ui.session.SessionContent
import com.derekwinters.stretch.ui.theme.StretchTheme
import com.derekwinters.stretch.ui.trends.TrendsContent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows.shadowOf
import java.util.Locale
import java.util.TimeZone

/**
 * The app screen a store shot shows (STORE-SHOT-001 to STORE-SHOT-005), composed through its
 * stateless content composable with the state [ShowcaseFixture] built (STORE-SHOT-006 invariant),
 * in the shot's fixed theme with dynamic colour off. The store shots and the feature graphic's
 * phones (STORE-GRAPHIC-007) both draw their screens through this, so a phone on the graphic is
 * the same screen as its shot. [context] is the application context, which formats the session
 * heading's time.
 */
@Composable
fun ShowcaseShotScreen(shot: StoreShot, context: Context) {
    StretchTheme(darkTheme = shot.dark, dynamicColor = false) {
        when (shot) {
            // STORE-SHOT-001: home, today's reminders and goal progress (HOME-002, HOME-006). Light.
            StoreShot.HOME -> HomeContent(
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

            // STORE-SHOT-002: the session from a reminder, some stretches checked (SESS-001 to
            // SESS-003). Dark.
            StoreShot.SESSION -> SessionContent(
                layout = ShowcaseFixture.sessionLayout(),
                topHeading = ShowcaseFixture.sessionHeading(context),
                checked = ShowcaseFixture.sessionChecked,
                canSave = true,
                onBack = {},
                onSave = {},
                onToggle = {},
            )

            // STORE-SHOT-003: daily goals, partway done (GOAL-001, GOAL-003). Light.
            StoreShot.GOALS -> GoalsContent(
                state = ShowcaseFixture.goals(),
                onBack = {},
                onTap = { _, _ -> },
                onNewStretch = {},
            )

            // STORE-SHOT-004: the schedule editor on a repeating schedule (SCHED-011, SCHED-012). Dark.
            StoreShot.SCHEDULE -> {
                val form = remember { ShowcaseFixture.scheduleForm() }
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

            // STORE-SHOT-005: weekly trends with the streak (TREND-004 to TREND-006). Light.
            StoreShot.TRENDS -> TrendsContent(
                state = ShowcaseFixture.trends(),
                onBack = {},
                onOpenDay = {},
                onSetKind = {},
                onPrevious = {},
                onNext = {},
            )
        }
    }
}

/**
 * The frozen environment every composed store image runs in (STORE-SHOT-006): a compose rule on
 * a plain host activity, en-US, the fixture's zone, the 12-hour clock and no animations. The
 * store shots ([StoreScreenshotTest]) and the feature graphic ([FeatureGraphicTest]) extend it,
 * each with the STORE-SHOT-006 device in its own `@Config`.
 *
 * The application must be a plain [android.app.Application], not StretchApp, so nothing starts
 * the database or the alarms (STORE-SHOT-006 invariant).
 */
abstract class ShowcaseComposeTest {

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    protected val composeRule: ComposeContentTestRule = createComposeRule()

    /**
     * The compose rule's host activity is declared by ui-test-manifest only in debug builds;
     * registering it here lets the release unit tests compose the images too.
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
}
