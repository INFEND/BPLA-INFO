package dev.infend.bplainfo;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Crash-loop circuit breaker. A failed migration must never force users
 * to clear app storage, losing gallery URIs and saved catalog changes.
 */
public final class StartupGuard {
    private static final String PENDING = "startup_catalog_pending_v2";
    private static final String REPORT = "last_startup_error.txt";
    private static boolean handlerInstalled;
    private StartupGuard() {}

    private static File marker(Context c) {
        return new File(c.getNoBackupFilesDir(), PENDING);
    }

    public static boolean wasInterrupted(Context c) {
        return marker(c).isFile();
    }

    public static void begin(Context c) {
        try(FileOutputStream output=new FileOutputStream(marker(c))) {
            output.write("catalog-migration-in-progress".getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }catch(Exception e){
            android.util.Log.w("BPLA-INFO","Unable to persist startup guard",e);
        }
    }

    public static void completed(Context c) {
        File pending=marker(c);
        if(pending.exists()&&!pending.delete())
            android.util.Log.w("BPLA-INFO","Unable to clear startup marker");
    }

    public static void capture(Context c,String stage,Throwable failure) {
        try {
            StringWriter stack=new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            String text="БПЛА ИНФО · АВАРИЙНЫЙ ОТЧЁТ\n"+
                "Этап: "+stage+"\nТип: "+failure.getClass().getName()+
                "\nВерсия: 1.0.22\n"+stack;
            if(text.length()>12000)text=text.substring(0,12000)+"\n[Отчёт сокращён]";
            try(FileOutputStream output=new FileOutputStream(new File(c.getFilesDir(),REPORT))) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
        }catch(Throwable ignored) {
            // Never let diagnostic reporting itself cause a second fatal crash.
        }
    }

    public static String report(Context c) {
        try {
            File file=new File(c.getFilesDir(),REPORT);
            if(!file.isFile()||file.length()>24000)return "Отчёт отсутствует.";
            byte[] buf=new byte[(int)file.length()];
            try(java.io.FileInputStream input=new java.io.FileInputStream(file)){
                int filled=0,n;
                while(filled<buf.length&&(n=input.read(buf,filled,buf.length-filled))>0)filled+=n;
                return new String(buf,0,filled,StandardCharsets.UTF_8);
            }
        }catch(Exception e){return "Чтение отчёта недоступно.";}
    }

    public static synchronized void installFatalRecorder(Context c) {
        if(handlerInstalled)return;
        final Context app=c.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            capture(app,"Необработанное исключение: "+thread.getName(),error);
            if(previous!=null)previous.uncaughtException(thread,error);
        });
        handlerInstalled=true;
    }
}
