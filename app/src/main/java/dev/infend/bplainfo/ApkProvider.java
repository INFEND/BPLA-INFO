package dev.infend.bplainfo;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
public final class ApkProvider extends ContentProvider {
 public static final Uri URI=Uri.parse("content://dev.infend.bplainfo.apk/update.apk");
 @Override public boolean onCreate(){return true;}
 private File checked(Uri u)throws FileNotFoundException{if(!URI.equals(u))throw new FileNotFoundException("Unknown URI");File f=new File(getContext().getCacheDir(),"ota/BPLA-INFO.apk");if(!f.isFile())throw new FileNotFoundException("Missing APK");return f;}
 @Override public String getType(Uri uri){return URI.equals(uri)?"application/vnd.android.package-archive":null;}
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){try{File f=checked(uri);String[] col=projection==null?new String[]{"_display_name","_size"}:projection;MatrixCursor m=new MatrixCursor(col);Object[] v=new Object[col.length];for(int i=0;i<col.length;i++){if(col[i].equals("_display_name"))v[i]="BPLA-INFO.apk";if(col[i].equals("_size"))v[i]=f.length();}m.addRow(v);return m;}catch(Exception e){return null;}}
 @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(checked(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
 @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
 @Override public int delete(Uri u,String s,String[] a){return 0;}
}
