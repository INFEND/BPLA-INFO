package dev.infend.bplainfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
/** Four reference-based visualizations stored locally in one alpha WebP atlas. */
public final class MaraGallery {
    private static Bitmap[] cache;
    private MaraGallery(){}
    public static boolean isMara(Drone d){
        return d!=null&&("seed-mara-2m".equals(d.id)||"МАРА-2М".equalsIgnoreCase(d.name()));
    }
    public static synchronized Bitmap get(Context context,int index){
        if(index<0||index>=4)return null;
        if(cache==null){
            cache=new Bitmap[4];
            Bitmap atlas=BitmapFactory.decodeResource(context.getResources(),R.drawable.mara_2m_atlas);
            if(atlas==null)return null;
            int width=atlas.getWidth()/2,height=atlas.getHeight()/2;
            if(width<1||height<1)return null;
            for(int i=0;i<4;i++){
                cache[i]=Bitmap.createBitmap(atlas,i%2*width,i/2*height,width,height);
            }
        }
        return cache[index];
    }
}
