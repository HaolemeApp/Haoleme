package com.haoleme.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelayUrlPolicyTest {
    @Test
    public void acceptsHttpsRelay() {
        assertTrue(RelayUrlPolicy.isAllowed("https://relay.example.com"));
    }

    @Test
    public void acceptsPrivateLanHttpRelay() {
        assertTrue(RelayUrlPolicy.isAllowed("http://192.168.1.20:8000"));
        assertTrue(RelayUrlPolicy.isAllowed("http://10.20.30.40:8765"));
        assertTrue(RelayUrlPolicy.isAllowed("http://172.16.0.2:8000"));
        assertTrue(RelayUrlPolicy.isAllowed("http://127.0.0.1:8000"));
        assertTrue(RelayUrlPolicy.isAllowed("http://[fd00::1]:8000"));
    }

    @Test
    public void rejectsPublicOrNamedHttpRelay() {
        assertFalse(RelayUrlPolicy.isAllowed("http://8.8.8.8:8000"));
        assertFalse(RelayUrlPolicy.isAllowed("http://relay.example.com:8000"));
        assertFalse(RelayUrlPolicy.isAllowed("ftp://192.168.1.20:8000"));
    }

    @Test
    public void rejectsCredentialsAndUnexpectedPaths() {
        assertFalse(RelayUrlPolicy.isAllowed("http://user:pass@192.168.1.20:8000"));
        assertFalse(RelayUrlPolicy.isAllowed("http://192.168.1.20:8000/relay"));
    }
}
