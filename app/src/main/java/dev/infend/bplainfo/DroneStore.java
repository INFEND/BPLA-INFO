package dev.infend.bplainfo;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
public final class DroneStore {
 private static final String PREFS="bpla_store",KEY="catalog_v1";
 public static ArrayList<Drone> load(Context c){String raw=c.getSharedPreferences(PREFS,0).getString(KEY,null);ArrayList<Drone> out=new ArrayList<>();if(raw==null){out.add(Drone.mara());save(c,out);return out;}try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length()&&i<5000;i++){JSONObject j=a.optJSONObject(i);if(j!=null)out.add(Drone.fromJson(j));}}catch(Exception e){out.add(Drone.mara());}return out;}
 public static void save(Context c,List<Drone> list){JSONArray a=new JSONArray();for(Drone d:list)try{a.put(d.json());}catch(Exception ignored){}c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
 public static String exportJson(List<Drone> list)throws Exception{JSONObject o=new JSONObject();o.put("schema",1);o.put("app","БПЛА ИНФО");JSONArray a=new JSONArray();for(Drone d:list)a.put(d.json());o.put("items",a);return o.toString(2);}
 public static int importJson(Context c,List<Drone> list,String input)throws Exception{JSONObject o=new JSONObject(input);if(o.optInt("schema")!=1)throw new IllegalArgumentException("Неподдерживаемый формат");JSONArray a=o.getJSONArray("items");if(a.length()>5000)throw new IllegalArgumentException("Слишком много карточек");int n=0;for(int i=0;i<a.length();i++){Drone d=Drone.fromJson(a.getJSONObject(i));if(d.name().isEmpty())continue;int index=-1;for(int j=0;j<list.size();j++)if(list.get(j).id.equals(d.id)){index=j;break;}if(index<0)list.add(d);else list.set(index,d);n++;}save(c,list);return n;}
}
