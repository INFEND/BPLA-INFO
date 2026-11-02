package dev.infend.bplainfo;

/** Rejects tiny images without excluding valid portrait photographs. */
final class PhotoDimensions {
    private PhotoDimensions() {}

    static boolean acceptable(int width,int height){
        return width>0 && height>0
                && Math.min(width,height)>=300
                && Math.max(width,height)>=500;
    }
}
