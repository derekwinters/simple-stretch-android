package com.derekwinters.stretch

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards the application ID, which no spec page names (issue #42). */
class ApplicationIdTest {

    // Issue #42 invariant: the application ID is exactly
    // `io.github.derekwinters.simple_stretch_android`. It matches the Play Console registration and
    // can never change after the first Play upload. The Kotlin namespace (com.derekwinters.stretch)
    // is deliberately different and is not what this checks.
    @Test
    fun applicationIdMatchesThePlayConsoleRegistration() {
        assertEquals("io.github.derekwinters.simple_stretch_android", BuildConfig.APPLICATION_ID)
    }
}
