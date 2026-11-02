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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hand-curated high-resolution documentary photos. Originals are fetched over
 * HTTPS and stored in a bounded cache; generated artwork is never used.
 */
public final class PhotoLibrary {
    public static final class Photo {
        public final String file, url, attributionUrl;
        public Photo(String file) {
            this.file=file;
            this.url=commonsUrl(file);
            String encoded;
            try{encoded=URLEncoder.encode(file.replace(' ','_'),"UTF-8").replace("+","%20");}
            catch(Exception e){encoded="";}
            this.attributionUrl="https://commons.wikimedia.org/wiki/File:"+encoded;
        }
    }
    public interface Callback {void ready(Bitmap bitmap);}
    private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(3);
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static Map<String,List<Photo>> ITEMS;
    private PhotoLibrary(){}
    public static synchronized List<Photo> forDrone(Context c,Drone d){
        if(ITEMS==null){
            ITEMS=new HashMap<>();
            try(InputStream input=c.getAssets().open("photo_sources.json")){
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){
                    output.write(buffer,0,n);
                    if(output.size()>300_000)throw new java.io.IOException("Invalid photo manifest size");
                }
                JSONObject root=new JSONObject(output.toString("UTF-8"));
                JSONObject items=root.getJSONObject("items");
                java.util.Iterator<String> iter=items.keys();
                while(iter.hasNext()){
                    String id=iter.next();
                    JSONArray photos=items.getJSONObject(id).getJSONArray("files");
                    List<Photo> result=new ArrayList<>();
                    for(int i=0;i<photos.length()&&i<5;i++){
                        String name=photos.getJSONObject(i).optString("file","");
                        if(name.matches("[^/\\\\]{5,200}\\.(?i:jpg|jpeg|png|webp)"))
                            result.add(new Photo(name));
                    }
                    if(!result.isEmpty())ITEMS.put(id,Collections.unmodifiableList(result));
                }
            }catch(Exception e){android.util.Log.w("BPLA-INFO","Photo catalog unavailable",e);}
        }
        if(d==null)return Collections.emptyList();
        String id=d.id;
        if("seed-mara-2m".equals(id))id="lostarmour-pdf-2024-036";
        List<Photo> result=ITEMS.get(id);
        return result==null?Collections.emptyList():result;
    }
    private static String hex(byte[] bytes){
        StringBuilder b=new StringBuilder();
        for(byte x:bytes)b.append(String.format(java.util.Locale.ROOT,"%02x",x&255));
        return b.toString();
    }
    private static String commonsUrl(String name){
        try{
            String canonical=name.replace(' ','_');
            String md5=hex(MessageDigest.getInstance("MD5").digest(canonical.getBytes("UTF-8")));
            String encoded=URLEncoder.encode(canonical,"UTF-8").replace("+","%20");
            return "https://upload.wikimedia.org/wikipedia/commons/"+md5.substring(0,1)+"/"+md5.substring(0,2)+"/"+encoded;
        }catch(Exception e){return "";}
    }
    public static void load(Context c,Photo photo,int maxPixels,Callback callback){
        if(photo==null){callback.ready(null);return;}
        Context app=c.getApplicationContext();
        EXECUTOR.execute(()->{
            Bitmap result=null;
            try{
                File folder=new File(app.getCacheDir(),"uav-photos");
                if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("No cache directory");
                String md5=hex(MessageDigest.getInstance("MD5").digest(photo.file.getBytes("UTF-8")));
                File output=new File(folder,md5+".img");
                if(!output.isFile()||output.length()<512){
                    URL url=new URL(photo.url);
                    HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(25000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent","BPLAINFO/1.0 (illustrated UAV reference)");
                    try{
                        int status=connection.getResponseCode();
                        if(status!=200)throw new java.io.IOException("Image HTTP "+status);
                        String type=connection.getContentType();
                        if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))throw new java.io.IOException("Not an image");
                        File temp=new File(folder,md5+".part");
                        long size=0;
                        try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                            byte[] buffer=new byte[32768];int n;
                            while((n=in.read(buffer))!=-1){
                                size+=n;if(size>15_000_000L)throw new java.io.IOException("Image too large");
                                out.write(buffer,0,n);
                            }
                        }
                        if(size<512||!temp.renameTo(output)){
                            temp.delete();throw new java.io.IOException("Invalid image");
                        }
                    }finally{connection.disconnect();}
                    trim(folder);
                }
                BitmapFactory.Options probe=new BitmapFactory.Options();
                probe.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(output.getAbsolutePath(),probe);
                if(probe.outWidth<500||probe.outHeight<300){
                    output.delete();throw new java.io.IOException("Photo resolution too low");
                }
                int sample=1;
                while(Math.max(probe.outWidth,probe.outHeight)/sample>Math.max(maxPixels,800))sample*=2;
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.RGB_565;
                result=BitmapFactory.decodeFile(output.getAbsolutePath(),opts);
                output.setLastModified(System.currentTimeMillis());
            }catch(Exception e){
                android.util.Log.d("BPLA-INFO","Photo not available: "+photo.file+" "+e.getMessage());
            }
            Bitmap finalResult=result;
            UI.post(()->callback.ready(finalResult));
        });
    }
    private static void trim(File dir){
        File[] files=dir.listFiles(f->f.getName().endsWith(".img"));
        if(files==null)return;
        long total=0;
        for(File f:files)total+=f.length();
        if(total<=90_000_000L)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(File f:files){
            if(total<=70_000_000L)break;
            long size=f.length();
            if(f.delete())total-=size;
        }
    }
    public static void into(Context c,Photo photo,ImageView image,int px){
        image.setTag(photo.file);
        load(c,photo,px,bitmap->{
            if(bitmap!=null&&photo.file.equals(image.getTag()))image.setImageBitmap(bitmap);
        });
    }
}
