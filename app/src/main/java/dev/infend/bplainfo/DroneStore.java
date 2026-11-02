package dev.infend.bplainfo;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
public final class DroneStore {
 private static final String PREFS="bpla_store",KEY="catalog_v1";
 public static ArrayList<Drone> load(Context c){String raw=c.getSharedPreferences(PREFS,0).getString(KEY,null);ArrayList<Drone> out=new ArrayList<>();if(raw==null){out.add(Drone.mara());mergeSeed(c,out);save(c,out);return out;}try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length()&&i<5000;i++){JSONObject j=a.optJSONObject(i);if(j!=null)out.add(Drone.fromJson(j));}}catch(Exception e){out.add(Drone.mara());}mergeSeed(c,out);return out;}
 private static final int SEED_REVISION=1;
 private static void mergeSeed(Context c,ArrayList<Drone> drones){
   android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
   if(prefs.getInt("seed_revision",0)>=SEED_REVISION)return;
   try(java.io.InputStream input=c.getAssets().open("catalog_lostarmour_2024_verified.json")){
     byte[] buf=new byte[8192];int n;
     java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
     while((n=input.read(buf))!=-1){
       out.write(buf,0,n);
       if(out.size()>2_000_000)throw new java.io.IOException("Catalogue too large");
     }
     JSONObject root=new JSONObject(out.toString("UTF-8"));
     JSONArray entries=root.getJSONArray("entries");
     if(entries.length()<1)throw new org.json.JSONException("Empty catalogue");
     for(int i=0;i<entries.length();i++){
       JSONObject item=entries.getJSONObject(i);
       Drone d=Drone.fromJson(item);
       if(d.name().isEmpty()||d.specRows.isEmpty())continue;
       for(String[] row:d.specRows){
         if(row.length<2)continue;
         String label=row[0].toUpperCase(java.util.Locale.ROOT),value=row[1];
         if(label.equals("ПРОИЗВОДИТЕЛЬ"))d.set(2,value);
         if(label.equals("РАЗМАХ КРЫЛЬЕВ"))d.set(3,value);
         if(label.equals("ДЛИНА ФЮЗЕЛЯЖА"))d.set(4,value);
         if(label.equals("МАКСИМАЛЬНАЯ СКОРОСТЬ"))d.set(5,value);
         if(label.equals("ДАЛЬНОСТЬ ПОЛЕТА")||label.equals("РАДИУС ДЕЙСТВИЯ"))d.set(6,value);
         if(label.equals("РАБОЧАЯ ВЫСОТА"))d.set(7,value);
         if(label.equals("ПРАКТИЧЕСКИЙ ПОТОЛОК"))d.set(8,value);
         if(label.equals("ПОЛЕЗНАЯ НАГРУЗКА"))d.set(9,value);
         if(label.equals("ВЗЛЕТНАЯ МАССА"))d.set(10,value);
         if(label.equals("АВТОНОМНОСТЬ"))d.set(11,value);
         if(label.equals("ТИП ЗАПУСКА"))d.set(12,value);
         if(label.equals("ТИП ДВИГАТЕЛЯ"))d.set(13,value);
       }
       boolean exists=false;
       for(Drone existing:drones){
         if(existing.id.equals(d.id)||existing.name().equalsIgnoreCase(d.name())){
           exists=true;
           if(existing.specRows.isEmpty()&&existing.catalogSource.isEmpty()){
             existing.specRows.addAll(d.specRows);
             existing.catalogSource=d.catalogSource;
           }
           break;
         }
       }
       if(!exists)drones.add(d);
     }
     save(c,drones);
     prefs.edit().putInt("seed_revision",SEED_REVISION).apply();
   }catch(Exception e){
     android.util.Log.w("BPLA-INFO","Bundled catalog import skipped: "+e.getClass().getSimpleName());
   }
 }
 public static void save(Context c,List<Drone> list){JSONArray a=new JSONArray();for(Drone d:list)try{a.put(d.json());}catch(Exception ignored){}c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
 public static String exportJson(List<Drone> list)throws Exception{JSONObject o=new JSONObject();o.put("schema",1);o.put("app","БПЛА ИНФО");JSONArray a=new JSONArray();for(Drone d:list)a.put(d.json());o.put("items",a);return o.toString(2);}
 public static int importJson(Context c,List<Drone> list,String input)throws Exception{JSONObject o=new JSONObject(input);if(o.optInt("schema")!=1)throw new IllegalArgumentException("Неподдерживаемый формат");JSONArray a=o.getJSONArray("items");if(a.length()>5000)throw new IllegalArgumentException("Слишком много карточек");int n=0;for(int i=0;i<a.length();i++){Drone d=Drone.fromJson(a.getJSONObject(i));if(d.name().isEmpty())continue;int index=-1;for(int j=0;j<list.size();j++)if(list.get(j).id.equals(d.id)){index=j;break;}if(index<0)list.add(d);else list.set(index,d);n++;}save(c,list);return n;}
}
