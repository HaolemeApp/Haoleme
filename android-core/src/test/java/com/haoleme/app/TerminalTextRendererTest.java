package com.haoleme.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalTextRendererTest {
    @Test
    public void rendersLatestWatchFrame() {
        String raw = "\u001b[?1049h\u001b[H\u001b[2J"
                + "Every 1.0s: status\r\nold value\r\n"
                + "\u001b[HEvery 1.0s: status\u001b[K\r\nnew value\u001b[K"
                + "\u001b[?1049l";

        String rendered = TerminalTextRenderer.render(raw);

        assertTrue(rendered.contains("Every 1.0s: status"));
        assertTrue(rendered.contains("new value"));
        assertFalse(rendered.contains("old value"));
        assertFalse(rendered.contains("\u001b"));
    }

    @Test
    public void appliesCursorPositionAndEraseCommands() {
        String raw = "GPU 0: 10%\r\nGPU 1: 20%"
                + "\u001b[1;8H99%\u001b[K"
                + "\u001b[2;8H88%\u001b[K";

        assertEquals("GPU 0: 99%\nGPU 1: 88%", TerminalTextRenderer.render(raw));
    }

    @Test
    public void collapsesProgressFramesAndStripsColor() {
        String raw = "step 1\rstep 2\r\u001b[32mstep 3\u001b[0m\r\ndone\r\n";

        assertEquals("step 3\ndone", TerminalTextRenderer.render(raw));
    }

    @Test
    public void ignoresOperatingSystemControlTitles() {
        String raw = "\u001b]0;watch title\u0007hello\r\nworld";

        assertEquals("hello\nworld", TerminalTextRenderer.render(raw));
    }
}
