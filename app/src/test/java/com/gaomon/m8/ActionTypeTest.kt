package com.gaomon.m8

import com.gaomon.m8.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActionTypeTest {

    @Test
    fun testFromIdValidCases() {
        assertEquals(ActionType.NONE, ActionType.fromId("none"))
        assertEquals(ActionType.TOGGLE_ERASER_HOLD, ActionType.fromId("eraser_hold"))
        assertEquals(ActionType.SELECT_PEN, ActionType.fromId("pen"))
        assertEquals(ActionType.SELECT_ERASER, ActionType.fromId("eraser"))
        assertEquals(ActionType.SELECT_LASSO, ActionType.fromId("lasso"))
    }

    @Test
    fun testFromIdInvalidCasesFallbackToNone() {
        assertEquals(ActionType.NONE, ActionType.fromId(""))
        assertEquals(ActionType.NONE, ActionType.fromId("unknown_action"))
        assertEquals(ActionType.NONE, ActionType.fromId("null"))
    }

    @Test
    fun testDisplayNamesAreNotEmpty() {
        for (action in ActionType.entries) {
            assertNotNull(action.displayName)
            assert(action.displayName.isNotBlank())
        }
    }
}
