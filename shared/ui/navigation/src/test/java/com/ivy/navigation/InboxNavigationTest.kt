package com.ivy.navigation

import org.junit.Assert.*
import org.junit.Test

class InboxNavigationTest {
    @Test fun `closing inbox leaves home with no inbox in back history`() {
        val nav = Navigation()
        nav.navigateTo(MainScreen)
        nav.navigateTo(InboxScreen)
        nav.returnToRoot(MainScreen)
        assertEquals(MainScreen, nav.currentScreen)
        assertFalse(nav.back())
        assertEquals(MainScreen, nav.currentScreen)
    }

    @Test fun `closing inbox opened as initial screen returns home`() {
        val nav = Navigation()
        nav.navigateTo(InboxScreen)
        nav.returnToRoot(MainScreen)
        assertEquals(MainScreen, nav.currentScreen)
        assertTrue(nav.backStackEmpty())
    }

    @Test fun `reopening inbox and pressing back returns home`() {
        val nav = Navigation()
        nav.navigateTo(MainScreen)
        nav.navigateTo(InboxScreen)
        nav.returnToRoot(MainScreen)
        nav.navigateTo(InboxScreen)
        assertTrue(nav.back())
        assertEquals(MainScreen, nav.currentScreen)
        assertFalse(nav.back())
    }
}
