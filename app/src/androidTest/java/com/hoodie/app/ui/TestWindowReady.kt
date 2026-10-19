package com.hoodie.app.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Request a visible test host, then synchronize with lifecycle AND Window focus before IME/a11y. */
internal fun <A : ComponentActivity> awaitTestWindow(
    rule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
) {
    configureTestWindow(rule)
    rule.waitUntil(10_000) {
        rule.activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
            rule.activity.window.decorView.hasWindowFocus()
    }
}

/** Configure before setContent; await focus only after the host has presented its first content. */
internal fun <A : ComponentActivity> configureTestWindow(
    rule: AndroidComposeTestRule<ActivityScenarioRule<A>, A>,
) {
    rule.runOnUiThread {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            rule.activity.setTurnScreenOn(true)
            rule.activity.setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        rule.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rule.activity.getSystemService(android.app.KeyguardManager::class.java)
            .requestDismissKeyguard(rule.activity, null)
    }
}
