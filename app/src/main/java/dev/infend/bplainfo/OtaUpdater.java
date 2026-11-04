package dev.infend.bplainfo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;

public final class OtaUpdater {
    public static final String MANIFEST_URL="https://github.com/INFEND/BPLA-INFO/releases/latest/download/update.json";
    private final Activity activity;
    private volatile boolean cancelled;
    private JSONObject version;
    public OtaUpdater(Activity a){activity=a;}

    private static String hex(byte[] b) {
        StringBuilder s=new StringBuilder();
        for(byte x:b)s.append(String.format(Locale.ROOT,"%02x",x&0xff));
        return s.toString();
    }
    private static InputStream open(String source) throws Exception {
        URL current=new URL(source);
        for(int hops=0;hops<8;hops++){
            if(!"https".equalsIgnoreCase(current.getProtocol()))throw new IOException("Разрешён только HTTPS");
            HttpURLConnection c=(HttpURLConnection)current.openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","BPLA-INFO-Android");
            int response=c.getResponseCode();
            if(response==301||response==302||response==303||response==307||response==308) {
                String loc=c.getHeaderField("Location");c.disconnect();
                if(loc==null)throw new IOException("Нет адреса перенаправления");
                current=new URL(current,loc);continue;
            }
            if(response!=200){c.disconnect();throw new IOException("HTTP "+response);}
            return new java.io.FilterInputStream(c.getInputStream()){public void close()throws IOException{super.close();c.disconnect();}};
        }
        throw new IOException("Слишком много перенаправлений");
    }
    private static byte[] readLimited(InputStream in,int max) throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>max)throw new IOException("Слишком большой ответ сервера");}
        return out.toByteArray();
    }
    private void ui(Runnable run){activity.runOnUiThread(()->{if(!activity.isFinishing())run.run();});}
    private void error(String message){ui(()->UiDialogs.message(activity,new ThemePrefs(activity),"Ошибка обновления",message));}
    /** Recover every missed version from the trusted release manifest. */
    private static String notesFor(JSONObject manifest,long installed) {
        String fallback=manifest.optString("notes","");
        JSONArray history=manifest.optJSONArray("history");
        if(history==null||history.length()>1000)return ReleaseNotes.visible(fallback);
        ArrayList<OtaHistory.Entry> entries=new ArrayList<>();
        for(int i=0;i<history.length();i++){
            JSONObject item=history.optJSONObject(i);
            if(item==null)return ReleaseNotes.visible(fallback);
            entries.add(new OtaHistory.Entry(item.optLong("versionCode",0),
                item.optString("versionName",""),item.optString("notes","")));
        }
        return OtaHistory.merge(entries,installed,manifest.optLong("versionCode",0),fallback);
    }
    private OtaProgressDialog progress;
    private void ensureProgress(){
        if(progress==null)progress=new OtaProgressDialog(activity);
        progress.show();
    }
    public void check(boolean silent){
        if(!silent)ui(()->{ensureProgress();progress.stage("Проверка обновлений","Подключение к серверу…");});
        new Thread(()->{
            try(InputStream in=open(MANIFEST_URL)){
                JSONObject j=new JSONObject(new String(readLimited(in,512*1024),java.nio.charset.StandardCharsets.UTF_8));
                if(!activity.getPackageName().equals(j.getString("packageName")))throw new SecurityException("Неверный пакет приложения");
                int remote=j.getInt("versionCode");
                PackageInfo installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
                long local=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
                if(remote<=local){
                    if(!silent){
                        String complete=notesFor(j,0);
                        ui(()->{ensureProgress();progress.current(installed.versionName,complete);});
                    }
                    return;
                }
                String changes=notesFor(j,local);
                version=j;
                ui(()->{
                    ensureProgress();
                    progress.offer(j.optString("versionName"),changes,this::download);
                });
            }catch(Exception e){
                if(!silent)ui(()->{ensureProgress();progress.fail("Не удалось проверить обновления: "+e.getMessage(),()->check(false));});
            }
        },"bpla-ota-check").start();
    }
    private void download(){
        if(version==null)return;
        String url=version.optString("apkUrl","");
        String sha=version.optString("sha256","");
        long expectedBytes=version.optLong("sizeBytes",-1);
        if(!url.startsWith("https://")||!sha.matches("[a-fA-F0-9]{64}")||expectedBytes<=0||expectedBytes>300_000_000L){
            ui(()->{ensureProgress();progress.fail("Некорректный манифест обновления",this::download);});
            return;
        }
        cancelled=false;
        ui(()->{ensureProgress();progress.stage("Подключение к серверу…","Подготовка безопасной загрузки");});
        new Thread(()->{
            File tmp=new File(activity.getCacheDir(),"ota/BPLA-INFO.part");
            File dst=new File(activity.getCacheDir(),"ota/BPLA-INFO.apk");
            try{
                if(!tmp.getParentFile().isDirectory()&&!tmp.getParentFile().mkdirs())throw new IOException("Нет каталога кэша");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                long total=0,lastPercent=-1;
                try(InputStream in=open(url);OutputStream out=new FileOutputStream(tmp)){
                    byte[] buf=new byte[64*1024];int n;
                    while((n=in.read(buf))!=-1){
                        if(cancelled)throw new InterruptedIOException("Отменено");
                        out.write(buf,0,n);digest.update(buf,0,n);total+=n;
                        if(total>expectedBytes)throw new IOException("Размер APK превышает ожидаемый");
                        int percent=(int)Math.min(100,total*100L/expectedBytes);
                        if(percent!=lastPercent){
                            lastPercent=percent;long received=total;
                            ui(()->{if(progress!=null)progress.downloading(received,expectedBytes);});
                        }
                    }
                }
                if(cancelled)throw new InterruptedIOException("Отменено");
                ui(()->{if(progress!=null)progress.stage("Проверка загруженного APK","Целостность, сертификат и версия");});
                if(total!=expectedBytes)throw new SecurityException("Размер APK не совпадает");
                if(!hex(digest.digest()).equalsIgnoreCase(sha))throw new SecurityException("SHA-256 APK не совпадает с манифестом");
                verifySigner(tmp,version.optString("certificateSha256",""));
                if(dst.exists()&&!dst.delete())throw new IOException("Не удалось заменить старый файл");
                if(!tmp.renameTo(dst))throw new IOException("Не удалось сохранить APK");
                ui(()->{if(progress!=null)progress.dismiss();install();});
            }catch(Exception e){
                tmp.delete();
                if(!(e instanceof InterruptedIOException))ui(()->{
                    ensureProgress();progress.fail("Загрузка не выполнена: "+e.getMessage(),this::download);
                });
            }
        },"bpla-ota-download").start();
    }
    private void verifySigner(File apk,String manifestCert) throws Exception{
        PackageManager pm=activity.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo current=pm.getPackageInfo(activity.getPackageName(),flags);
        PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
        if(archive==null||!activity.getPackageName().equals(archive.packageName))
            throw new SecurityException("Невозможно проверить пакет APK");
        long targetVersion=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        long installedVersion=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode;
        if(targetVersion<=installedVersion||targetVersion!=version.getInt("versionCode"))
            throw new SecurityException("Некорректная версия обновления");
        Signature[] existing;
        Signature[] incoming;
        if(Build.VERSION.SDK_INT>=28){
            if(current.signingInfo==null||archive.signingInfo==null)
                throw new SecurityException("Сертификат APK недоступен");
            existing=current.signingInfo.getApkContentsSigners();
            incoming=archive.signingInfo.getApkContentsSigners();
        } else {
            existing=current.signatures;
            incoming=archive.signatures;
        }
        if(existing==null||incoming==null||existing.length!=1||incoming.length!=1||!existing[0].equals(incoming[0]))
            throw new SecurityException("Подпись APK отличается от установленной");
        String cert=hex(MessageDigest.getInstance("SHA-256").digest(incoming[0].toByteArray()));
        if(!manifestCert.isEmpty()&&!cert.equalsIgnoreCase(manifestCert))throw new SecurityException("Сертификат APK не совпадает с манифестом");
    }
    private void install(){
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            activity.getPreferences(0).edit().putBoolean("ota_pending_install",true).apply();
            UiDialogs.confirmation(activity,new ThemePrefs(activity),"Разрешение установки",
                "Разреши установку обновлений для БПЛА ИНФО. После возвращения установка продолжится автоматически.",
                "НАСТРОЙКИ",()->{
                    Intent intent=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+activity.getPackageName()));
                    activity.startActivity(intent);
                });
            return;
        }
        activity.getPreferences(0).edit().putBoolean("ota_pending_install",false).apply();
        Intent i=new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.URI,"application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(i);}
        catch(Exception e){error("Не удалось открыть системный установщик: "+e.getMessage());}
    }
    public static void resumePending(Activity a){
        if(!a.getPreferences(0).getBoolean("ota_pending_install",false))return;
        if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls())return;
        File downloaded=new File(a.getCacheDir(),"ota/BPLA-INFO.apk");
        if(!downloaded.isFile()){a.getPreferences(0).edit().putBoolean("ota_pending_install",false).apply();return;}
        a.getPreferences(0).edit().putBoolean("ota_pending_install",false).apply();
        Intent i=new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.URI,"application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{a.startActivity(i);}catch(Exception ignored){}
    }
}
