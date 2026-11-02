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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-curated high-resolution documentary photos. Originals are fetched over
 * HTTPS and stored in a bounded cache; generated artwork is never used.
 */
public final class PhotoLibrary {
    public static final class Photo {
        public final String file, url, fallbackUrl, attributionUrl, author, license;
        public Photo(String file,String author,String license) {
            this.file=file;
            this.author=author;
            this.license=license;
            this.url=commonsUrl(file);
            this.fallbackUrl=directCommonsUrl(file);
            String encoded;
            try{encoded=URLEncoder.encode(file.replace(' ','_'),"UTF-8").replace("+","%20");}
            catch(Exception e){encoded="";}
            this.attributionUrl="https://commons.wikimedia.org/wiki/File:"+encoded;
        }
    }
    public interface Callback {void ready(Bitmap bitmap);}
    // One download at a time protects the on-disk .part files and limits Commons traffic.
    // A full-screen request should not wait behind every gallery thumbnail.
    private static final AtomicLong NEXT_TASK=new AtomicLong();
    private static final class LoadTask implements Runnable,Comparable<LoadTask> {
        final int priority;
        final long order=NEXT_TASK.getAndIncrement();
        final Runnable work;
        LoadTask(int maxPixels,Runnable work){this.priority=maxPixels>=2000?0:1;this.work=work;}
        @Override public int compareTo(LoadTask other){
            int byPriority=Integer.compare(priority,other.priority);
            return byPriority!=0?byPriority:Long.compare(order,other.order);
        }
        @Override public void run(){work.run();}
    }
    private static final ThreadPoolExecutor EXECUTOR=new ThreadPoolExecutor(
        1,1,0L,TimeUnit.MILLISECONDS,new PriorityBlockingQueue<Runnable>());
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static long lastNetworkRequest=0;
    // Shared cooldown prevents a blocked Commons host from being hit by every
    // queued thumbnail, even when the user restarts the app.
    private static volatile long nextAllowedNetworkAtMs=0L;
    private static void throttle() throws InterruptedException {
        long elapsed=System.currentTimeMillis()-lastNetworkRequest;
        if(elapsed<1600)Thread.sleep(1600-elapsed);
        lastNetworkRequest=System.currentTimeMillis();
    }
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
                            result.add(new Photo(name,photos.getJSONObject(i).optString("author",""),photos.getJSONObject(i).optString("license","")));
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
    // MediaWiki's documented redirect endpoint serves an appropriately sized
    // image and avoids downloading very large Commons originals on Android.
    private static String commonsUrl(String name){
        try{
            String canonical=name.replace(' ','_');
            String encoded=URLEncoder.encode(canonical,"UTF-8").replace("+","%20");
            return "https://commons.wikimedia.org/wiki/Special:Redirect/file/"+encoded+"?width=1600";
        }catch(Exception e){return "";}
    }
    // Retained only as a fallback for installations where MediaWiki redirect
    // is unavailable. This URL may point to a large original image.
    private static String directCommonsUrl(String name){
        try{
            String canonical=name.replace(' ','_');
            String md5=hex(MessageDigest.getInstance("MD5").digest(canonical.getBytes("UTF-8")));
            String encoded=URLEncoder.encode(canonical,"UTF-8").replace("+","%20");
            return "https://upload.wikimedia.org/wikipedia/commons/"+md5.substring(0,1)+"/"+md5.substring(0,2)+"/"+encoded;
        }catch(Exception e){return "";}
    }
    // Validate cached/downloaded bytes before accepting them. A failed or partial
    // decode must not poison subsequent offline gallery views.
    private static boolean validImage(File file){
        if(!file.isFile()||file.length()<512)return false;
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);
        return PhotoDimensions.acceptable(bounds.outWidth,bounds.outHeight);
    }
    public static void load(Context c,Photo photo,int maxPixels,Callback callback){
        if(photo==null){callback.ready(null);return;}
        Context app=c.getApplicationContext();
        EXECUTOR.execute(new LoadTask(maxPixels,()->{
            Bitmap result=null;
            try{
                File folder=new File(app.getCacheDir(),"uav-photos");
                if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("No cache directory");
                String md5=hex(MessageDigest.getInstance("MD5").digest(photo.file.getBytes("UTF-8")));
                File output=new File(folder,md5+".img");
                if(output.isFile()&&!validImage(output))output.delete();
                if(!output.isFile()){
                    long persistedCooldown=app.getSharedPreferences("photo_net",Context.MODE_PRIVATE)
                            .getLong("cooldown_until",0L);
                    nextAllowedNetworkAtMs=Math.max(nextAllowedNetworkAtMs,persistedCooldown);
                    if(System.currentTimeMillis()<nextAllowedNetworkAtMs)
                        throw new java.io.IOException("Commons cooldown active; cached photos remain available");
                    boolean complete=false;
                    // Try Commons' supported width-limited redirect first.
                    // If it fails, fall back to the traditional original URL.
                    for(String address:new String[]{photo.url,photo.fallbackUrl}){
                        if(System.currentTimeMillis()<nextAllowedNetworkAtMs)break;
                        if(address.isEmpty())continue;
                        for(int attempt=0;attempt<2&&!complete;attempt++){
                            HttpURLConnection connection=null;
                            File temp=new File(folder,md5+".part");
                            try{
                                throttle();
                                connection=(HttpURLConnection)new URL(address).openConnection();
                                connection.setConnectTimeout(12000);
                                connection.setReadTimeout(35000);
                                connection.setInstanceFollowRedirects(true);
                                connection.setRequestProperty("User-Agent","BPLAINFO/1.1 (https://github.com/INFEND/BPLA-INFO)");
                                connection.setRequestProperty("Accept","image/jpeg,image/png,image/webp,image/*;q=0.8");
                                int status=connection.getResponseCode();
                                if(CommonsRetryPolicy.isHostCooldownStatus(status)){
                                    // Respect Retry-After globally, not just for this
                                    // image. HTTP 403 may also mean Wikimedia has temporarily
                                    // denied this client. Never switch to the original URL during
                                    // a host cooldown: that doubles the blocked traffic.
                                    String header=connection.getHeaderField("Retry-After");
                                    long until=CommonsRetryPolicy.nextAllowedMillis(
                                            status,header,System.currentTimeMillis());
                                    nextAllowedNetworkAtMs=Math.max(nextAllowedNetworkAtMs,until);
                                    app.getSharedPreferences("photo_net",Context.MODE_PRIVATE)
                                            .edit().putLong("cooldown_until",nextAllowedNetworkAtMs).apply();
                                    android.util.Log.w("BPLA-INFO","Commons HTTP "+status+
                                            "; retry after cooldown (header="+header+")");
                                    break;
                                }
                                if(status!=200)break;
                                String type=connection.getContentType();
                                if(type==null||!type.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))
                                    throw new java.io.IOException("Response is not an image");
                                long announced=connection.getContentLengthLong();
                                if(announced>15_000_000L)throw new java.io.IOException("Image too large");
                                long size=0;
                                try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(temp)){
                                    byte[] buffer=new byte[32768];int n;
                                    while((n=in.read(buffer))!=-1){
                                        size+=n;
                                        if(size>15_000_000L)throw new java.io.IOException("Image too large");
                                        out.write(buffer,0,n);
                                    }
                                }
                                if(size<512||!validImage(temp)||!temp.renameTo(output))
                                    throw new java.io.IOException("Invalid image response");
                                complete=true;
                            }catch(java.io.IOException e){
                                android.util.Log.w("BPLA-INFO","Photo request failed: "+photo.file+" ("+e.getMessage()+")");
                            }finally{
                                if(connection!=null)connection.disconnect();
                                if(!complete)temp.delete();
                            }
                        }
                        if(complete||System.currentTimeMillis()<nextAllowedNetworkAtMs)break;
                    }
                    if(!complete)throw new java.io.IOException("All Commons image endpoints unavailable");
                    trim(folder);
                }
                BitmapFactory.Options probe=new BitmapFactory.Options();
                probe.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(output.getAbsolutePath(),probe);
                if(!PhotoDimensions.acceptable(probe.outWidth,probe.outHeight)){
                    output.delete();throw new java.io.IOException("Photo resolution too low");
                }
                int sample=1;
                while(Math.max(probe.outWidth,probe.outHeight)/sample>Math.max(maxPixels,800))sample*=2;
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
                result=BitmapFactory.decodeFile(output.getAbsolutePath(),opts);
                if(result==null){
                    output.delete();
                    throw new java.io.IOException("Cached photo could not be decoded");
                }
                output.setLastModified(System.currentTimeMillis());
            }catch(Exception e){
                android.util.Log.d("BPLA-INFO","Photo not available: "+photo.file+" "+e.getMessage());
            }
            Bitmap finalResult=result;
            UI.post(()->callback.ready(finalResult));
        }));
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
    /**
     * Explain a server-enforced cooldown without making another network request.
     * Previously cached images can still be viewed during this interval.
     */
    public static String unavailableMessage(Context context){
        long persisted=context.getApplicationContext()
                .getSharedPreferences("photo_net",Context.MODE_PRIVATE)
                .getLong("cooldown_until",0L);
        long until=Math.max(nextAllowedNetworkAtMs,persisted);
        long remaining=Math.max(0L,(until-System.currentTimeMillis()+999L)/1000L);
        if(remaining>0L)
            return "WIKIMEDIA: ПАУЗА "+((remaining+59L)/60L)+" МИН · КЭШ ДОСТУПЕН";
        return "ФОТО НЕДОСТУПНО • НАЖМИТЕ ДЛЯ ПОВТОРА";
    }
    public interface ResultCallback {void complete(boolean success);}
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
                }else{
                    image.setContentDescription(unavailableMessage(c));
                }
                if(status!=null)status.complete(bitmap!=null);
            }
        });
    }
}
