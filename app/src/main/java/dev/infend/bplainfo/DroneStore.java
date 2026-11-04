package dev.infend.bplainfo;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
public final class DroneStore {
 private static final String PREFS="bpla_store",KEY="catalog_v1";
 public static ArrayList<Drone> load(Context c){String raw=c.getSharedPreferences(PREFS,0).getString(KEY,null);ArrayList<Drone> out=new ArrayList<>();if(raw==null){out.add(Drone.mara());mergeSeed(c,out);mergeEnrichment(c,out);mergeBulletin30(c,out);mergeVsuPdf(c,out);mergeAlbum(c,out);save(c,out);return out;}try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length()&&i<5000;i++){JSONObject j=a.optJSONObject(i);if(j!=null)out.add(Drone.fromJson(j));}}catch(Exception e){out.add(Drone.mara());}mergeSeed(c,out);mergeEnrichment(c,out);mergeBulletin30(c,out);mergeVsuPdf(c,out);mergeAlbum(c,out);return out;}
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
  private static final int BULLETIN30_REVISION=1;

 /** Exact canonical model names only, no speculative fuzzy crossmatching. */
 private static String normalizeModelName(String s){
   if(s==null)return "";
   return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFKC)
       .toUpperCase(java.util.Locale.ROOT).replace('Ё','Е').replaceAll("[^\\p{L}\\p{N}]","");
 }
 private static boolean sameCategory(String existing,String incoming){
   String t=existing==null?"":existing.toLowerCase(java.util.Locale.ROOT);
   String v=incoming==null?"":incoming.toLowerCase(java.util.Locale.ROOT);
   if(t.contains("коптер")||t.contains("мульти"))return v.contains("коптер");
   if(t.contains("барраж"))return v.contains("барраж");
   if(t.contains("аэростат"))return v.contains("аэростат");
   if(t.contains("самол")||t.contains("крыл"))return v.contains("самол");
   return false;
 }
 private static boolean hasSpecLabel(Drone d,String label){
   for(String[] row:d.specRows){
     if(row.length>=2&&row[0].equalsIgnoreCase(label))return true;
   }
   return false;
 }
 private static void applyBasicSpec(Drone d,String label,String value){
   String u=label.toUpperCase(java.util.Locale.ROOT).replace('Ё','Е');
   if(u.startsWith("ПРОИЗВОДИТЕЛЬ"))d.set(2,value);
   else if(u.startsWith("РАЗМАХ КРЫЛ"))d.set(3,value);
   else if(u.startsWith("МАКСИМАЛЬНАЯ СКОРОСТЬ"))d.set(5,value);
   else if(u.startsWith("ДАЛЬНОСТЬ ДЕЙСТВИЯ"))d.set(6,value);
   else if(u.startsWith("РАБОЧАЯ ВЫСОТА"))d.set(7,value);
   else if(u.startsWith("ПРАКТИЧЕСКИЙ ПОТОЛОК"))d.set(8,value);
   else if(u.startsWith("МАССА ПОЛЕЗНОЙ НАГРУЗКИ"))d.set(9,value);
   else if(u.startsWith("ВЗЛЕТНЫЙ ВЕС")||u.startsWith("ВЗЛЕТНАЯ МАССА"))d.set(10,value);
   else if(u.startsWith("ВРЕМЯ ПОЛЕТА")||u.startsWith("ПРОДОЛЖИТЕЛЬНОСТЬ ПОЛЕТА"))d.set(11,value);
   else if(u.startsWith("СПОСОБ СТАРТА"))d.set(12,value);
   else if(u.startsWith("ТИП ДВИГАТЕЛЯ"))d.set(13,value);
 }
 /**
  * Source-attributed technical catalogue entries from a user's PDF.
  * No photographs are imported; user-local media, favorites and preexisting
  * specifications are never deleted or overwritten.
  */
 private static void mergeBulletin30(Context c,ArrayList<Drone> drones){
   android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
   if(prefs.getInt("bulletin30_revision",0)>=BULLETIN30_REVISION)return;
   try(java.io.InputStream in=c.getAssets().open("bulletin30_2025.json")){
     java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
     byte[] buffer=new byte[8192];int count;
     while((count=in.read(buffer))!=-1){
       out.write(buffer,0,count);
       if(out.size()>900_000)throw new java.io.IOException("Supplemental catalog too large");
     }
     JSONObject root=new JSONObject(out.toString("UTF-8"));
     if(root.optInt("schema")!=1)throw new org.json.JSONException("Unexpected bulletin schema");
     JSONArray entries=root.getJSONArray("entries");
     if(entries.length()<370)throw new org.json.JSONException("Incomplete 2025 catalog");
     HashSet<String> imported=new HashSet<>();
     for(int i=0;i<entries.length();i++){
       JSONObject entry=entries.getJSONObject(i);
       String id=entry.optString("id",""), name=entry.optString("name","").trim();
       String type=entry.optString("type","").trim();
       int page=entry.optInt("page",0);
       JSONArray rows=entry.optJSONArray("specRows");
       if(id.isEmpty()||name.isEmpty()||type.isEmpty()||page<1||page>213||rows==null||rows.length()==0)
         throw new org.json.JSONException("Invalid bulletin entry at "+i);
       if(!imported.add(id))throw new org.json.JSONException("Duplicate bulletin ID "+id);
       Drone matched=null;
       for(Drone existing:drones){
         if(existing.id.equals(id)){matched=existing;break;}
         if(normalizeModelName(existing.name()).equals(normalizeModelName(name))&&sameCategory(existing.type(),type)){
           matched=existing;break;
         }
       }
       String citation="Справочник по БПЛА · Информационно-технический бюллетень №30 (2025), стр. "+page;
       if(matched==null){
         Drone d=new Drone();
         d.id=id;
         d.set(0,name);d.set(1,type);d.set(14,citation);
         d.set(15,"Данные из бюллетеня 2025 года; независимая проверка не выполнялась, возможны опечатки.");
         d.catalogSource=citation;
         d.sourceGroup=type;
         matched=d;
         drones.add(d);
       }
       for(int j=0;j<rows.length();j++){
         JSONArray pair=rows.optJSONArray(j);
         if(pair==null||pair.length()!=2)continue;
         String key=pair.optString(0,"").trim(),value=pair.optString(1,"").trim();
         if(key.isEmpty()||value.isEmpty())continue;
         if(matched.id.equals(id)){
           if(!hasSpecLabel(matched,key)&&matched.specRows.size()<80){
             matched.specRows.add(new String[]{key,value});
             applyBasicSpec(matched,key,value);
           }
         }else{
           String attributed="БЮЛЛЕТЕНЬ №30 (стр. "+page+") · "+key;
           if(!hasSpecLabel(matched,attributed)&&matched.specRows.size()<80)
             matched.specRows.add(new String[]{attributed,value});
         }
       }
       if(entry.optBoolean("variantReviewNeeded")&&matched.specRows.size()<80){
         String warning="БЮЛЛЕТЕНЬ №30 · ПРОВЕРКА МОДИФИКАЦИИ";
         if(!hasSpecLabel(matched,warning))matched.specRows.add(new String[]{warning,
           "Одинаковое название встречается на нескольких страницах; принадлежность к одной модификации не подтверждена."});
       }
     }
     save(c,drones);
     prefs.edit().putInt("bulletin30_revision",BULLETIN30_REVISION).apply();
   }catch(Exception e){
     android.util.Log.w("BPLA-INFO","2025 bulletin import skipped: "+e.getClass().getSimpleName());
   }
 }

 private static final int VSU_PDF_REVISION=1;

 /**
  * Merge the 60-page user-supplied profile source without changing names,
  * favorites, photos, user edits, or any existing specification values.
  * Explicit source IDs resolve model aliases; a different "Vector" remains
  * a distinct aircraft instead of being silently conflated by name.
  */
 private static void mergeVsuPdf(Context c,ArrayList<Drone> drones){
   android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
   if(prefs.getInt("vsu_pdf_revision",0)>=VSU_PDF_REVISION)return;
   try(java.io.InputStream in=c.getAssets().open("catalog_bpla_vsu_pdf.json")){
     java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
     byte[] buf=new byte[8192];int n;
     while((n=in.read(buf))!=-1){
       bytes.write(buf,0,n);
       if(bytes.size()>450_000)throw new java.io.IOException("VSU PDF asset oversized");
     }
     JSONObject catalog=new JSONObject(bytes.toString("UTF-8"));
     if(catalog.optInt("schema")!=1||
        !"2913a58a55163c912160539b44509a772fa518d4fbd7ff9b4cd03dbeaf044b65".equals(
          catalog.optString("sourcePdfSha256","")))
       throw new org.json.JSONException("Unexpected VSU PDF identity");
     JSONArray entries=catalog.getJSONArray("entries");
     if(entries.length()!=34)throw new org.json.JSONException("Incomplete VSU PDF");
     HashSet<String> processed=new HashSet<>();
     for(int i=0;i<entries.length();i++){
       JSONObject item=entries.getJSONObject(i);
       String id=item.optString("id",""),name=item.optString("name","").trim();
       String targetId=item.optString("targetId",""),type=item.optString("type","").trim();
       int page=item.optInt("page",0);
       JSONArray rows=item.optJSONArray("specRows");
       if(id.isEmpty()||name.isEmpty()||page<1||page>60||rows==null||
          !processed.add(id))throw new org.json.JSONException("Invalid VSU PDF entry: "+i);
       Drone matched=null;
       if(!targetId.isEmpty()){
         for(Drone d:drones)if(targetId.equals(d.id)){matched=d;break;}
       }
       if(matched==null){
         for(Drone d:drones)if(id.equals(d.id)){matched=d;break;}
       }
       boolean newRecord=matched==null;
       if(newRecord){
         matched=new Drone();
         matched.id=id;
         matched.set(0,name);
         matched.set(1,type);
         matched.set(14,"БПЛА ВСУ ПДФ.pdf, стр. "+page);
         matched.set(15,"Данные пользовательского PDF не проверены независимо; возможны противоречия.");
         matched.catalogSource="БПЛА ВСУ ПДФ.pdf, стр. "+page;
         matched.sourceGroup=type;
         drones.add(matched);
       }
       for(int j=0;j<rows.length();j++){
         JSONArray row=rows.optJSONArray(j);
         if(row==null||row.length()!=2)continue;
         String key=row.optString(0,"").trim(),value=row.optString(1,"").trim();
         if(key.isEmpty()||value.isEmpty())continue;
         String attributed=newRecord?key:"БПЛА ВСУ (стр. "+page+") · "+key;
         if(!hasSpecLabel(matched,attributed)&&matched.specRows.size()<80){
           matched.specRows.add(new String[]{attributed,value});
           if(newRecord)applyBasicSpec(matched,key,value);
         }
       }
     }
     save(c,drones);
     prefs.edit().putInt("vsu_pdf_revision",VSU_PDF_REVISION).apply();
   }catch(Exception e){
     android.util.Log.w("BPLA-INFO","VSU PDF import unavailable: "+e.getClass().getSimpleName());
   }
 }


 private static final int ALBUM_REVISION=1;

 /** Strictly reviewed name equivalents. Do not merge unrelated variants by fuzzy matching. */
 private static String albumAliasTarget(int physicalPage){
   switch(physicalPage){
     case 11:return "lostarmour-pdf-2024-054"; // UJ-22 Airborne
     case 13:return "bulletin30-2025-p034l"; // D-80 Discovery
     case 16:return "bulletin30-2025-p194r"; // UJ-26 / Бобер
     case 31:return "lostarmour-pdf-2024-063"; // Bayraktar TB2
     case 32:return "lostarmour-pdf-2024-066"; // Mugin-5 Pro
     case 33:return "bulletin30-2025-p035r"; // АН-БК-1
     case 34:return "lostarmour-pdf-2024-068"; // PD-1
     case 37:return "bulletin30-2025-p031l"; // Primoco UAV One 150
     case 48:return "lostarmour-pdf-2024-071"; // Raybird-3
     case 49:return "lostarmour-pdf-2024-064"; // Luna NG
     case 51:return "lostarmour-pdf-2024-055"; // Bayraktar Mini
     case 52:return "bulletin30-2025-p020l"; // Лелека-100
     case 53:return "lostarmour-pdf-2024-057"; // Чаклун
     case 54:return "lostarmour-pdf-2024-081"; // German Quantum Systems Vector, not Pakistani
     case 55:return "bulletin30-2025-p021l"; // Shark
     case 56:return "lostarmour-pdf-2024-037"; // MINI SHARK
     case 58:return "lostarmour-pdf-2024-019"; // Quantix Recon
     case 59:return "lostarmour-pdf-2024-051"; // Spectator M1
     case 60:return "lostarmour-pdf-2024-042"; // PUMA LE
     case 61:return "lostarmour-pdf-2024-041"; // PUMA AE
     case 63:return "bulletin30-2025-p008l"; // ScanEagle
     case 64:return "lostarmour-pdf-2024-032"; // FlyEye
     case 86:return "lostarmour-pdf-2024-002"; // DeltaQuad Pro
     case 88:return "lostarmour-pdf-2024-005"; // А1-СМ Фурия
     case 89:return "lostarmour-pdf-2024-001"; // Бумеранг
     case 90:return "lostarmour-pdf-2024-014"; // Sparrow (not Sparrow LE)
     case 91:return "lostarmour-pdf-2024-003"; // Домаха
     case 100:return "lostarmour-pdf-2024-015"; // Валькирия
     default:return "";
   }
 }
 /**
  * Offline, text-only 156-physical-page album. Never replace user data or
  * transfer unknown-rights images, operational notes, or arms information.
  */
 private static void mergeAlbum(Context c,ArrayList<Drone> drones){
   android.content.SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
   if(prefs.getInt("album_revision",0)>=ALBUM_REVISION)return;
   try(java.io.InputStream raw=c.getAssets().open("catalog_album_2026.json.gz");
       java.io.InputStream in=new java.util.zip.GZIPInputStream(raw)){
     java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
     byte[] buf=new byte[8192];int len;
     while((len=in.read(buf))!=-1){
       output.write(buf,0,len);
       if(output.size()>300_000)throw new java.io.IOException("Album asset oversized");
     }
     JSONObject book=new JSONObject(output.toString("UTF-8"));
     if(book.optInt("schema")!=1||
        !"fb7b03cf3a0693fc2233c82ab3de0eaf1b3e9cb9b61585c7a9fb62ab2eaa94fe".equals(
          book.optString("sourcePdfSha256","")))
       throw new org.json.JSONException("Wrong user album identity");
     JSONArray entries=book.getJSONArray("entries");
     if(entries.length()!=146||book.optInt("photosImported",-1)!=0||
        book.optInt("operationsImported",-1)!=0)
       throw new org.json.JSONException("Incomplete album");
     HashSet<String> ids=new HashSet<>();
     for(int i=0;i<entries.length();i++){
       JSONObject item=entries.getJSONObject(i);
       String id=item.optString("id",""),name=item.optString("name","").trim();
       String type=item.optString("type","").trim(),country=item.optString("country","").trim();
       int page=item.optInt("page",0);
       JSONArray rows=item.optJSONArray("specRows");
       if(!id.equals(String.format(java.util.Locale.ROOT,"album-2026-p%03d",page))||
          name.isEmpty()||page<5||page>155||rows==null||rows.length()<1||
          !ids.add(id)||!(
           type.equals("Самолётный")||type.equals("Коптерный")||
           type.equals("Барражирующий")||type.equals("Морской")||type.equals("Наземный")))
         throw new org.json.JSONException("Invalid album entry "+i);
       Drone found=null;
       String matchId=albumAliasTarget(page);
       if(!matchId.isEmpty()){
         for(Drone candidate:drones)if(candidate.id.equals(matchId)){found=candidate;break;}
       }
       if(found==null){
         for(Drone candidate:drones){
           if(candidate.id.equals(id)){found=candidate;break;}
           if(matchId.isEmpty()&&normalizeModelName(candidate.name()).equals(normalizeModelName(name))
              &&sameCategory(candidate.type(),type)){found=candidate;break;}
         }
       }
       boolean added=found==null;
       if(added){
         found=new Drone();
         found.id=id;
         found.set(0,name);
         found.set(1,type);
         found.set(14,"Альбом беспилотных систем, PDF стр. "+page);
         found.set(15,"Показания загруженного пользователем альбома; независимо не проверены.");
         found.catalogSource="Альбом беспилотных систем, PDF стр. "+page;
         found.sourceGroup=type;
         drones.add(found);
       }
       if(added&&!country.isEmpty())found.set(2,country+" (по альбому)");
       for(int j=0;j<rows.length();j++){
         JSONArray pair=rows.optJSONArray(j);
         if(pair==null||pair.length()!=2)continue;
         String key=pair.optString(0,"").trim(),value=pair.optString(1,"").trim();
         if(key.isEmpty()||value.isEmpty())continue;
         String label=added?key:"АЛЬБОМ (PDF стр. "+page+") · "+key;
         if(!hasSpecLabel(found,label)&&found.specRows.size()<80){
           found.specRows.add(new String[]{label,value});
           if(added)applyBasicSpec(found,key,value);
         }
       }
     }
     save(c,drones);
     prefs.edit().putInt("album_revision",ALBUM_REVISION).apply();
   }catch(Exception e){
     android.util.Log.w("BPLA-INFO","Album import skipped: "+e.getClass().getSimpleName());
   }
 }

 public static void save(Context c,List<Drone> list){JSONArray a=new JSONArray();for(Drone d:list)try{a.put(d.json());}catch(Exception ignored){}c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
 public static String exportJson(List<Drone> list)throws Exception{JSONObject o=new JSONObject();o.put("schema",1);o.put("app","БПЛА ИНФО");JSONArray a=new JSONArray();for(Drone d:list)a.put(d.json());o.put("items",a);return o.toString(2);}
 public static int importJson(Context c,List<Drone> list,String input)throws Exception{JSONObject o=new JSONObject(input);if(o.optInt("schema")!=1)throw new IllegalArgumentException("Неподдерживаемый формат");JSONArray a=o.getJSONArray("items");if(a.length()>5000)throw new IllegalArgumentException("Слишком много карточек");int n=0;for(int i=0;i<a.length();i++){Drone d=Drone.fromJson(a.getJSONObject(i));if(d.name().isEmpty())continue;int index=-1;for(int j=0;j<list.size();j++)if(list.get(j).id.equals(d.id)){index=j;break;}if(index<0)list.add(d);else list.set(index,d);n++;}save(c,list);return n;}
}
