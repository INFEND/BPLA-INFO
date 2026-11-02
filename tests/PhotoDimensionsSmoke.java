package dev.infend.bplainfo;

public final class PhotoDimensionsSmoke {
    private static void check(boolean expected,int width,int height){
        boolean actual=PhotoDimensions.acceptable(width,height);
        if(actual!=expected)
            throw new AssertionError(width+"x"+height+" expected "+expected+" but got "+actual);
    }
    public static void main(String[] args){
        check(true,1600,900);
        check(true,480,1280);
        check(true,1280,480);
        check(true,300,500);
        check(true,500,300);
        check(false,299,1600);
        check(false,1600,299);
        check(false,499,300);
        check(false,0,800);
        check(false,-1,800);
        System.out.println("Photo dimensions: 10 landscape/portrait/invalid cases passed");
    }
}
