package com.symverse.aiglasses.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatViewTest {
    @Test
    fun shouldCycleShownTransparentHiddenAndBackToShown() {
        assertEquals(ChatView.Transparent, ChatView.Shown.next())
        assertEquals(ChatView.Hidden, ChatView.Transparent.next())
        assertEquals(ChatView.Shown, ChatView.Hidden.next())
    }
}
