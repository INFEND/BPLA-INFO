package dev.infend.bplainfo;

/** Runs on plain JVM; no Android SDK or live Wikimedia requests required. */
public final class CommonsImageUrlsSmoke {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String name = "Bayraktar TB2 Ground.jpg";
        check(CommonsImageUrls.thumbnail(name).equals(
            "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/9e/Bayraktar_TB2_Ground.jpg/1280px-Bayraktar_TB2_Ground.jpg"),
            "thumbnail hash path or filename is incorrect");
        check(CommonsImageUrls.original(name).equals(
            "https://upload.wikimedia.org/wikipedia/commons/9/9e/Bayraktar_TB2_Ground.jpg"),
            "original URL is incorrect");
        check(CommonsImageUrls.attribution(name).equals(
            "https://commons.wikimedia.org/wiki/File:Bayraktar_TB2_Ground.jpg"),
            "attribution page URL is incorrect");
        String unicode = CommonsImageUrls.thumbnail("Bayraktar Mini İHA Karlı Hava Durumunda İniş.JPG");
        check(unicode.startsWith("https://thumb.wikimedia.org/"), "wrong thumbnail host");
        check(unicode.contains("%C4%B0"), "non-ASCII file name was not encoded");
        check(!unicode.contains(" "), "unescaped space in URL");
        check(!unicode.contains("Special:Redirect"), "thumbnail must not use Commons redirect");
        System.out.println("CommonsImageUrlsSmoke: 6 checks passed");
    }
}
