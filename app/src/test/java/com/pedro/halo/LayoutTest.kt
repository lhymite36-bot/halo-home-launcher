package com.pedro.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {
    private fun apps(n: Int) = List(n) { HomeItem.App(it.toLong(), "k$it") }

    @Test fun appsFillRows() {
        assertEquals(2, rowsUsed(apps(5), 4))
        assertEquals(1, rowsUsed(apps(4), 4))
        assertEquals(0, rowsUsed(emptyList(), 4))
    }

    @Test fun widgetsTakeFullRows() {
        val items = listOf<HomeItem>(HomeItem.Widget(100, 1, 2)) + apps(3)
        assertEquals(3, rowsUsed(items, 4))
        val rows = layoutRows(items, 4)
        assertEquals(2, rows.size)
    }

    @Test fun headerRowsMatchClock() {
        assertEquals(2, headerRows(LauncherSettings(clockStyle = ClockStyle.BIG)))
        assertEquals(0, headerRows(LauncherSettings(clockStyle = ClockStyle.HIDDEN)))
        assertEquals(4, availableRows(LauncherSettings(gridRows = 6), 0))
        assertEquals(6, availableRows(LauncherSettings(gridRows = 6), 1))
    }
}
