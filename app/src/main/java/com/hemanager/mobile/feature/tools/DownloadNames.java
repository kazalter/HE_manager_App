package com.hemanager.mobile.feature.tools;

import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preserve server filenames rather than changing their extension for octet-stream. */
public final class DownloadNames {
    private static final Pattern ENCODED = Pattern.compile("filename\\*\\s*=\\s*UTF-8''([^;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN = Pattern.compile("filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))", Pattern.CASE_INSENSITIVE);
    private DownloadNames() {}

    public static String choose(String disposition, String fallback) {
        String value = fallback;
        if (disposition != null) {
            Matcher encoded = ENCODED.matcher(disposition);
            Matcher plain = PLAIN.matcher(disposition);
            if (encoded.find()) {
                try { value = URLDecoder.decode(encoded.group(1).trim().replace("+", "%2B"), "UTF-8"); }
                catch (Exception ignored) { if (plain.find()) value = plain.group(1) != null ? plain.group(1) : plain.group(2); }
            } else if (plain.find()) value = plain.group(1) != null ? plain.group(1) : plain.group(2);
        }
        if (value == null) value = "download";
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim().replaceAll("[\\p{Cntrl}<>:\"|?*]", "_");
        return value.isEmpty() || value.equals(".") || value.equals("..") ? "download" : value;
    }
}
