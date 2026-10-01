package com.hoodie.app.notifications

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.notification.HoodieNotifier
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelsTest {

    @Test
    fun canaisCriadosNaInicializacao() {
        val nm = ApplicationProvider.getApplicationContext<android.content.Context>().getSystemService(NotificationManager::class.java)
        assertNotNull(nm.getNotificationChannel(HoodieNotifier.CH_EVENTS))
        assertNotNull(nm.getNotificationChannel(HoodieNotifier.CH_QUESTIONS))
    }
}
