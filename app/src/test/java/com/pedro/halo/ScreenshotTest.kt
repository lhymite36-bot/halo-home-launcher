package com.pedro.halo

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Process
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val names = listOf("Phone", "Messages", "Browser", "Camera", "Gallery", "Play Store", "WhatsApp", "YouTube",
        "Gmail", "Maps", "Clock", "Settings", "Calculator", "Files", "Music", "Notes", "Weather", "Calendar", "Contacts",
        "Drive", "Meet", "Photos", "Spotify", "Instagram", "Health", "Wallet", "Recorder", "Translate", "News", "Podcasts")
    private val colors = listOf(0xFF2BB673, 0xFF3D7BFF, 0xFFFF8A3D, 0xFF5C5C66, 0xFFE0569B, 0xFF14B8A6, 0xFF25D366, 0xFFFF3B30,
        0xFFEA4335, 0xFF34A853, 0xFF1C1C1E, 0xFF8E8E93, 0xFF6A5CFF, 0xFFF2B21B)

    private fun icon(i: Int): AdaptiveIconDrawable {
        val fg = if (i % 2 == 0) ShapeDrawable(OvalShape()) else ShapeDrawable(RoundRectShape(FloatArray(8) { 18f }, null, null))
        fg.paint.color = Color.WHITE
        fg.intrinsicWidth = 100; fg.intrinsicHeight = 100
        return AdaptiveIconDrawable(ColorDrawable(colors[i % colors.size].toInt()), InsetDrawable(fg, 0.36f))
    }

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(600); rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test fun captureScreens() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = AppRepository.get(ctx)
        val store = SettingsStore.get(ctx)
        Thread.sleep(800) // let the real (empty) app scan finish first
        val apps = names.mapIndexed { i, n ->
            AppEntry("demo.$i/.Main#0", n, ComponentName("demo.$i", "demo.$i.Main"), Process.myUserHandle(), false)
        }
        repo.testIcons = { k -> icon(k.substringAfter("demo.").substringBefore("/").toInt()) }
        val k = apps.map { it.key }
        store.update { LauncherSettings(onboarded = true) }
        store.updateLayout {
            HomeLayout(
                pages = listOf(listOf(
                    HomeItem.App(1, k[4]), HomeItem.App(2, k[5]), HomeItem.App(3, k[6]), HomeItem.App(4, k[7]),
                    HomeItem.App(5, k[8]), HomeItem.App(6, k[9]), HomeItem.App(7, k[10]),
                    HomeItem.Folder(8, "Google", listOf(k[19], k[20], k[21], k[27], k[28])),
                ), listOf(HomeItem.App(9, k[11]))),
                dock = listOf(HomeItem.App(10, k[0]), HomeItem.App(11, k[1]), HomeItem.App(12, k[2]), HomeItem.App(13, k[3])),
                seeded = true,
            )
        }
        repo.setAppsForTest(apps)
        shot("01_home_light")

        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.7f, endY = top + 50f, durationMillis = 300) }
        shot("02_apps_screen")
        rule.activity.homePresses.intValue++
        shot("02b_after_home")

        rule.onNodeWithText("Google").performClick()
        shot("03_folder")
        rule.activity.homePresses.intValue++
        rule.waitForIdle()

        rule.onAllNodesWithText("YouTube")[0].performTouchInput { longClick() }
        shot("04_app_menu")
        rule.activity.homePresses.intValue++

        store.update { it.copy(themeMode = ThemeMode.DARK, wallpaper = "midnight", iconStyle = IconStyle.THEMED, accent = 0xFF9B5CFF.toInt(), clockStyle = ClockStyle.STACKED) }
        shot("05_home_dark_themed")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.7f, endY = top + 50f, durationMillis = 300) }
        shot("06_apps_dark")
        rule.activity.homePresses.intValue++

        store.update { it.copy(themeMode = ThemeMode.LIGHT, wallpaper = "sunset", iconStyle = IconStyle.ORIGINAL, iconShape = IconShape.CIRCLE, gridCols = 5, gridRows = 6, clockStyle = ClockStyle.COMPACT) }
        shot("07_home_5x6_circle")

        store.update { it.copy(onboarded = false, wallpaper = "aurora") }
        shot("08_welcome")
        store.update { it.copy(onboarded = true) }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<SettingsActivity>()

    @Test fun captureSettings() {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/09_settings_top.png")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top + 10f, durationMillis = 400) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/10_settings_scrolled.png")
        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top + 10f, durationMillis = 400) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/11_settings_more.png")
    }
}
