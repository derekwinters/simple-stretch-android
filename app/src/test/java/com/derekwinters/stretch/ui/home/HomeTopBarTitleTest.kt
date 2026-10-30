package com.derekwinters.stretch.ui.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.derekwinters.stretch.R
import com.derekwinters.stretch.store.ShowcaseComposeTest
import com.derekwinters.stretch.store.ShowcaseShotScreen
import com.derekwinters.stretch.store.StoreShot
import com.derekwinters.stretch.store.StoreShotDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Requirement IDs refer to docs/spec/screens.md (HOME-) and docs/spec/store-listing.md
 * (STORE-LIST-). Home is composed through [HomeContent] with the showcase state, in the frozen
 * environment the store shots use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [StoreShotDevice.SDK], application = Application::class)
class HomeTopBarTitleTest : ShowcaseComposeTest() {

    // HOME-004 and the STORE-LIST-008 invariant: the Home top bar's title is the app's name, read
    // from app_name rather than written as a literal.
    @Test
    fun topBarTitleIsAppName() {
        composeRule.setContent { ShowcaseShotScreen(StoreShot.HOME, context) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
    }
}
