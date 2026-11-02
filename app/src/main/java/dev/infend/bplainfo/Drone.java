package dev.infend.bplainfo;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.UUID;
public final class Drone {
 public static final String[] FIELDS={"name","type","manufacturer","wingspan","length","maxSpeed","range","workingHeight","ceiling","payload","takeoffMass","endurance","launch","engine","source","notes"};
 public static final String[] TITLES={"Название","Тип / категория","Производитель","Размах крыльев","Длина фюзеляжа","Максимальная скорость","Дальность полёта","Рабочая высота","Практический потолок","Полезная нагрузка","Взлётная масса","Автономность","Тип запуска","Тип двигателя","Источник","Примечания"};
 public String id=UUID.randomUUID().toString(),imageUri="";
 public String[] values=new String[FIELDS.length];
 public boolean favorite;
 public Drone(){for(int i=0;i<values.length;i++) values[i]="";}
 public String get(int i){return values[i]==null?"":values[i];}
 public void set(int i,String s){values[i]=s==null?"":s.trim();}
 public String name(){return get(0);}
 public String type(){return get(1);}
 public String manufacturer(){return get(2);}
 public JSONObject json() throws JSONException {JSONObject j=new JSONObject();j.put("id",id);for(int i=0;i<FIELDS.length;i++)j.put(FIELDS[i],get(i));j.put("imageUri",imageUri);j.put("favorite",favorite);return j;}
 public static Drone fromJson(JSONObject j){Drone d=new Drone();String id=j.optString("id","");if(!id.isEmpty()&&id.length()<120)d.id=id;for(int i=0;i<FIELDS.length;i++){String v=j.optString(FIELDS[i],"");d.set(i,v.length()>3000?v.substring(0,3000):v);}d.imageUri=j.optString("imageUri","");d.favorite=j.optBoolean("favorite");return d;}
 public static Drone mara(){Drone d=new Drone();d.id="seed-mara-2m";String[] v={"МАРА-2М","Самолётный","КАРБОЛАЙН","1,95 м","1,14 м","100 км/ч","25 км","50–750 м","2000 м","0,4 кг","2,5 кг","1,5 часа","С руки","Электрический","Изображение пользователя, независимо не проверено","Сведения переписаны с изображения. Рисунки в приложении схематические."};for(int i=0;i<v.length;i++)d.set(i,v[i]);return d;}
 public static double numeric(String s){if(s==null)return -1;java.util.regex.Matcher m=java.util.regex.Pattern.compile("-?\\d+(?:[.,]\\d+)?").matcher(s);try{return m.find()?Double.parseDouble(m.group().replace(',','.')):-1;}catch(Exception e){return -1;}}
}
