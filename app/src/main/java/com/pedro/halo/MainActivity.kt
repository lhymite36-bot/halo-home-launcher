package com.pedro.halo

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    lateinit var controller: LauncherController
    lateinit var widgetHost: AppWidgetHost
    lateinit var widgetManager: AppWidgetManager

    /** Incremented whenever the Home button is pressed while we're in front. */
    val homePresses = mutableIntStateOf(0)
    private var pendingWidgetPage = 0
    private var pendingWidgetRows = 2
    private var pendingWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        controller = LauncherController(this)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, HOST_ID)
        setContent { LauncherRoot(this) }
    }

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }

    fun setLightStatusBar(light: Boolean) {
        val c = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        c.isAppearanceLightStatusBars = light
        c.isAppearanceLightNavigationBars = light
    }

    // ------------------------------ Widgets ------------------------------

    fun rowsFor(info: AppWidgetProviderInfo): Int {
        val dpH = info.minHeight / resources.displayMetrics.density
        return ceil(dpH / 80f).toInt().coerceIn(1, 4)
    }

    fun requestWidget(info: AppWidgetProviderInfo, page: Int) {
        pendingWidgetPage = page
        pendingWidgetRows = rowsFor(info)
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val ok = runCatching { widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        if (ok) configureOrAdd(id) else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            runCatching { startActivityForResult(i, REQ_BIND) }.onFailure { widgetHost.deleteAppWidgetId(id) }
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            runCatching { widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null) }
                .onFailure { addWidget(id) }
        } else addWidget(id)
    }

    private fun addWidget(id: Int) = controller.addWidget(id, pendingWidgetRows, pendingWidgetPage)

    fun removeWidget(item: HomeItem.Widget) {
        controller.removeItem(item.id)
        runCatching { widgetHost.deleteAppWidgetId(item.widgetId) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK && id != -1) configureOrAdd(id)
                else if (id != -1) widgetHost.deleteAppWidgetId(id)
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK && id != -1) addWidget(id)
                else if (id != -1) widgetHost.deleteAppWidgetId(id)
        }
    }

    companion object {
        const val HOST_ID = 2026
        const val REQ_BIND = 11
        const val REQ_CONFIGURE = 12
    }
}
