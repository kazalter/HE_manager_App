package com.hemanager.mobile.feature.tools;

import java.net.URI;
import java.net.URISyntaxException;

/** Origin checks used before giving the embedded HE frontend a login session. */
public final class WebEndpoint {
    private WebEndpoint() {}

    public static String normalize(String value) {
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("请输入 Web 首页地址，例如 http://192.168.0.101:8011");
            }
            return new URI(scheme.toLowerCase(java.util.Locale.ROOT), null,
                    uri.getHost().toLowerCase(java.util.Locale.ROOT), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("服务器地址格式不正确", e);
        }
    }

    public static String defaultFor(String apiUrl) {
        String origin = normalize(apiUrl);
        URI uri = URI.create(origin);
        if (uri.getPort() != 8010) return origin;
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), 8011, null, null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static boolean sameOrigin(String expected, String candidate) {
        try {
            URI a = URI.create(expected);
            URI b = URI.create(candidate);
            return a.getHost() != null && b.getHost() != null && b.getUserInfo() == null
                    && a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost().equalsIgnoreCase(b.getHost())
                    && effectivePort(a) == effectivePort(b);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }
}
