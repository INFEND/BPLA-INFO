package dev.infend.bplainfo;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.UUID;
public final class Drone {
 public static final String[] FIELDS={"name","type","manufacturer","wingspan","length","maxSpeed","range","workingHeight","ceiling","payload","takeoffMass","endurance","launch","engine","source","notes"};
 public static final String[] TITLES={"Название","Тип / категория","Производитель","Размах крыльев","Длина фюзеляжа","Максимальная скорость","Дальность полёта","Рабочая высота","Практический потолок","Полезная нагрузка","Взлётная масса","Автономность","Тип запуска","Тип двигателя","Источник","Примечания"};
 public String id=UUID.randomUUID().toString(),imageUri="";
 public String[] values=new String[FIELDS.length];
 public java.util.ArrayList<String> galleryUris=new java.util.ArrayList<>();
 public boolean favorite;
 public String catalogSource="",sourceGroup="";
 public java.util.ArrayList<String[]> specRows=new java.util.ArrayList<>();
 public Drone(){for(int i=0;i<values.length;i++) values[i]="";}
 public String get(int i){return values[i]==null?"":values[i];}
 public void set(int i,String s){values[i]=s==null?"":s.trim();}
 public String name(){return get(0);}
 public String type(){return get(1);}
 public String manufacturer(){return get(2);}
 public JSONObject json() throws JSONException {JSONObject j=new JSONObject();j.put("id",id);for(int i=0;i<FIELDS.length;i++)j.put(FIELDS[i],get(i));j.put("imageUri",imageUri);org.json.JSONArray gallery=new org.json.JSONArray();
 for(String image:galleryUris)gallery.put(image);j.put("galleryUris",gallery);
 j.put("favorite",favorite);j.put("catalogSource",catalogSource);j.put("sourceGroup",sourceGroup);
 org.json.JSONArray specArray=new org.json.JSONArray();
 for(String[] row:specRows){if(row.length<2)continue;org.json.JSONArray pair=new org.json.JSONArray();pair.put(row[0]);pair.put(row[1]);specArray.put(pair);}
 j.put("specRows",specArray);return j;}
 public static Drone fromJson(JSONObject j){Drone d=new Drone();String id=j.optString("id","");if(!id.isEmpty()&&id.length()<120)d.id=id;for(int i=0;i<FIELDS.length;i++){String v=j.optString(FIELDS[i],"");d.set(i,v.length()>3000?v.substring(0,3000):v);}d.imageUri=j.optString("imageUri","");
 org.json.JSONArray gallery=j.optJSONArray("galleryUris");
 if(gallery!=null)for(int i=0;i<gallery.length()&&i<15;i++){
  String uri=gallery.optString(i,"");if(!uri.isEmpty()&&!d.galleryUris.contains(uri))d.galleryUris.add(uri);
 }
 if(d.galleryUris.isEmpty()&&!d.imageUri.isEmpty())d.galleryUris.add(d.imageUri);
 d.favorite=j.optBoolean("favorite");
 d.catalogSource=j.optString("catalogSource","");
 d.sourceGroup=j.optString("sourceGroup","");
 org.json.JSONArray rows=j.optJSONArray("specRows");
 if(rows!=null)for(int i=0;i<rows.length()&&i<80;i++){
   org.json.JSONArray pair=rows.optJSONArray(i);
   if(pair!=null&&pair.length()>=2)d.specRows.add(new String[]{pair.optString(0),pair.optString(1)});
 }
 return d;}
 public static Drone mara(){Drone d=new Drone();d.id="seed-mara-2m";String[] v={"МАРА-2М","Самолётный","КАРБОЛАЙН","1,95 м","1,14 м","100 км/ч","25 км","50–750 м","2000 м","0,4 кг","2,5 кг","1,5 часа","С руки","Электрический","Изображение пользователя, независимо не проверено","Сведения переписаны с изображения. Рисунки в приложении схематические."};for(int i=0;i<v.length;i++)d.set(i,v[i]);return d;}
 public String specification(String query){
   String term=query==null?"":query.toLowerCase(java.util.Locale.ROOT);
   for(String[] row:specRows){
      if(row[0].toLowerCase(java.util.Locale.ROOT).contains(term))return row[1];
   }
   return "";
 }
 public String displayType(){
   String resolved=CatalogFacets.airframe(type(),sourceGroup,name());
   return resolved.isEmpty()?"БПЛА":resolved;
 }
 public String engineType(){
   String a=specification("ТИП ДВИГАТЕЛЯ");
   return a.isEmpty()?get(13):a;
 }
 private String firstSpecification(String... names){
   for(String name:names){
     String upper=name.toUpperCase(java.util.Locale.ROOT);
     for(String[] row:specRows){
       if(row.length<2)continue;
       if(row[0].toUpperCase(java.util.Locale.ROOT).equals(upper)&&!row[1].trim().isEmpty())return row[1].trim();
     }
   }
   return "";
 }
 public String previewSpan(){String a=firstSpecification("РАЗМАХ КРЫЛЬЕВ");return a.isEmpty()?get(3):a;}
 public String previewEndurance(){String a=firstSpecification("АВТОНОМНОСТЬ","ВРЕМЯ ПОЛЕТА","ПРОДОЛЖИТЕЛЬНОСТЬ ПОЛЕТА","ВРЕМЯ РАБОТЫ");return a.isEmpty()?get(11):a;}
 public String previewSpeed(){
   String a=firstSpecification("МАКСИМАЛЬНАЯ СКОРОСТЬ","МАКС. ГОРИЗОНТ. СКОРОСТЬ","КРЕЙСЕРСКАЯ СКОРОСТЬ","СКОРОСТЬ АТАКИ");
   return a.isEmpty()?get(5):a;
 }
 public String previewRange(){String a=firstSpecification("ДАЛЬНОСТЬ ПОЛЕТА","РАДИУС ДЕЙСТВИЯ","ТАКТИЧЕСКИЙ РАДИУС, КМ","ТАКТИЧЕСКИЙ РАДИУС","ДАЛЬНОСТЬ","ДАЛЬНОСТЬ ХОДА");return a.isEmpty()?get(6):a;}
 public static double numeric(String s){if(s==null)return -1;java.util.regex.Matcher m=java.util.regex.Pattern.compile("-?\\d+(?:[.,]\\d+)?").matcher(s);try{return m.find()?Double.parseDouble(m.group().replace(',','.')):-1;}catch(Exception e){return -1;}}
}
