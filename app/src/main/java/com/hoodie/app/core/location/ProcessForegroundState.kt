package com.hoodie.app.core.location
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
/** Tracks every activity in this process, independently of Home. */
@Singleton
class ProcessForegroundState @Inject constructor(@ApplicationContext context: Context) : Application.ActivityLifecycleCallbacks {
    private var started = 0
    @Volatile var isVisible: Boolean = false
        private set
    init { (context.applicationContext as Application).registerActivityLifecycleCallbacks(this) }
    override fun onActivityStarted(activity: Activity) { started++; isVisible = started > 0 }
    override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0); isVisible = started > 0 }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
