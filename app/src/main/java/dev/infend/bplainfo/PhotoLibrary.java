package dev.infend.bplainfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Photographs are supplied as verifiable APK assets, never from remote URLs.
 * User-selected content:// photos remain managed separately by MainActivity.
 */
public final class PhotoLibrary {
    public static final class Photo {
        public final String file, sha256, author, license, source;
        public Photo(String file,String sha256,String author,String license,String source) {
            this.file=file;
            this.sha256=sha256;
            this.author=author;
            this.license=license;
            this.source=source;
        }
    }
    public interface Callback { void ready(Bitmap bitmap); }
    public interface ResultCallback { void complete(boolean success); }

    private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(2);
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static Map<String,List<Photo>> ITEMS;
    private PhotoLibrary(){}

    /** Removes all old downloaded media, including incomplete .part files. */
    public static void purgeLegacyCache(Context context){
        Context app=context.getApplicationContext();
        if(app.getSharedPreferences("offline_photos",Context.MODE_PRIVATE)
                .getBoolean("legacy_purged",false))return;
        File dir=new File(app.getCacheDir(),"uav-photos");
        boolean clean=true;
        if(dir.isDirectory()){
            File[] files=dir.listFiles();
            if(files==null)clean=false;
            else for(File file:files)if(!file.delete())clean=false;
            if(clean&&!dir.delete())clean=false;
        }else if(dir.exists()&&!dir.delete())clean=false;
        if(clean){
            app.getSharedPreferences("photo_net",Context.MODE_PRIVATE).edit().clear().apply();
            app.getSharedPreferences("offline_photos",Context.MODE_PRIVATE)
                    .edit().putBoolean("legacy_purged",true).apply();
        }
    }

    private static byte[] readManifest(Context c)throws Exception{
        try(InputStream in=c.getAssets().open("bundled_photos.json")){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                out.write(buf,0,n);
                if(out.size()>500_000)throw new java.io.IOException("Photo manifest too large");
            }
            return out.toByteArray();
        }
    }
    private static boolean safePath(String value){
        return value.matches("photos/[a-z0-9][a-z0-9_-]{2,100}\\.(?i:webp|png|jpg|jpeg)");
    }
    public static synchronized List<Photo> forDrone(Context context,Drone drone){
        if(ITEMS==null){
            Map<String,List<Photo>> built=new HashMap<>();
            try{
                JSONObject root=new JSONObject(new String(readManifest(context),"UTF-8"));
                if(root.getInt("schema")!=2 ||
                   !"apk-assets-only".equals(root.getString("storage")))
                    throw new java.io.IOException("Unsupported photo manifest");
                JSONObject items=root.getJSONObject("items");
                java.util.Iterator<String> keys=items.keys();
                while(keys.hasNext()){
                    String id=keys.next();
                    JSONArray photos=items.getJSONObject(id).getJSONArray("files");
                    ArrayList<Photo> list=new ArrayList<>();
                    for(int i=0;i<photos.length()&&i<5;i++){
                        JSONObject record=photos.getJSONObject(i);
                        String file=record.optString("file","");
                        String sha=record.optString("sha256","");
                        String author=record.optString("author","");
                        String license=record.optString("license","");
                        String source=record.optString("source","");
                        if(!safePath(file)||!sha.matches("[a-f0-9]{64}") ||
                            author.isEmpty()||license.isEmpty()||source.isEmpty())continue;
                        try(InputStream check=context.getAssets().open(file)){
                            if(check.read()==-1)continue;
                        }catch(Exception missing){continue;}
                        list.add(new Photo(file,sha,author,license,source));
                    }
                    if(!list.isEmpty())built.put(id,Collections.unmodifiableList(list));
                }
            }catch(Exception e){
                android.util.Log.w("BPLA-INFO","Bundled photo manifest invalid",e);
            }
            ITEMS=Collections.unmodifiableMap(built);
        }
        if(drone==null)return Collections.emptyList();
        String id="seed-mara-2m".equals(drone.id)?"lostarmour-pdf-2024-036":drone.id;
        List<Photo> list=ITEMS.get(id);
        return list==null?Collections.emptyList():list;
    }
    private static String sha256(Context c,String path)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        long size=0;
        try(InputStream in=c.getAssets().open(path)){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1){
                size+=n;
                if(size>12_000_000L)throw new java.io.IOException("Asset image too large");
                digest.update(buffer,0,n);
            }
        }
        if(size<512)throw new java.io.IOException("Asset image too small");
        StringBuilder b=new StringBuilder(64);
        for(byte v:digest.digest())b.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
        return b.toString();
    }
    public static void load(Context context,Photo photo,int maxPixels,Callback callback){
        if(photo==null){UI.post(()->callback.ready(null));return;}
        Context app=context.getApplicationContext();
        EXECUTOR.execute(()->{
            Bitmap result=null;
            try{
                if(!photo.sha256.equals(sha256(app,photo.file)))
                    throw new java.io.IOException("Bundled photo checksum mismatch");
                BitmapFactory.Options probe=new BitmapFactory.Options();
                probe.inJustDecodeBounds=true;
                try(InputStream in=app.getAssets().open(photo.file)){
                    BitmapFactory.decodeStream(in,null,probe);
                }
                if(!PhotoDimensions.acceptable(probe.outWidth,probe.outHeight))
                    throw new java.io.IOException("Bundled photo resolution too low");
                int sample=1;
                int maximum=Math.max(800,maxPixels);
                while(Math.max(probe.outWidth,probe.outHeight)/sample>maximum)sample*=2;
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inSampleSize=sample;
                opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
                try(InputStream in=app.getAssets().open(photo.file)){
                    result=BitmapFactory.decodeStream(in,null,opts);
                }
            }catch(Exception e){
                android.util.Log.w("BPLA-INFO","Cannot decode bundled photo: "+photo.file,e);
            }catch(OutOfMemoryError e){
                android.util.Log.e("BPLA-INFO","Bundled photo exceeds memory: "+photo.file,e);
            }
            Bitmap bitmap=result;
            UI.post(()->callback.ready(bitmap));
        });
    }
    public static String unavailableMessage(Context context){
        return "ФОТО НЕДОСТУПНО В ПРИЛОЖЕНИИ";
    }
    public static void into(Context c,Photo photo,ImageView image,int px){
        into(c,photo,image,px,null);
    }
    public static void into(Context c,Photo photo,ImageView image,int px,ResultCallback status){
        if(photo==null){
            if(status!=null)status.complete(false);
            return;
        }
        image.setTag(photo.file);
        load(c,photo,px,bitmap->{
            if(photo.file.equals(image.getTag())){
                if(bitmap!=null){
                    image.clearColorFilter();
                    image.setImageBitmap(bitmap);
                    image.setContentDescription("Фотография БПЛА");
                }else image.setContentDescription(unavailableMessage(c));
                if(status!=null)status.complete(bitmap!=null);
            }
        });
    }
}
