package dev.infend.bplainfo;

import android.app.Activity;
import android.app.AlertDialog;
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
    private void error(String message){ui(()->new AlertDialog.Builder(activity).setTitle("OTA · Ошибка").setMessage(message).setPositiveButton("ОК",null).show());}
    public void check(boolean silent){
        new Thread(()->{
            try(InputStream in=open(MANIFEST_URL)){
                JSONObject j=new JSONObject(new String(readLimited(in,512*1024),java.nio.charset.StandardCharsets.UTF_8));
                if(!activity.getPackageName().equals(j.getString("packageName")))throw new SecurityException("Неверный пакет приложения");
                int remote=j.getInt("versionCode");
                PackageInfo installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
                long local=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
                if(remote<=local){
                    if(!silent)ui(()->new AlertDialog.Builder(activity).setTitle("БПЛА ИНФО").setMessage("Установлена актуальная версия. Обновлений нет.").setPositiveButton("ГОТОВО",null).show());
                    return;
                }
                version=j;
                ui(()->new AlertDialog.Builder(activity)
                    .setTitle("Доступно обновление "+j.optString("versionName"))
                    .setMessage("Изменения:\n"+j.optString("notes","Без описания"))
                    .setNegativeButton("ПОЗЖЕ",null)
                    .setPositiveButton("ОБНОВИТЬ",(d,w)->download()).show());
            }catch(Exception e){if(!silent)error("Не удалось проверить релиз: "+e.getMessage());}
        },"ota-manifest").start();
    }
    private void download(){
        if(version==null)return;
        String url=version.optString("apkUrl","");
        String sha=version.optString("sha256","");
        long expectedBytes=version.optLong("sizeBytes",-1);
        if(!url.startsWith("https://")||!sha.matches("[a-fA-F0-9]{64}")||expectedBytes<=0){
            error("Некорректный подписанный манифест обновления");return;
        }
        cancelled=false;
        LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(35,25,35,10);
        TextView label=new TextView(activity);label.setText("Соединение с сервером…");label.setTextSize(14);box.addView(label);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);box.addView(bar,new LinearLayout.LayoutParams(-1,55));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Загрузка обновления")
            .setView(box).setNegativeButton("ОТМЕНА",(d,w)->cancelled=true).create();
        dialog.setCancelable(false);dialog.show();
        new Thread(()->{
            File tmp=new File(activity.getCacheDir(),"ota/BPLA-INFO.part");
            File dst=new File(activity.getCacheDir(),"ota/BPLA-INFO.apk");
            try{
                if(!tmp.getParentFile().isDirectory()&&!tmp.getParentFile().mkdirs())throw new IOException("Нет каталога кэша");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                long total=0,at=0;
                try(InputStream in=open(url);OutputStream out=new FileOutputStream(tmp)){
                    byte[] buf=new byte[64*1024];int n;
                    while((n=in.read(buf))!=-1){
                        if(cancelled)throw new InterruptedIOException("Отменено");
                        out.write(buf,0,n);digest.update(buf,0,n);total+=n;
                        if(total>expectedBytes||total>300_000_000L)throw new IOException("Размер APK превышает ожидаемый");
                        int percent=(int)Math.min(100,(total*100)/expectedBytes);
                        if(percent!=at){at=percent;int shown=percent;ui(()->{bar.setProgress(shown);label.setText("Загружено "+shown+"%");});}
                    }
                }
                if(cancelled)throw new InterruptedIOException("Отменено");
                if(total!=expectedBytes)throw new SecurityException("Размер APK не совпадает");
                if(!hex(digest.digest()).equalsIgnoreCase(sha))throw new SecurityException("SHA-256 APK не совпадает с манифестом");
                verifySigner(tmp,version.optString("certificateSha256",""));
                if(dst.exists()&&!dst.delete())throw new IOException("Не удалось заменить старый файл");
                if(!tmp.renameTo(dst))throw new IOException("Не удалось сохранить APK");
                ui(()->{dialog.dismiss();install();});
            }catch(Exception e){
                tmp.delete();
                ui(()->{dialog.dismiss();if(!(e instanceof InterruptedIOException))error(e.getMessage());});
            }
        },"ota-download").start();
    }
    private void verifySigner(File apk,String manifestCert) throws Exception{
        PackageManager pm=activity.getPackageManager();
        PackageInfo current=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(archive==null||!activity.getPackageName().equals(archive.packageName)||archive.signingInfo==null||current.signingInfo==null)
            throw new SecurityException("Невозможно проверить подпись APK");
        long targetVersion=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        long installedVersion=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode;
        if(targetVersion<=installedVersion||targetVersion!=version.getInt("versionCode"))
            throw new SecurityException("Некорректная версия обновления");
        Signature[] existing=current.signingInfo.getApkContentsSigners();
        Signature[] incoming=archive.signingInfo.getApkContentsSigners();
        if(existing.length!=1||incoming.length!=1||!existing[0].equals(incoming[0]))
            throw new SecurityException("Подпись APK отличается от установленной");
        String cert=hex(MessageDigest.getInstance("SHA-256").digest(incoming[0].toByteArray()));
        if(!manifestCert.isEmpty()&&!cert.equalsIgnoreCase(manifestCert))throw new SecurityException("Сертификат APK не совпадает с манифестом");
    }
    private void install(){
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            activity.getPreferences(0).edit().putBoolean("ota_pending_install",true).apply();
            new AlertDialog.Builder(activity).setTitle("Разрешение установки")
                .setMessage("Android требует один раз разрешить установку обновлений из БПЛА ИНФО. После возврата установка продолжится автоматически.")
                .setNegativeButton("ПОЗЖЕ",null).setPositiveButton("ОТКРЫТЬ НАСТРОЙКИ",(d,w)->{
                    Intent intent=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()));
                    activity.startActivity(intent);
                }).show();
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
