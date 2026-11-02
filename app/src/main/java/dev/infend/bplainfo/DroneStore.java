package dev.infend.bplainfo;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
public final class DroneStore {
 private static final String PREFS="bpla_store",KEY="catalog_v1";
 public static ArrayList<Drone> load(Context c){String raw=c.getSharedPreferences(PREFS,0).getString(KEY,null);ArrayList<Drone> out=new ArrayList<>();if(raw==null){out.add(Drone.mara());mergeSeed(c,out);mergeEnrichment(c,out);save(c,out);return out;}try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length()&&i<5000;i++){JSONObject j=a.optJSONObject(i);if(j!=null)out.add(Drone.fromJson(j));}}catch(Exception e){out.add(Drone.mara());}mergeSeed(c,out);mergeEnrichment(c,out);return out;}
 private static final int SEED_REVISION=2;
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
           if(existing.sourceGroup.isEmpty()&&!d.sourceGroup.isEmpty())existing.sourceGroup=d.sourceGroup;
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
 private static void mergeEnrichment(Context c,ArrayList<Drone> drones){
   android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
   final int revision=3;
   if(prefs.getInt("enrichment_revision",0)>=revision)return;
   try(java.io.InputStream in=c.getAssets().open("spec_enrichment_2026.json")){
     java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
     byte[] buf=new byte[4096];int n;
     while((n=in.read(buf))!=-1){
       output.write(buf,0,n);
       if(output.size()>200_000)throw new java.io.IOException("Research file too large");
     }
     JSONObject root=new JSONObject(output.toString("UTF-8"));
     JSONArray entries=root.getJSONArray("entries");
     if(entries.length()==0)throw new java.io.IOException("No supplementary records");
     boolean modified=false;
     for(int i=0;i<entries.length();i++){
       JSONObject addition=entries.getJSONObject(i);
       String id=addition.optString("id");
       String model=addition.optString("name");
       if(id.isEmpty()||model.isEmpty())continue;
       Drone original=null;
       for(Drone drone:drones){
         if(drone.id.equals(id)&&drone.name().equalsIgnoreCase(model)){original=drone;break;}
       }
       if(original==null)continue;
       JSONArray fields=addition.optJSONArray("fields");
       if(fields==null)continue;
       for(int j=0;j<fields.length();j++){
         JSONArray row=fields.optJSONArray(j);
         if(row==null||row.length()!=2)continue;
         String key=row.optString(0,"").trim(),value=row.optString(1,"").trim();
         if(key.isEmpty()||value.isEmpty())continue;
         boolean already=false;
         for(String[] spec:original.specRows){
           if(spec.length>=2&&spec[0].equalsIgnoreCase(key)){already=true;break;}
         }
         if(already)continue;
         int insert=original.specRows.size();
         if(key.equals("МАКСИМАЛЬНАЯ СКОРОСТЬ")){
           for(int k=0;k<original.specRows.size();k++){
             if(original.specRows.get(k)[0].contains("СКОРОСТЬ")){insert=k;break;}
           }
         }else if(key.equals("ПРОИЗВОДИТЕЛЬ")){
           insert=original.specRows.size();
         }
         original.specRows.add(insert,new String[]{key,value});
         if(key.equals("МАКСИМАЛЬНАЯ СКОРОСТЬ")&&original.get(5).isEmpty())original.set(5,value);
         if(key.equals("ПРОИЗВОДИТЕЛЬ")&&original.get(2).isEmpty())original.set(2,value);
         modified=true;
       }
     }
     if(modified)save(c,drones);
     prefs.edit().putInt("enrichment_revision",revision).apply();
   }catch(Exception e){
     android.util.Log.w("BPLA-INFO","Verified catalogue additions unavailable: "+e.getClass().getSimpleName());
   }
 }
 public static void save(Context c,List<Drone> list){JSONArray a=new JSONArray();for(Drone d:list)try{a.put(d.json());}catch(Exception ignored){}c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
 public static String exportJson(List<Drone> list)throws Exception{JSONObject o=new JSONObject();o.put("schema",1);o.put("app","БПЛА ИНФО");JSONArray a=new JSONArray();for(Drone d:list)a.put(d.json());o.put("items",a);return o.toString(2);}
 public static int importJson(Context c,List<Drone> list,String input)throws Exception{JSONObject o=new JSONObject(input);if(o.optInt("schema")!=1)throw new IllegalArgumentException("Неподдерживаемый формат");JSONArray a=o.getJSONArray("items");if(a.length()>5000)throw new IllegalArgumentException("Слишком много карточек");int n=0;for(int i=0;i<a.length();i++){Drone d=Drone.fromJson(a.getJSONObject(i));if(d.name().isEmpty())continue;int index=-1;for(int j=0;j<list.size();j++)if(list.get(j).id.equals(d.id)){index=j;break;}if(index<0)list.add(d);else list.set(index,d);n++;}save(c,list);return n;}
}
