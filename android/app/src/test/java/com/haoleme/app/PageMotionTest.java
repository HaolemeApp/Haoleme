package com.haoleme.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PageMotionTest {
    @Test
    public void transitionDurationsMatchThePlan() {
        assertEquals(220L, PageMotion.TAB_MS);
        assertEquals(210L, PageMotion.PUSH_MS);
        assertEquals(180L, PageMotion.POP_MS);
        assertEquals(180L, PageMotion.DIALOG_IN_MS);
        assertEquals(140L, PageMotion.DIALOG_OUT_MS);
        assertTrue(PageMotion.EASE != null);
    }

    @Test
    public void nullViewsAreSafe() {
        PageMotion.cancel(null);
        PageMotion.crossfade(null, null, null);
        PageMotion.pushOverlay(null);
        PageMotion.popOverlay(null, null);
    }
}
