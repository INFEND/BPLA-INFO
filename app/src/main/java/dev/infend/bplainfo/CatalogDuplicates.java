package dev.infend.bplainfo;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One-time conservative merge of exact duplicate models, with a complete private backup. */
public final class CatalogDuplicates {
    private static final String PREFS="bpla_store";
    private static final int REVISION=1;
    private CatalogDuplicates(){}

    public static ArrayList<Drone> cleanup(Context context,ArrayList<Drone> records){
        if(context.getSharedPreferences(PREFS,0).getInt("duplicates_cleaned_revision",0)>=REVISION)
            return records;
        try{
            // Clone all records. A backup failure never mutates the loaded catalogue.
            ArrayList<Drone> copied=new ArrayList<>();
            for(Drone d:records)copied.add(Drone.fromJson(d.json()));
            Map<String,Drone> canonical=new LinkedHashMap<>();
            Map<String,Integer> positions=new LinkedHashMap<>();
            ArrayList<Drone> removed=new ArrayList<>();
            for(Drone drone:copied){
                String airframe=CatalogFacets.airframe(drone.type(),drone.sourceGroup,drone.name());
                String key=CatalogFacets.canonicalName(drone.name());
                if(key.isEmpty()||airframe.isEmpty()){
                    canonical.put("UNIQUE:"+drone.id,drone);
                    continue;
                }
                key=airframe+"|"+key;
                Drone seen=canonical.get(key);
                if(seen==null){
                    canonical.put(key,drone);
                    continue;
                }
                // Never automatically merge user-created IDs with imported models.
                // Their edits may intentionally differ even when names match.
                if(!generated(seen)||!generated(drone)||
                   !CatalogFacets.identicalModel(seen.name(),drone.name())){
                    canonical.put(key+"|"+drone.id,drone);
                    continue;
                }
                Drone chosen=score(seen)>=score(drone)?seen:drone;
                Drone redundant=chosen==seen?drone:seen;
                merge(chosen,redundant);
                canonical.put(key,chosen);
                removed.add(redundant);
            }
            if(removed.isEmpty()){
                context.getSharedPreferences(PREFS,0).edit()
                    .putInt("duplicates_cleaned_revision",REVISION).apply();
                return records;
            }
            // Persist all originals, not merely the discarded records: reversible archive.
            File backup=new File(context.getNoBackupFilesDir(),"catalog_exact_duplicates_before_v1.json");
            if(!backup.isFile()){
                JSONArray all=new JSONArray();
                for(Drone d:records)all.put(d.json());
                byte[] bytes=all.toString().getBytes(StandardCharsets.UTF_8);
                if(bytes.length>12_000_000)throw new IllegalStateException("Backup too large");
                AtomicFile atomic=new AtomicFile(backup);
                FileOutputStream out=null;
                try{
                    out=atomic.startWrite();
                    out.write(bytes);
                    atomic.finishWrite(out);
                }catch(Exception error){
                    if(out!=null)atomic.failWrite(out);
                    throw error;
                }
            }
            ArrayList<Drone> unique=new ArrayList<>(canonical.values());
            if(unique.size()+removed.size()!=records.size())
                throw new IllegalStateException("Unexpected merge count");
            DroneStore.save(context,unique);
            context.getSharedPreferences(PREFS,0).edit()
                .putInt("duplicates_cleaned_revision",REVISION).apply();
            android.util.Log.i("BPLA-INFO","Archived "+removed.size()+" exact duplicate cards");
            return unique;
        }catch(Exception ex){
            android.util.Log.e("BPLA-INFO","Duplicate cleanup skipped, originals preserved",ex);
            return records;
        }
    }

    private static boolean generated(Drone d){
        return d.id.startsWith("lostarmour-pdf-")||
               d.id.startsWith("bulletin30-")||
               d.id.startsWith("album-2026-")||
               d.id.startsWith("vsu-pdf-");
    }
    private static int score(Drone d){
        int count=0;
        if(d.id.startsWith("lostarmour-pdf"))count+=150;
        if(d.id.startsWith("bulletin30"))count+=110;
        if(d.id.startsWith("album"))count+=60;
        if(d.favorite)count+=25;
        count+=Math.min(18,d.galleryUris.size())*5;
        count+=Math.min(100,d.specRows.size());
        for(int i=1;i<14;i++)if(!d.get(i).isEmpty())count++;
        return count;
    }
    private static void merge(Drone keeper,Drone other){
        keeper.favorite|=other.favorite;
        for(String uri:other.galleryUris)
            if(!uri.isEmpty()&&!keeper.galleryUris.contains(uri)&&keeper.galleryUris.size()<15)
                keeper.galleryUris.add(uri);
        if(keeper.imageUri.isEmpty())keeper.imageUri=other.imageUri;
        for(int i=1;i<keeper.values.length;i++){
            if(keeper.get(i).isEmpty()&&!other.get(i).isEmpty())keeper.set(i,other.get(i));
        }
        if(keeper.catalogSource.isEmpty())keeper.catalogSource=other.catalogSource;
        if(keeper.sourceGroup.isEmpty())keeper.sourceGroup=other.sourceGroup;
        for(String[] row:other.specRows){
            if(row==null||row.length<2||keeper.specRows.size()>=80)continue;
            String label=CardPresentation.label(row[0]);
            if(label.isEmpty())continue;
            boolean present=false;
            for(String[] have:keeper.specRows)
                if(have.length>=2&&label.equals(CardPresentation.label(have[0]))){
                    present=true;break;
                }
            if(!present)keeper.specRows.add(new String[]{row[0],row[1]});
        }
    }
}
