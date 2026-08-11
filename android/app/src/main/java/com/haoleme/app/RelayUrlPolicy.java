package com.haoleme.app;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;

final class RelayUrlPolicy {
    private RelayUrlPolicy() {
    }

    static boolean isAllowed(String value) {
        try {
            URL url = new URL(value);
            String host = url.getHost() == null ? "" : url.getHost().trim();
            if (host.isEmpty() || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) {
                return false;
            }
            String path = url.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                return false;
            }
            if ("https".equalsIgnoreCase(url.getProtocol())) {
                return true;
            }
            return "http".equalsIgnoreCase(url.getProtocol()) && isLocalHost(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isLocalHttp(String value) {
        try {
            URL url = new URL(value);
            return "http".equalsIgnoreCase(url.getProtocol()) && isLocalHost(url.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean isLocalHost(String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (isPrivateIpv4(host)) {
            return true;
        }
        if (!host.contains(":") || !host.matches("[0-9a-fA-F:.%]+")) {
            return false;
        }
        try {
            String addressWithoutZone = host.split("%", 2)[0];
            InetAddress address = InetAddress.getByName(addressWithoutZone);
            if (!(address instanceof Inet6Address)) {
                return false;
            }
            byte first = address.getAddress()[0];
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            return uniqueLocal || address.isLoopbackAddress() || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        int first = octets[0];
        int second = octets[1];
        return first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }
}
