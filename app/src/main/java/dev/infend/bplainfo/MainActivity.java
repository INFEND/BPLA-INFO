package dev.infend.bplainfo;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int PICK_IMAGE=201, IMPORT_JSON=202, EXPORT_JSON=203;
    private final ArrayList<Drone> records=new ArrayList<>();
    private ThemePrefs t;
    private LinearLayout root,listHost;
    private int cardRenderEpoch=0;
    private static final int CARD_BATCH_SIZE=24;
    private String searchTerm="",category="Все";
    private boolean onlyFavorites=false,descending=false,showingDetail=false;
    private int sortBy=0;
    private Drone pendingImage;
    private final String[] sortNames={"По названию","По производителю","По скорости","По дальности","По массе","По размаху крыла"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        t=new ThemePrefs(this);
        // Show a live screen before reading/migrating the large bundled catalog.
        setup();
        LinearLayout loading=vertical();
        loading.setPadding(dp(22),dp(32),dp(22),dp(24));
        loading.addView(text("ЗАГРУЗКА КАТАЛОГА",18,t.fg,true));
        loading.addView(text("Подготовка карточек БПЛА…",13,t.muted,false));
        root.addView(loading);
        new Thread(()->{
            try{
                Context app=getApplicationContext();
                PhotoLibrary.purgeLegacyCache(app);
                ArrayList<Drone> loaded=DroneStore.load(app);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    records.addAll(loaded);
                    home();
                    long last=getPreferences(0).getLong("ota_checked",0);
                    if(System.currentTimeMillis()-last>24*60*60*1000L){
                        getPreferences(0).edit().putLong("ota_checked",System.currentTimeMillis()).apply();
                        new OtaUpdater(this).check(true);
                    }
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    loading.addView(text("Не удалось открыть каталог: "+error.getClass().getSimpleName(),13,t.muted,false));
                    loading.addView(button("ПОВТОРИТЬ",true,()->recreate()));
                });
            }
        },"bpla-catalog-load").start();
    }
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams margins(int w,int h,int x,int y){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.setMargins(dp(x),dp(y),dp(x),dp(y));return p;
    }
    private GradientDrawable panel(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),t.stroke);return g;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));v.setIncludeFontPadding(true);return v;
    }
    private TextView button(String label,boolean accent,Runnable click){
        TextView b=text(label,13,accent?(t.mode==2?Color.WHITE:0xFF06140C):t.fg,true);
        b.setGravity(Gravity.CENTER);b.setPadding(dp(13),dp(10),dp(13),dp(10));
        b.setBackground(panel(accent?t.accent:t.panel,14));
        b.setOnClickListener(v->click.run());return b;
    }
    private void setup(){
        cardRenderEpoch++; // Cancel any pending batch from the previous screen.
        getWindow().setStatusBarColor(t.bg);getWindow().setNavigationBarColor(t.bg);
        int flags=t.mode==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        root=vertical();root.setBackgroundColor(t.bg);
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController ctl=getWindow().getInsetsController();
            if(ctl!=null){
                int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctl.setSystemBarsAppearance(t.mode==2?mask:0,mask);
            }
            root.setOnApplyWindowInsetsListener((view,insets)->{
                Insets bars=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
        }else root.setFitsSystemWindows(true);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.requestApplyInsets();
    }
    private ScrollView scroll(LinearLayout into){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.setClipToPadding(false);
        sc.setVerticalScrollBarEnabled(false);into.addView(sc,new LinearLayout.LayoutParams(-1,0,1));return sc;
    }
    private void titleBar(String title,String hint,Runnable back){
        LinearLayout bar=horizontal();bar.setPadding(dp(18),dp(18),dp(18),dp(12));root.addView(bar);
        if(back!=null){TextView b=button("‹",false,back);b.setTextSize(24);bar.addView(b,margins(46,46,0,0));}
        LinearLayout titles=vertical();LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);
        titleLp.leftMargin=dp(back==null?0:10);bar.addView(titles,titleLp);
        titles.addView(text(title,23,t.fg,true));
        titles.addView(text(hint,11,t.muted,false));
        if(back==null)bar.addView(button("⚙",false,this::settings),margins(46,46,4,0));
    }
    private void home(){
        showingDetail=false;showingSettings=false;activeSettingsSection="";
        setup();
        titleBar("БПЛА  ИНФО","Каталог БПЛА",null);
        LinearLayout inner=vertical();inner.setPadding(dp(14),0,dp(14),dp(30));
        ScrollView sc=scroll(root);sc.addView(inner);
        LinearLayout controls=horizontal();inner.addView(controls,margins(-1,-2,0,9));
        controls.addView(button("+ ДОБАВИТЬ",true,()->edit(null)),new LinearLayout.LayoutParams(0,dp(43),1));
        controls.addView(button("⇅ СОРТИРОВКА",false,this::sortDialog),margins(-2,43,5,0));
        controls.addView(button(onlyFavorites?"★":"☆",false,()->{onlyFavorites=!onlyFavorites;home();}),margins(44,43,5,0));
        EditText search=new EditText(this);
        search.setTextSize(15);search.setSingleLine(true);search.setTextColor(t.fg);search.setHintTextColor(t.muted);
        search.setPadding(dp(16),0,dp(14),0);search.setHint("⌕  Поиск по названию и параметрам");
        search.setBackground(panel(t.panel,16));search.setText(searchTerm);
        inner.addView(search,margins(-1,49,0,6));
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int start,int before,int count){searchTerm=s.toString();cards();}
            public void afterTextChanged(Editable e){}
        });
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=horizontal();strip.addView(chips);inner.addView(strip,margins(-1,47,0,6));
        for(String group:new String[]{"Все","Самолётный","Мультикоптер","Многомоторный","Барражирующие боеприпасы"}){
            TextView chip=button(group,category.equals(group),()->{category=group;home();});
            chips.addView(chip,margins(-2,39,0,0));
        }
        LinearLayout heading=horizontal();inner.addView(heading,margins(-1,-2,3,3));
        TextView count=text("",13,t.muted,true);count.setId(1234001);heading.addView(count,new LinearLayout.LayoutParams(0,dp(31),1));
        heading.addView(button("↧ JSON",false,this::importFile),margins(-2,34,3,0));
        heading.addView(button("↥ JSON",false,this::exportFile),margins(-2,34,3,0));
        listHost=vertical();inner.addView(listHost);
        cards();
    }
    private List<Drone> filtered(){
        ArrayList<Drone> a=new ArrayList<>();
        String q=searchTerm.toLowerCase(Locale.ROOT).trim();
        for(Drone d:records){
            if(onlyFavorites&&!d.favorite)continue;
            String kind=d.displayType().toLowerCase(Locale.ROOT);
            if(category.equals("Самолётный")&&!kind.equals("самолётный"))continue;
            if(category.equals("Мультикоптер")&&!kind.equals("мультикоптер"))continue;
            if(category.equals("Многомоторный")&&!kind.equals("многомоторный"))continue;
            if(category.equals("Барражирующие боеприпасы")&&!kind.contains("барраж"))continue;
            if(!q.isEmpty()){
                boolean match=false;
                for(String field:d.values){if(field!=null&&field.toLowerCase(Locale.ROOT).contains(q)){match=true;break;}}
                if(!match)for(String[] row:d.specRows){
                    if(row.length>=2&&(row[0].toLowerCase(Locale.ROOT).contains(q)||row[1].toLowerCase(Locale.ROOT).contains(q))){match=true;break;}
                }
                if(!match)continue;
            }
            a.add(d);
        }
        a.sort((x,y)->{
            int result;
            if(sortBy>=2){
                int field=sortBy==2?5:sortBy==3?6:sortBy==4?10:3;
                double vx=Drone.numeric(sortBy==2?x.previewSpeed():sortBy==3?x.previewRange():x.get(field));
                double vy=Drone.numeric(sortBy==2?y.previewSpeed():sortBy==3?y.previewRange():y.get(field));
                if(vx<0&&vy>=0)return 1;
                if(vy<0&&vx>=0)return -1;
                result=Double.compare(vx,vy);
            }else result=(sortBy==0?x.name():x.manufacturer()).compareToIgnoreCase(sortBy==0?y.name():y.manufacturer());
            return descending?-result:result;
        });
        return a;
    }
    private void cards(){
        if(listHost==null)return;
        final LinearLayout target=listHost;
        final int epoch=++cardRenderEpoch;
        target.removeAllViews();
        List<Drone> found=filtered();
        View parent=(View)listHost.getParent();
        if(parent!=null){View count=((View)parent).getRootView().findViewById(1234001);if(count instanceof TextView)((TextView)count).setText("НАЙДЕНО: "+found.size()+"   |   ВСЕГО: "+records.size());}
        if(found.isEmpty()){
            LinearLayout empty=vertical();empty.setPadding(dp(20),dp(36),dp(20),dp(36));empty.setGravity(Gravity.CENTER);
            empty.setBackground(panel(t.panel,22));
            empty.addView(text("⌕  Карточки не найдены",18,t.fg,true));
            empty.addView(text("Измени фильтр или создай новую запись",13,t.muted,false));
            listHost.addView(empty,margins(-1,-2,0,8));return;
        }
        renderCardBatch(target,found,0,epoch);
    }
    private void renderCardBatch(LinearLayout target,List<Drone> found,int offset,int epoch){
        if(epoch!=cardRenderEpoch||target!=listHost||isFinishing()||isDestroyed())return;
        int end=Math.min(found.size(),offset+CARD_BATCH_SIZE);
        for(int index=offset;index<end;index++){
            Drone d=found.get(index);
            LinearLayout card=vertical();card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(panel(t.panel,20));
            listHost.addView(card,margins(-1,-2,0,5));
            LinearLayout head=horizontal();card.addView(head);
            ImageView typeIcon=new ImageView(this);
            String type=d.type().toLowerCase(Locale.ROOT);
            typeIcon.setImageResource(type.contains("коптер")||type.contains("мульт")||type.contains("квадр")?R.drawable.ic_multicopter:R.drawable.ic_airplane);
            typeIcon.setColorFilter(t.accent);
            typeIcon.setPadding(dp(3),dp(3),dp(3),dp(3));
            head.addView(typeIcon,margins(37,37,0,0));
            LinearLayout labels=vertical();head.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            labels.addView(text(d.name(),20,t.fg,true));
            String engine=d.engineType().trim();
            String modelType=d.displayType();
            String subtitle=engine.isEmpty()?modelType:modelType+" | "+engine;
            labels.addView(text(subtitle,13,t.fg,true));
            TextView star=button(d.favorite?"★":"☆",false,()->{d.favorite=!d.favorite;save();cards();});
            star.setTextColor(t.accent);head.addView(star,margins(42,42,3,0));
            LinearLayout stats=horizontal();card.addView(stats);
            stat(stats,"АВТОНОМНОСТЬ",d.previewEndurance());
            stat(stats,"СКОРОСТЬ",d.previewSpeed());
            stat(stats,"ДАЛЬНОСТЬ",d.previewRange());
            card.setOnClickListener(v->detail(d));
            if(t.animations){card.setAlpha(0f);card.setTranslationY(dp(13));card.animate().alpha(1).translationY(0).setDuration(180).setStartDelay(Math.min(index,8)*25L).start();}
        }
        if(end<found.size()){
            target.postDelayed(()->renderCardBatch(target,found,end,epoch),12L);
        }
    }
    private void stat(LinearLayout row,String label,String value){
        String formatted=SpecFormatter.display(value);
        if(formatted.isEmpty())return;
        LinearLayout group=vertical();group.setPadding(dp(4),dp(3),dp(3),dp(2));
        row.addView(group,new LinearLayout.LayoutParams(0,-2,1));
        group.addView(text(label,9,t.muted,true));
        group.addView(text(formatted,13,t.accent,true));
    }
    private void save(){DroneStore.save(this,records);}
    private void detail(Drone d){
        hideKeyboard();showingDetail=true;setup();
        titleBar(d.name(),d.catalogSource.isEmpty()?"ТЕХНИЧЕСКАЯ КАРТОЧКА":"АРХИВ | КЛАССИФИКАТОР 2024",this::home);
        LinearLayout content=vertical();content.setPadding(dp(15),0,dp(15),dp(26));scroll(root).addView(content);
        LinearLayout photos=vertical();photos.setPadding(dp(12),dp(13),dp(12),dp(13));
        photos.setBackground(outlined(t.panel,t.stroke,20));
        content.addView(photos,margins(-1,-2,0,7));
        LinearLayout phototop=horizontal();photos.addView(phototop);
        TextView photosTitle=text("ФОТОГРАФИИ МОДЕЛИ",12,t.accent,true);
        phototop.addView(photosTitle,new LinearLayout.LayoutParams(0,-2,1));
        phototop.addView(button("+ ФОТО",false,()->openPhotoPicker(d)),margins(-2,38,4,0));
        java.util.List<PhotoLibrary.Photo> documentary=PhotoLibrary.forDrone(this,d);
        java.util.LinkedHashSet<String> images=new java.util.LinkedHashSet<>(d.galleryUris);
        if(images.isEmpty()&&!d.imageUri.isEmpty())images.add(d.imageUri);
        int available=documentary.size()+images.size();

        if(available==0){
            photos.addView(text("Фото пока не добавлены. Используйте «+ ФОТО», чтобы выбрать свои снимки с устройства.",12,t.muted,false),margins(-1,-2,0,8));
        }else{
            HorizontalScrollView strip=new HorizontalScrollView(this);
            strip.setHorizontalScrollBarEnabled(false);
            photos.addView(strip,margins(-1,214,0,5));
            LinearLayout gallery=horizontal();strip.addView(gallery);
            // Decode only locally bundled photos that are visible in the strip.
            // No image download or remote photo URLs are used in this application.
            java.util.ArrayList<View> lazyFrames=new java.util.ArrayList<>();
            java.util.ArrayList<Runnable> lazyLoads=new java.util.ArrayList<>();
            int photoNumber=0;
            for(PhotoLibrary.Photo p:documentary){
                final int number=++photoNumber;
                LinearLayout frame=vertical();frame.setPadding(dp(6),dp(6),dp(6),dp(4));
                frame.setBackground(outlined(t.soft,t.stroke,14));
                gallery.addView(frame,margins(250,202,0,5));
                ImageView photo=new ImageView(this);
                photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                photo.setImageResource(R.drawable.ic_airplane);photo.setColorFilter(t.muted);
                frame.addView(photo,new LinearLayout.LayoutParams(-1,0,1));
                TextView photoStatus=text("ФОТО В ПРИЛОЖЕНИИ",10,t.muted,false);
                frame.addView(photoStatus,margins(-1,-2,0,2));
                final boolean[] requested={false};
                lazyFrames.add(frame);
                lazyLoads.add(()->{
                    if(requested[0])return;
                    requested[0]=true;
                    photoStatus.setText("ОТКРЫТИЕ ФОТО…");
                    PhotoLibrary.into(this,p,photo,1350,ok->
                        photoStatus.setText(ok?"СНИМОК "+number:PhotoLibrary.unavailableMessage(this)));
                });

                frame.setOnClickListener(v->showDocumentaryPhoto(p));
            }
            for(String path:images){
                if(photoNumber++>=15)break;
                LinearLayout frame=vertical();frame.setPadding(dp(6),dp(6),dp(6),dp(4));
                frame.setBackground(outlined(t.soft,t.stroke,14));
                gallery.addView(frame,margins(250,202,0,5));
                ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                frame.addView(photo,new LinearLayout.LayoutParams(-1,0,1));
                try{photo.setImageURI(Uri.parse(path));}catch(Exception ignored){}

                frame.setOnClickListener(v->showFullPhoto(path));
            }
            strip.setOnScrollChangeListener((v,x,y,oldX,oldY)->
                loadVisibleDocumentaryPhotos(strip,lazyFrames,lazyLoads));
            gallery.addOnLayoutChangeListener((v,l,top,r,b,oldL,oldT,oldR,oldB)->
                loadVisibleDocumentaryPhotos(strip,lazyFrames,lazyLoads));
            strip.post(()->loadVisibleDocumentaryPhotos(strip,lazyFrames,lazyLoads));
        }
        LinearLayout actions=horizontal();content.addView(actions,margins(-1,49,0,6));
        actions.addView(button(d.favorite?"★ В ИЗБРАННОМ":"☆ ИЗБРАННОЕ",false,()->{d.favorite=!d.favorite;save();detail(d);}),new LinearLayout.LayoutParams(0,-1,1));
        actions.addView(button("✎ ИЗМЕНИТЬ",true,()->edit(d)),margins(-2,-1,4,0));
        actions.addView(button("⋮",false,()->more(d)),margins(45,-1,4,0));
        LinearLayout table=vertical();table.setPadding(dp(15),dp(14),dp(15),dp(14));table.setBackground(panel(t.panel,19));
        content.addView(table,margins(-1,-2,0,6));table.addView(text("ХАРАКТЕРИСТИКИ БПЛА",15,t.accent,true),margins(-1,-2,0,9));
        if(!d.specRows.isEmpty()){
            for(String[] item:d.specRows){
                if(item.length<2||SpecFormatter.display(item[1]).isEmpty())continue;
                LinearLayout row=vertical();row.setPadding(0,dp(6),0,dp(8));table.addView(row);
                row.addView(text(item[0].toUpperCase(Locale.ROOT),10,t.muted,true));
                row.addView(text(SpecFormatter.display(item[1]),15,t.fg,true));
                View divider=new View(this);divider.setBackgroundColor(t.stroke);
                table.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }else{
            for(int i=1;i<14;i++){
                if(SpecFormatter.display(d.get(i)).isEmpty())continue;
                LinearLayout row=vertical();row.setPadding(0,dp(6),0,dp(8));table.addView(row);
                row.addView(text(Drone.TITLES[i].toUpperCase(Locale.ROOT),10,t.muted,true));
                row.addView(text(SpecFormatter.display(d.get(i)),15,t.fg,true));
                View divider=new View(this);divider.setBackgroundColor(t.stroke);
                table.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            }
        }
    }
    private void loadVisibleDocumentaryPhotos(HorizontalScrollView strip,
                                             java.util.List<View> frames,
                                             java.util.List<Runnable> loaders){
        if(strip.getWidth()<=0)return;
        int left=strip.getScrollX()-dp(60);
        int right=strip.getScrollX()+strip.getWidth()+dp(60);
        for(int i=0;i<frames.size();i++){
            View frame=frames.get(i);
            if(frame.getWidth()>0&&frame.getRight()>=left&&frame.getLeft()<=right)
                loaders.get(i).run();
        }
    }
    private void more(Drone d){
        UiDialogs.options(this,t,d.name(),new String[]{"Добавить фотографии","Поделиться карточкой","Удалить карточку"},-1,which->{
            if(which==0){openPhotoPicker(d);return;}
            if(which==1){
                StringBuilder content=new StringBuilder(d.name()+"\n");
                if(!d.specRows.isEmpty()){
                    for(String[] item:d.specRows){
                        if(item.length>=2)content.append(item[0]).append(": ").append(item[1]).append('\n');
                    }
                }else{
                    for(int i=1;i<14;i++){
                        if(!d.get(i).isEmpty())content.append(Drone.TITLES[i]).append(": ").append(d.get(i)).append('\n');
                    }
                }
                Intent intent=new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,content.toString());
                startActivity(Intent.createChooser(intent,"Поделиться карточкой"));
                return;
            }
            if(which==2)UiDialogs.confirmation(this,t,"Удаление карточки",
                "Удалить «"+d.name()+"» из локального каталога?","УДАЛИТЬ",()->{
                    records.remove(d);save();home();
                });
        });
    }
    private void edit(Drone existing){
        Drone d=existing==null?new Drone():existing;
        ScrollView sc=new ScrollView(this);LinearLayout fields=vertical();fields.setPadding(dp(18),dp(8),dp(18),dp(7));sc.addView(fields);
        EditText[] inputs=new EditText[Drone.FIELDS.length];
        for(int i=0;i<inputs.length;i++){
            if(i>=14)continue;
            fields.addView(text(Drone.TITLES[i],12,t.mode==2?0xFF425348:t.muted,true),margins(-1,-2,0,0));
            EditText e=new EditText(this);e.setTextSize(15);e.setSingleLine(i!=15);e.setText(d.get(i));
            if(i==15)e.setMinLines(2);
            e.setHint(Drone.TITLES[i]);e.setSelectAllOnFocus(false);inputs[i]=e;fields.addView(e,margins(-1,43,0,4));
        }
        for(EditText input:inputs){
            if(input==null)continue;
            input.setTextColor(t.fg);
            input.setHintTextColor(t.muted);
            input.setBackground(outlined(t.soft,t.stroke,10));
            input.setPadding(dp(12),dp(5),dp(12),dp(5));
        }
        UiDialogs.editor(this,t,existing==null?"Новая карточка":"Редактирование",sc,()->{
            if(inputs[0].getText().toString().trim().isEmpty()){
                inputs[0].setError("Название обязательно");return false;
            }
            for(int i=0;i<inputs.length;i++)if(inputs[i]!=null)d.set(i,inputs[i].getText().toString());
            if(existing==null)records.add(d);
            save();
            detail(d);
            return true;
        });
    }
    private void sortDialog(){
        String[] names=new String[sortNames.length+1];
        System.arraycopy(sortNames,0,names,0,sortNames.length);
        names[sortNames.length]=descending?"Направление: ↓ убывание":"Направление: ↑ возрастание";
        UiDialogs.options(this,t,"Сортировка",names,sortBy,choice->{
            if(choice==sortNames.length)descending=!descending;
            else sortBy=choice;
            home();
        });
    }
    private String activeSettingsSection="";
    private final java.util.Map<String,LinearLayout> settingsBodies=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,TextView> settingsArrows=new java.util.LinkedHashMap<>();
    private boolean showingSettings=false;
    private String appVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "—";}
    }
    private void settings(){
        activeSettingsSection="";
        settingsBodies.clear();
        settingsArrows.clear();
        showingDetail=false;showingSettings=true;
        setup();
        titleBar("НАСТРОЙКИ","ПЕРСОНАЛИЗАЦИЯ  /  БПЛА ИНФО",this::home);
        LinearLayout content=vertical();content.setPadding(dp(16),0,dp(16),dp(28));
        scroll(root).addView(content);
        LinearLayout intro=vertical();intro.setPadding(dp(17),dp(16),dp(17),dp(17));
        intro.setBackground(panel(t.panel,20));content.addView(intro,margins(-1,-2,0,8));
        intro.addView(text("ПЕРСОНАЛИЗАЦИЯ",11,t.accent,true));
        intro.addView(text("Интерфейс под твой стиль",20,t.fg,true),margins(-1,-2,0,4));
        intro.addView(text("Удобное оформление и персональные настройки.",12,t.muted,false));

        addSettingsSection(content,"appearance","Оформление","Тема и цветовые акценты","◈",body->{
            body.addView(text("ТЕМА ИНТЕРФЕЙСА",11,t.muted,true),margins(-1,-2,1,10));
            LinearLayout themes=horizontal();body.addView(themes,margins(-1,96,0,7));
            String[] names={"AMOLED","Тёмная","Светлая"},iconKinds={"amoled","dark","light"};
            for(int i=0;i<3;i++){
                final int index=i;boolean selected=t.mode==i;
                LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(3),dp(6),dp(3),dp(6));
                tile.setBackground(outlined(selected?t.soft:t.buttonBg,selected?t.accent:t.stroke,14));
                SettingsIconView glyph=new SettingsIconView(this,iconKinds[i],selected?t.accent:t.muted);
                tile.addView(glyph,margins(34,34,0,4));
                TextView title=text(names[i],12,selected?t.accent:t.fg,selected);
                title.setGravity(Gravity.CENTER);tile.addView(title);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                lp.setMargins(dp(3),0,dp(3),0);themes.addView(tile,lp);
                tile.setContentDescription("Тема "+names[i]);
                tile.setOnClickListener(v->{
                    if(t.mode==index)return;
                    t.mode=index;t.save(this);t=new ThemePrefs(this);settings();
                });
            }
            body.addView(text("АКЦЕНТНЫЙ ЦВЕТ  |  "+ThemePrefs.ACCENTS[t.accentIndex],11,t.muted,true),margins(-1,-2,2,10));
            for(int from=0;from<ThemePrefs.ACCENTS.length;from+=4){
                LinearLayout row=horizontal();body.addView(row,margins(-1,75,0,2));
                for(int j=from;j<Math.min(from+4,ThemePrefs.ACCENTS.length);j++){
                    final int index=j;boolean selected=index==t.accentIndex;
                    LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
                    tile.setPadding(dp(2),dp(7),dp(2),dp(5));
                    tile.setBackground(outlined(selected?t.soft:t.panel,selected?t.accent:t.stroke,13));
                    TextView dot=text(selected?"✓":"●",21,selected?contrast(ThemePrefs.PALETTE[index]):ThemePrefs.PALETTE[index],true);
                    dot.setGravity(Gravity.CENTER);
                    dot.setBackground(outlined(ThemePrefs.PALETTE[index],ThemePrefs.PALETTE[index],30));
                    tile.addView(dot,margins(29,29,0,0));
                    TextView label=text(ThemePrefs.ACCENTS[index],10,selected?t.accent:t.fg,selected);
                    label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    tile.addView(label,margins(-1,-2,0,3));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
                    lp.setMargins(dp(2),0,dp(2),0);row.addView(tile,lp);
                    tile.setOnClickListener(v->{
                        if(t.accentIndex==index)return;
                        t.accentIndex=index;t.save(this);t=new ThemePrefs(this);settings();
                    });
                }
            }
        });
        addSettingsSection(content,"motion","Анимация и отклик","Плавность и тактильная обратная связь","✦",body->{
            settingsToggle(body,"Плавные анимации","Переходы карточек и смена состояний",t.animations,on->{t.animations=on;t.save(this);});
            settingsToggle(body,"Тактильный отклик","Короткая вибрация при выборе",t.haptics,on->{t.haptics=on;t.save(this);});
        });
        addSettingsSection(content,"ota","Обновления приложения","OTA прямо в приложении, без браузера","↻",body->{
            LinearLayout updateTile=vertical();updateTile.setPadding(dp(14),dp(14),dp(14),dp(14));
            updateTile.setBackground(outlined(t.soft,t.stroke,14));
            body.addView(updateTile,margins(-1,-2,0,6));
            updateTile.addView(text("БПЛА ИНФО  |  "+appVersion(),16,t.fg,true));
            updateTile.addView(text("Проверка и установка обновлений через защищённый канал",12,t.muted,false),margins(-1,-2,0,8));
            updateTile.addView(button("↻  ПРОВЕРИТЬ ОБНОВЛЕНИЯ",true,()->new OtaUpdater(this).check(false)),margins(-1,48,0,0));
        });
        addSettingsSection(content,"about","О приложении","Версия и управление каталогом","ⓘ",body->{
            body.addView(text("БПЛА ИНФО",18,t.fg,true));
            body.addView(text("Версия "+appVersion()+"  |  INFEND",12,t.muted,false),margins(-1,-2,0,6));
            body.addView(text("Локальный справочник БПЛА. Управление каталогом, импорт и экспорт доступны с главного экрана.",13,t.fg,false));
        });
    }
    private int contrast(int color){
        return (.299*Color.red(color)+.587*Color.green(color)+.114*Color.blue(color))>150?0xff0a1520:0xffffffff;
    }
    private GradientDrawable outlined(int color,int border,int radius){
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),border);
        return bg;
    }
    private interface SectionRenderer {void render(LinearLayout body);}
    private void addSettingsSection(LinearLayout into,String id,String heading,String subtitle,String symbol,SectionRenderer renderer){
        boolean opened=activeSettingsSection.equals(id);
        LinearLayout wrapper=vertical();wrapper.setBackground(outlined(t.panel,t.stroke,20));
        into.addView(wrapper,margins(-1,-2,0,7));
        LinearLayout header=horizontal();header.setPadding(dp(14),dp(14),dp(14),dp(14));
        wrapper.addView(header);
        SettingsIconView icon=new SettingsIconView(this,id,opened?t.accent:t.muted);
        icon.setBackground(outlined(t.soft,t.stroke,13));
        icon.setPadding(dp(9),dp(9),dp(9),dp(9));
        header.addView(icon,margins(45,45,0,0));
        LinearLayout labels=vertical();labels.setPadding(dp(12),0,0,0);
        header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text(heading,16,t.fg,true));
        labels.addView(text(subtitle,11,t.muted,false));
        TextView arrow=text(opened?"⌃":"⌄",21,opened?t.accent:t.muted,true);
        header.addView(arrow);
        LinearLayout body=vertical();body.setPadding(dp(14),0,dp(14),dp(14));
        wrapper.addView(body);renderer.render(body);
        body.setVisibility(opened?View.VISIBLE:View.GONE);
        header.setOnClickListener(v->{
            if(t.haptics)v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            boolean expand=body.getVisibility()!=View.VISIBLE;
            for(LinearLayout item:settingsBodies.values()){
                if(item!=body){
                    item.animate().cancel();
                    item.setVisibility(View.GONE);
                    item.setAlpha(1f);
                    item.setTranslationY(0f);
                }
            }
            for(TextView item:settingsArrows.values())item.setText("⌄");
            body.animate().cancel();
            if(expand){
                body.setVisibility(View.VISIBLE);
                arrow.setText("⌃");
                activeSettingsSection=id;
                if(t.animations){
                    body.setAlpha(0f);body.setTranslationY(-dp(8));
                    body.animate().alpha(1f).translationY(0f).setDuration(190).start();
                }else{
                    body.setAlpha(1f);body.setTranslationY(0f);
                }
            }else{
                body.setVisibility(View.GONE);
                body.setAlpha(1f);body.setTranslationY(0f);
                arrow.setText("⌄");
                activeSettingsSection="";
            }
        });
        settingsBodies.put(id,body);
        settingsArrows.put(id,arrow);
    }
    private interface ToggleHandler {void set(boolean selected);}
    private void settingsToggle(LinearLayout parent,String label,String description,boolean initial,ToggleHandler onChange){
        LinearLayout row=horizontal();row.setPadding(dp(5),dp(12),dp(5),dp(12));
        parent.addView(row,margins(-1,-2,0,4));
        LinearLayout labels=vertical();row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(text(label,14,t.fg,true));
        labels.addView(text(description,11,t.muted,false));
        Switch toggle=new Switch(this);toggle.setChecked(initial);
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(t.accent));
        toggle.setOnCheckedChangeListener((button,isChecked)->onChange.set(isChecked));
        row.addView(toggle);
        row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));
    }
    private void openPhotoPicker(Drone d){
        pendingImage=d;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_IMAGE);
    }
    private boolean savePickedPhoto(Uri uri,Drone drone){
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String value=uri.toString();
            if(!drone.galleryUris.contains(value)&&drone.galleryUris.size()<15)drone.galleryUris.add(value);
            if(drone.imageUri.isEmpty())drone.imageUri=value;
            return true;
        }catch(Exception e){return false;}
    }
    private void showFullBitmap(android.graphics.Bitmap bitmap){
        if(bitmap==null)return;
        ImageView viewer=new ImageView(this);
        viewer.setBackgroundColor(t.bg);
        viewer.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.setImageBitmap(bitmap);
        android.app.Dialog dialog=new android.app.Dialog(this);
        dialog.setContentView(viewer);
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(-1,-1);
        }
        viewer.setOnClickListener(v->dialog.dismiss());
    }
    private void showDocumentaryPhoto(PhotoLibrary.Photo item){
        android.app.Dialog overlay=new android.app.Dialog(this);
        LinearLayout content=vertical();
        content.setPadding(dp(13),dp(13),dp(13),dp(22));
        content.setBackgroundColor(t.bg);
        LinearLayout bar=horizontal();content.addView(bar,margins(-1,47,0,0));
        View blank=new View(this);
        bar.addView(blank,new LinearLayout.LayoutParams(0,-1,1));
        bar.addView(button("✕",false,overlay::dismiss),margins(45,40,0,0));
        ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setImageResource(R.drawable.ic_airplane);photo.setColorFilter(t.muted);
        // A weighted zero-height child inside a wrap-content dialog can render
        // as an invisible image on some Android window implementations.
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        int photoHeight=Math.max(dp(240),Math.min((int)(screenHeight*0.68f),dp(760)));
        content.addView(photo,new LinearLayout.LayoutParams(-1,photoHeight));
        String credit="ФОТО ВСТРОЕНО В ПРИЛОЖЕНИЕ";
        if(!item.author.isEmpty())credit+=" | "+item.author;
        if(!item.license.isEmpty())credit+=" | "+item.license;
        if(!item.source.isEmpty())credit+=" | "+item.source;
        TextView attribution=text(credit,12,t.muted,false);
        attribution.setPadding(dp(4),dp(8),dp(4),dp(8));
        content.addView(attribution,margins(-1,-2,0,0));
        TextView loadStatus=text("ОТКРЫТИЕ ФОТО…",11,t.muted,false);
        loadStatus.setGravity(Gravity.CENTER);
        content.addView(loadStatus,margins(-1,30,0,4));
        // Retry local decoding without closing the viewer; no network is involved.
        Runnable retry=()->{
            loadStatus.setText("ОТКРЫТИЕ ФОТО…");
            PhotoLibrary.into(this,item,photo,2800,ok->
                loadStatus.setText(ok?"СНИМОК ЗАГРУЖЕН":PhotoLibrary.unavailableMessage(this)));
        };
        photo.setOnClickListener(v->retry.run());
        loadStatus.setOnClickListener(v->retry.run());
        overlay.setContentView(content);
        overlay.show();
        if(overlay.getWindow()!=null){
            overlay.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            overlay.getWindow().setLayout(-1,-1);
        }
        retry.run();
    }
    private void showFullPhoto(String uri){
        ImageView viewer=new ImageView(this);viewer.setBackgroundColor(t.bg);
        viewer.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try{viewer.setImageURI(Uri.parse(uri));}catch(Exception ignored){}
        android.app.Dialog dialog=new android.app.Dialog(this);
        dialog.setContentView(viewer);
        android.view.Window win=dialog.getWindow();
        if(win!=null){win.setBackgroundDrawableResource(android.R.color.transparent);}
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
        viewer.setOnClickListener(v->dialog.dismiss());
    }
    private void importFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT_JSON);}
    private void exportFile(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"BPLA-INFO-catalog.json");startActivityForResult(i,EXPORT_JSON);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        Uri uri=data.getData();
        if(request==PICK_IMAGE&&pendingImage!=null){
            int added=0;
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount()&&i<15;i++){
                    Uri photo=data.getClipData().getItemAt(i).getUri();
                    added+=savePickedPhoto(photo,pendingImage)?1:0;
                }
            }else if(uri!=null)added+=savePickedPhoto(uri,pendingImage)?1:0;
            save();toast("Добавлено фото: "+added);detail(pendingImage);return;
        }
        if(uri==null)return;
        try{
            if(request==EXPORT_JSON){
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Нет доступа");out.write(DroneStore.exportJson(records).getBytes(StandardCharsets.UTF_8));}
                toast("Каталог экспортирован");
            } else if(request==IMPORT_JSON){
                byte[] content;
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Нет доступа");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>=0){out.write(buf,0,n);if(out.size()>2_000_000)throw new IOException("Файл слишком большой");}content=out.toByteArray();}
                int n=DroneStore.importJson(this,records,new String(content,StandardCharsets.UTF_8));toast("Импортировано / обновлено: "+n);home();
            }
        }catch(Exception e){UiDialogs.message(this,t,"Ошибка файла",e.getMessage()==null?"Не удалось прочитать файл":e.getMessage());}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}catch(Exception ignored){}}
    @Override protected void onResume(){super.onResume();OtaUpdater.resumePending(this);}
    @Override public void onBackPressed(){if(showingDetail||showingSettings){home();return;}super.onBackPressed();}
}
