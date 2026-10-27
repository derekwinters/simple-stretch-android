package com.derekwinters.stretch.store

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.derekwinters.stretch.MainActivity
import com.derekwinters.stretch.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Requirement IDs refer to docs/spec/store-listing.md. The app's name on the phone is the store
 * title (STORE-LIST-008).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [StoreShotDevice.SDK], application = Application::class)
class LauncherLabelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // STORE-LIST-008 invariant: app_name equals the compiled store title, so the launcher label
    // and the listing title can only change together.
    @Test
    fun appNameIsTheStoreTitle() {
        val title = repoFile(TITLE_PATH).readText(Charsets.UTF_8).trimEnd('\n')
        assertEquals("Simple Stretch", title)
        assertEquals(title, context.getString(R.string.app_name))
    }

    // STORE-LIST-008 invariant: the manifest names the app_name resource, never a literal, and the
    // launcher activity inherits it rather than declaring a label of its own.
    @Test
    fun manifestLabelsComeFromAppName() {
        assertEquals(R.string.app_name, context.applicationInfo.labelRes)
        val launcher = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        assertEquals(0, launcher.labelRes)
        assertEquals(null, launcher.nonLocalizedLabel)
    }

    private companion object {
        const val TITLE_PATH = "fastlane/metadata/android/en-US/title.txt"
    }
}
