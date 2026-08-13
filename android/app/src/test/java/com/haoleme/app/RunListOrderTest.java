package com.haoleme.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RunListOrderTest {
    @Test
    public void pinnedIdsMoveToFrontInPinOrder() {
        List<String> ordered = RunListOrder.orderIds(
                Arrays.asList("a", "b", "c", "d"),
                Arrays.asList("c", "a")
        );
        assertEquals(Arrays.asList("c", "a", "b", "d"), ordered);
    }

    @Test
    public void unpinKeepsRemainingRelativeOrder() {
        List<String> ordered = RunListOrder.orderIds(
                Arrays.asList("c", "a", "b", "d"),
                Collections.singletonList("c")
        );
        assertEquals(Arrays.asList("c", "a", "b", "d"), ordered);
        ordered = RunListOrder.orderIds(
                Arrays.asList("c", "a", "b", "d"),
                Collections.<String>emptyList()
        );
        assertEquals(Arrays.asList("c", "a", "b", "d"), ordered);
    }

    @Test
    public void missingPinnedIdsAreSkipped() {
        List<String> ordered = RunListOrder.orderIds(
                Arrays.asList("a", "b"),
                Arrays.asList("z", "b")
        );
        assertEquals(Arrays.asList("b", "a"), ordered);
    }

    @Test
    public void emptyPlaceholderIsDropped() {
        List<String> ordered = RunListOrder.orderIds(
                Arrays.asList("__empty__", "a"),
                Collections.singletonList("a")
        );
        assertEquals(Collections.singletonList("a"), ordered);
    }
}
