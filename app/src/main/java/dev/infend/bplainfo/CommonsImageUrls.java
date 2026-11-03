package dev.infend.bplainfo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Deterministic public Wikimedia Commons image URLs. No API key or private
 * GitHub endpoint is required. The CDN thumbnail avoids a separate request
 * to Commons' Special:Redirect endpoint for every gallery image.
 *
 * A syntactically valid URL is not proof that a particular Commons file exists.
 */
public final class CommonsImageUrls {
    private CommonsImageUrls() {}

    private static String encodedName(String name) throws Exception {
        return URLEncoder.encode(name.replace(' ', '_'), StandardCharsets.UTF_8.name())
                .replace("+", "%20");
    }

    private static String hashPath(String name) throws Exception {
        byte[] hash = MessageDigest.getInstance("MD5")
                .digest(name.replace(' ', '_').getBytes(StandardCharsets.UTF_8));
        StringBuilder md5 = new StringBuilder();
        for (byte b : hash) md5.append(String.format(Locale.ROOT, "%02x", b & 255));
        return md5.substring(0, 1) + "/" + md5.substring(0, 2);
    }

    public static String thumbnail(String name) {
        try {
            String canonical = encodedName(name);
            return "https://upload.wikimedia.org/wikipedia/commons/thumb/"
                    + hashPath(name) + "/" + canonical + "/1280px-" + canonical;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String original(String name) {
        try {
            return "https://upload.wikimedia.org/wikipedia/commons/"
                    + hashPath(name) + "/" + encodedName(name);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String attribution(String name) {
        try {
            return "https://commons.wikimedia.org/wiki/File:" + encodedName(name);
        } catch (Exception ignored) {
            return "";
        }
    }
}
