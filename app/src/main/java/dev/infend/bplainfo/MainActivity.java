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
    private static final int CARD_BATCH_SIZE=18;
    private String searchTerm="",category="Все",countryFilter="",roleFilter="";
    private LinearLayout homeRoot,homeListHost;
    private ScrollView homeScroll;
    private boolean onlyFavorites=false,onlyWithPhotos=false,descending=false,showingDetail=false;
    private int sortBy=0;
    private Drone pendingImage;
    private final String[] sortNames={"По названию","По производителю","По скорости","По дальности","По массе","По размаху крыла","По автономности"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        StartupGuard.installFatalRecorder(this);
        t=new ThemePrefs(this);
        if(StartupGuard.wasInterrupted(this)){
            showRecoveryScreen();
            return;
        }
        StartupGuard.begin(this);
        setup();
        LinearLayout loading=vertical();
        loading.setPadding(dp(22),dp(32),dp(22),dp(24));
        loading.addView(text("ЗАГРУЗКА КАТАЛОГА",18,t.fg,true));
        loading.addView(text("Подготовка карточек БПЛА…",13,t.muted,false));
        root.addView(loading);
        new Thread(()->{
            try{
                Context app=getApplicationContext();
                try{PhotoLibrary.purgeLegacyCache(app);}
                catch(RuntimeException cacheError){
                    android.util.Log.w("BPLA-INFO","Old cache cleanup skipped",cacheError);
                }
                ArrayList<Drone> loaded=DroneStore.load(app);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    try{
                        records.clear();
                        records.addAll(loaded);
                        home();
                    }catch(Throwable uiError){
                        showStartupFailure("Отображение каталога",uiError);
                        return;
                    }
                    try{
                        long last=getPreferences(0).getLong("ota_checked",0);
                        if(System.currentTimeMillis()-last>24*60*60*1000L){
                            getPreferences(0).edit().putLong("ota_checked",System.currentTimeMillis()).apply();
                            new OtaUpdater(this).check(true);
                        }
                    }catch(Throwable updateError){
                        android.util.Log.w("BPLA-INFO","Background OTA check skipped",updateError);
                    }
                });
            }catch(Throwable failure){
                StartupGuard.capture(getApplicationContext(),"Чтение/миграция каталога",failure);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    showStartupFailure("Чтение/миграция каталога",failure);
                });
            }
        },"bpla-catalog-load").start();
    }

    /**
     * Safe screen after a previous crash / Android process kill while loading.
     * Never auto-repeat an unsafe migration. No database writes or deletions.
     */
    private void showRecoveryScreen(){
        setup();
        LinearLayout pane=vertical();
        pane.setPadding(dp(22),dp(25),dp(22),dp(24));
        scroll(root).addView(pane);
        pane.addView(text("РЕЖИМ ВОССТАНОВЛЕНИЯ",20,t.fg,true));
        pane.addView(text("Предыдущая загрузка каталога была прервана. "+
            "Данные приложения, избранное и локальные фотографии не удалялись.",14,t.muted,false));
        pane.addView(text("Автоматический повтор отключён, чтобы избежать цикла вылетов.",13,t.muted,false));
        pane.addView(button("СКОПИРОВАТЬ ОТЧЁТ",true,()->copyStartupReport()));
        pane.addView(button("ПОСМОТРЕТЬ СОХРАНЁННЫЕ НАЗВАНИЯ",false,this::showRecoveryPreview));
        pane.addView(button("ПОВТОРИТЬ ЗАГРУЗКУ",false,()->{
            StartupGuard.completed(this);
            recreate();
        }));
    }

    private void copyStartupReport(){
        String report=StartupGuard.report(this);
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
            getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("БПЛА ИНФО, диагностика",report));
            Toast.makeText(this,"Отчёт скопирован",Toast.LENGTH_SHORT).show();
        }
    }

    private void showRecoveryPreview(){
        setup();
        LinearLayout pane=vertical();
        pane.setPadding(dp(20),dp(24),dp(20),dp(20));
        scroll(root).addView(pane);
        pane.addView(text("СОХРАНЁННЫЕ МОДЕЛИ",19,t.fg,true));
        pane.addView(text("Только чтение. Миграция и сохранение отключены.",13,t.muted,false));
        pane.addView(text("Чтение…",13,t.muted,false));
        new Thread(()->{
            try{
                ArrayList<String> names=DroneStore.readOnlyPreview(getApplicationContext(),80);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    pane.removeViewAt(pane.getChildCount()-1);
                    for(String name:names)pane.addView(text(name,14,t.fg,false));
                    if(names.isEmpty())pane.addView(text("Нет доступных записей",14,t.muted,false));
                    pane.addView(button("НАЗАД",false,this::showRecoveryScreen));
                });
            }catch(Throwable error){
                StartupGuard.capture(this,"Чтение сохранённых названий",error);
                runOnUiThread(()->showRecoveryScreen());
            }
        },"bpla-recovery-preview").start();
    }
    /** A recoverable screen replaces a process crash without touching local records. */
    private void showStartupFailure(String stage,Throwable failure){
        android.util.Log.e("BPLA-INFO","Startup failure: "+stage,failure);
        StartupGuard.capture(this,stage,failure);
        setup();
        LinearLayout pane=vertical();
        pane.setPadding(dp(22),dp(34),dp(22),dp(25));
        scroll(root).addView(pane);
        pane.addView(text("ОШИБКА ЗАГРУЗКИ",19,t.fg,true));
        pane.addView(text("Данные и фотографии не удалены.",13,t.muted,false));
        pane.addView(text("Этап: "+stage+" | "+failure.getClass().getSimpleName(),12,t.muted,false));
        pane.addView(button("СКОПИРОВАТЬ ОТЧЁТ",true,this::copyStartupReport));
        pane.addView(button("ОТКРЫТЬ ВОССТАНОВЛЕНИЕ",false,this::showRecoveryScreen));
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
    private View navIcon(String kind,Runnable click){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(panel(t.panel,14));
        SettingsIconView symbol=new SettingsIconView(this,kind,t.accent);
        symbol.setPadding(dp(9),dp(9),dp(9),dp(9));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER);
        frame.addView(symbol,lp);
        frame.setContentDescription(kind.equals("settings")?"Настройки":"Назад");
        frame.setOnClickListener(v->{
            if(t.haptics)v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if(t.animations){
                symbol.animate().scaleX(.86f).scaleY(.86f).rotationBy(kind.equals("settings")?45f:0f)
                    .setDuration(110).withEndAction(()->{
                        symbol.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                        click.run();
                    }).start();
            }else click.run();
        });
        return frame;
    }
    private void titleBar(String title,String hint,Runnable back){
        LinearLayout bar=horizontal();bar.setPadding(dp(18),dp(18),dp(18),dp(12));root.addView(bar);
        if(back!=null)bar.addView(navIcon("back",back),margins(44,44,0,0));
        LinearLayout titles=vertical();
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);
        titleLp.leftMargin=dp(back==null?0:11);bar.addView(titles,titleLp);
        titles.addView(text(title,23,t.fg,true));
        titles.addView(text(hint,11,t.muted,false));
        if(back==null)bar.addView(navIcon("settings",this::settings),margins(44,44,4,0));
    }
    private void returnToHome(){
        if(homeRoot==null){home();return;}
        hideKeyboard();
        showingDetail=false;showingSettings=false;
        root=homeRoot;listHost=homeListHost;
        setContentView(homeRoot);
        if(Build.VERSION.SDK_INT>=30)root.requestApplyInsets();
    }
    private void chooseCategory(String selected,LinearLayout chips){
        category=selected;
        for(int i=0;i<chips.getChildCount();i++){
            View child=chips.getChildAt(i);
            if(!(child instanceof TextView))continue;
            TextView chip=(TextView)child;
            boolean active=selected.contentEquals(chip.getText());
            chip.setTextColor(active?(t.mode==2?Color.WHITE:0xFF06140C):t.fg);
            chip.setBackground(panel(active?t.accent:t.panel,14));
        }
        cards();
    }
    private void home(){
        showingDetail=false;showingSettings=false;activeSettingsSection="";
        setup();
        homeRoot=root;
        titleBar("БПЛА  ИНФО","Каталог БПЛА",null);
        LinearLayout inner=vertical();inner.setPadding(dp(14),0,dp(14),dp(30));
        ScrollView sc=scroll(root);sc.addView(inner);
        homeScroll=sc;
        LinearLayout controls=horizontal();inner.addView(controls,margins(-1,-2,0,9));
        controls.addView(button("+ ДОБАВИТЬ",true,()->edit(null)),new LinearLayout.LayoutParams(0,dp(43),1));
        controls.addView(button("⇅ СОРТИРОВКА",false,this::sortDialog),margins(-2,43,5,0));
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
        for(String group:new String[]{"Все","Самолётный","Коптерный"}){
            TextView chip=button(group,category.equals(group),()->chooseCategory(group,chips));
            chips.addView(chip,margins(-2,39,0,0));
        }
        LinearLayout heading=horizontal();inner.addView(heading,margins(-1,-2,3,3));
        TextView count=text("",13,t.muted,true);count.setId(1234001);heading.addView(count,new LinearLayout.LayoutParams(0,dp(31),1));
        listHost=vertical();inner.addView(listHost);
        homeListHost=listHost;
        cards();
    }
    /** Include only real bundled APK photos and locally attached photos, never remote URLs. */
    private boolean hasAttachedPhoto(Drone d){
        for(String image:d.galleryUris){
            if(isLocalPhotoUri(image))return true;
        }
        if(isLocalPhotoUri(d.imageUri))return true;
        return !PhotoLibrary.forDrone(this,d).isEmpty();
    }
    private static boolean isLocalPhotoUri(String uri){
        if(uri==null)return false;
        String value=uri.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("content://")||value.startsWith("file://");
    }
    private List<Drone> filtered(){
        ArrayList<Drone> a=new ArrayList<>();
        String q=searchTerm.toLowerCase(Locale.ROOT).trim();
        java.util.LinkedHashMap<String,Drone> unique=new java.util.LinkedHashMap<>();
        for(Drone d:records){
            if(!CardPresentation.isAir(d.type(),d.sourceGroup))continue;
            String kind=CatalogFacets.airframe(d.type(),d.sourceGroup,d.name());
            if(category.equals("Самолётный")&&!kind.equals("Самолётный"))continue;
            if(category.equals("Коптерный")&&!kind.equals("Коптерный"))continue;
            if(onlyFavorites&&!d.favorite)continue;
            if(onlyWithPhotos&&!hasAttachedPhoto(d))continue;
            if(!countryFilter.isEmpty()&&!countryFilter.equals(CatalogFacets.country(d.specRows)))continue;
            if(!roleFilter.isEmpty()&&!roleFilter.equals(CatalogFacets.role(d.type(),d.sourceGroup,d.specRows)))continue;
            if(!q.isEmpty()){
                boolean match=false;
                for(String field:d.values){if(field!=null&&field.toLowerCase(Locale.ROOT).contains(q)){match=true;break;}}
                if(!match)for(String[] row:d.specRows){
                    if(row.length>=2&&(row[0].toLowerCase(Locale.ROOT).contains(q)||row[1].toLowerCase(Locale.ROOT).contains(q))){match=true;break;}
                }
                if(!match)continue;
            }
            String canonical=CatalogFacets.canonicalName(CardPresentation.name(d.name()));
            if(canonical.isEmpty())canonical=d.id;
            Drone previous=unique.get(canonical);
            if(previous==null||quality(d)>quality(previous))unique.put(canonical,d);
        }
        a.addAll(unique.values());
        a.sort((x,y)->{
            int result;
            if(sortBy>=2){
                int field=sortBy==2?5:sortBy==3?6:sortBy==4?10:3;
                double vx=Drone.numeric(sortBy==2?x.previewSpeed():sortBy==3?x.previewRange():
                    sortBy==6?x.previewEndurance():x.get(field));
                double vy=Drone.numeric(sortBy==2?y.previewSpeed():sortBy==3?y.previewRange():
                    sortBy==6?y.previewEndurance():y.get(field));
                if(vx<0&&vy>=0)return 1;
                if(vy<0&&vx>=0)return -1;
                result=Double.compare(vx,vy);
            }else result=(sortBy==0?x.name():x.manufacturer()).compareToIgnoreCase(sortBy==0?y.name():y.manufacturer());
            return descending?-result:result;
        });
        return a;
    }
    private int quality(Drone d){
        int score=Math.min(120,d.specRows.size())*2;
        if(d.id.startsWith("lostarmour-pdf-2024-"))score+=30;
        if(d.id.startsWith("bulletin30-"))score+=18;
        if(d.favorite)score+=5;
        score+=Math.min(10,d.galleryUris.size())*3;
        return score;
    }
    private void cards(){
        if(listHost==null)return;
        final LinearLayout target=listHost;
        final int epoch=++cardRenderEpoch;
        target.removeAllViews();
        List<Drone> found=filtered();
        View parent=(View)listHost.getParent();
        if(parent!=null){View count=((View)parent).getRootView().findViewById(1234001);if(count instanceof TextView)((TextView)count).setText("НАЙДЕНО: "+found.size()+"   |   ВСЕГО: "+records.stream().filter(d->CardPresentation.isAir(d.type(),d.sourceGroup)).count());}
        if(found.isEmpty()){
            LinearLayout empty=vertical();empty.setPadding(dp(20),dp(36),dp(20),dp(36));empty.setGravity(Gravity.CENTER);
            empty.setBackground(panel(t.panel,22));
            empty.addView(text("⌕  Карточки не найдены",18,t.fg,true));
            empty.addView(text(onlyWithPhotos?"Для выбранных моделей пока нет фотографий":"Измени фильтр или создай новую запись",13,t.muted,false));
            listHost.addView(empty,margins(-1,-2,0,8));StartupGuard.completed(this);return;
        }
        // Render after the first UI frame; never construct hundreds of cards in one run.
        target.post(()->{
            if(epoch!=cardRenderEpoch||target!=listHost)return;
            try{renderCardBatch(target,found,0,epoch);}
            catch(RuntimeException error){showStartupFailure("Список моделей",error);}
        });
    }
    private void renderCardBatch(LinearLayout target,List<Drone> found,int offset,int epoch){
        if(epoch!=cardRenderEpoch||target!=listHost||isFinishing()||isDestroyed())return;
        int end=CatalogPaging.end(offset,found.size(),CARD_BATCH_SIZE);
        for(int index=offset;index<end;index++){
            Drone d=found.get(index);
            LinearLayout card=vertical();card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(panel(t.panel,20));
            listHost.addView(card,margins(-1,-2,0,5));
            LinearLayout head=horizontal();card.addView(head);
            ImageView typeIcon=new ImageView(this);
            String type=CatalogFacets.airframe(d.type(),d.sourceGroup,d.name());
            typeIcon.setImageResource(type.equals("Коптерный")?R.drawable.ic_multicopter:R.drawable.ic_airplane);
            typeIcon.setColorFilter(t.accent);
            typeIcon.setPadding(dp(4),dp(4),dp(4),dp(4));
            LinearLayout.LayoutParams iconLayout=margins(32,32,0,0);
            iconLayout.rightMargin=dp(11);
            head.addView(typeIcon,iconLayout);
            LinearLayout labels=vertical();head.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            labels.addView(text(CardPresentation.name(d.name()),20,t.fg,true));
            String engine=CardPresentation.engine(d.engineType());
            String modelType=CatalogFacets.airframe(d.type(),d.sourceGroup,d.name());
            String subtitle=engine.isEmpty()?modelType:modelType+" | "+engine;
            labels.addView(text(subtitle,13,t.fg,true));
            LinearLayout stats=horizontal();card.addView(stats);
            stat(stats,"АВТОНОМНОСТЬ",PreviewFormatter.endurance(d.previewEndurance(),d.specRows),Gravity.START);
            stat(stats,"СКОРОСТЬ",PreviewFormatter.speed(d.previewSpeed(),d.specRows),Gravity.CENTER_HORIZONTAL);
            stat(stats,"ДАЛЬНОСТЬ",PreviewFormatter.range(d.previewRange(),d.specRows),Gravity.END);
            card.setOnClickListener(v->detail(d));
            if(t.animations){card.setAlpha(0f);card.setTranslationY(dp(13));card.animate().alpha(1).translationY(0).setDuration(180).setStartDelay(Math.min(index,8)*25L).start();}
        }
        if(offset==0)target.post(()->StartupGuard.completed(getApplicationContext()));
        if(end<found.size()){
            final LinearLayout footer=vertical();
            target.addView(footer,margins(-1,-2,0,8));
            footer.addView(button("ПОКАЗАТЬ ЕЩЁ ("+end+" / "+found.size()+")",false,()->{
                if(epoch!=cardRenderEpoch||target!=listHost)return;
                target.removeView(footer);
                try{renderCardBatch(target,found,end,epoch);}
                catch(RuntimeException error){showStartupFailure("Продолжение списка",error);}
            }),margins(-1,44,0,4));
        }
    }
    private void stat(LinearLayout row,String label,String value,int alignment){
        String formatted=SpecFormatter.display(value);
        if(formatted.isEmpty())formatted="—";
        LinearLayout group=vertical();
        group.setPadding(0,dp(3),0,dp(2));
        row.addView(group,new LinearLayout.LayoutParams(0,-2,1));
        TextView caption=text(label,9,t.muted,true);
        caption.setGravity(alignment|Gravity.CENTER_VERTICAL);
        caption.setMinHeight(dp(18));
        caption.setSingleLine(true);
        group.addView(caption,new LinearLayout.LayoutParams(-1,-2));
        TextView amount=text(formatted,13,t.accent,true);
        amount.setGravity(alignment|Gravity.TOP);
        amount.setMaxLines(2);
        group.addView(amount,new LinearLayout.LayoutParams(-1,-2));
    }
    private void save(){DroneStore.save(this,records);}
    /** Edge-to-edge rounded thumbnail with a 6dp gutter. The bitmap itself is not stretched. */
    private FrameLayout addPhotoThumbnail(LinearLayout gallery){
        FrameLayout frame=new FrameLayout(this);
        frame.setBackground(outlined(t.soft,t.stroke,14));
        frame.setClipToOutline(true);
        // Draw the hairline border on top of the photo rather than behind it.
        frame.setForeground(outlined(Color.TRANSPARENT,t.stroke,14));
        LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(dp(250),dp(190));
        position.rightMargin=dp(6);
        gallery.addView(frame,position);
        return frame;
    }
    private ImageView addThumbnailImage(FrameLayout frame){
        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photo.setAdjustViewBounds(false);
        frame.addView(photo,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        return photo;
    }
    private void detail(Drone d){
        hideKeyboard();showingDetail=true;setup();
        titleBar(CardPresentation.name(d.name()),"ТЕХНИЧЕСКАЯ КАРТОЧКА",this::returnToHome);
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
            photos.addView(strip,margins(-1,196,0,5));
            LinearLayout gallery=horizontal();strip.addView(gallery);
            // Decode only locally bundled photos that are visible in the strip.
            // No image download or remote photo URLs are used in this application.
            java.util.ArrayList<View> lazyFrames=new java.util.ArrayList<>();
            java.util.ArrayList<Runnable> lazyLoads=new java.util.ArrayList<>();
            int photoNumber=documentary.size();
            for(PhotoLibrary.Photo p:documentary){
                FrameLayout frame=addPhotoThumbnail(gallery);
                ImageView photo=addThumbnailImage(frame);
                photo.setImageResource(R.drawable.ic_airplane);
                photo.setColorFilter(t.muted);
                final boolean[] requested={false};
                lazyFrames.add(frame);
                lazyLoads.add(()->{
                    if(requested[0])return;
                    requested[0]=true;
                    PhotoLibrary.into(this,p,photo,1350);
                });
                frame.setOnClickListener(v->showDocumentaryPhoto(p));
            }
            for(String path:images){
                if(photoNumber++>=15)break;
                FrameLayout frame=addPhotoThumbnail(gallery);
                ImageView photo=addThumbnailImage(frame);
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
        TextView favoriteAction=button(d.favorite?"★ В ИЗБРАННОМ":"☆ ИЗБРАННОЕ",false,()->{});
        favoriteAction.setOnClickListener(v->{
            d.favorite=!d.favorite;save();
            favoriteAction.setText(d.favorite?"★ В ИЗБРАННОМ":"☆ ИЗБРАННОЕ");
            if(t.haptics)v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if(t.animations)v.animate().scaleX(.96f).scaleY(.96f).setDuration(90)
                .withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()).start();
        });
        actions.addView(favoriteAction,new LinearLayout.LayoutParams(0,-1,1));
        actions.addView(button("✎ ИЗМЕНИТЬ",true,()->edit(d)),margins(-2,-1,4,0));
        actions.addView(button("⋮",false,()->more(d)),margins(45,-1,4,0));
        LinearLayout table=vertical();table.setPadding(dp(15),dp(14),dp(15),dp(14));table.setBackground(panel(t.panel,19));
        content.addView(table,margins(-1,-2,0,6));table.addView(text("ХАРАКТЕРИСТИКИ БПЛА",15,t.accent,true),margins(-1,-2,0,9));
        // Consistent order for base fields, PDF fields and locally edited cards.
        for(String[] item:CardPresentation.rows(d.values,d.specRows)){
            LinearLayout row=vertical();row.setPadding(0,dp(6),0,dp(8));table.addView(row);
            row.addView(text(item[0],10,t.muted,true));
            row.addView(text(item[1],15,t.fg,true));
            View divider=new View(this);divider.setBackgroundColor(t.stroke);
            table.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
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
            if(!CardPresentation.isAir(d.type(),d.sourceGroup)){
                if(inputs[1]!=null)inputs[1].setError("Допускаются только авиационные БПЛА");
                return false;
            }
            if(existing==null)records.add(d);
            save();
            detail(d);
            return true;
        });
    }
    private void sortDialog(){
        SortSheet.State state=new SortSheet.State();
        state.sortBy=sortBy;
        state.descending=descending;
        state.favorites=onlyFavorites;
        state.photos=onlyWithPhotos;
        state.country=countryFilter;
        state.role=roleFilter;
        java.util.TreeSet<String> countries=new java.util.TreeSet<>();
        for(Drone d:records){
            String country=CatalogFacets.country(d.specRows);
            if(!country.isEmpty())countries.add(country);
        }
        java.util.ArrayList<String> options=new java.util.ArrayList<>();
        options.add("");
        options.addAll(countries);
        SortSheet.open(this,t,state,options,()->{
            sortBy=state.sortBy;descending=state.descending;
            onlyFavorites=state.favorites;onlyWithPhotos=state.photos;
            countryFilter=state.country;roleFilter=state.role;
            cards();
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
        titleBar("НАСТРОЙКИ","ПЕРСОНАЛИЗАЦИЯ  /  БПЛА ИНФО",this::returnToHome);
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
                tile.addView(glyph,margins(30,30,0,7));
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
        header.addView(icon,margins(41,41,0,0));
        LinearLayout labels=vertical();labels.setPadding(dp(15),0,0,0);
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
    /** Photo provenance stays in bundled metadata, not overlaid on the image viewer. */
    private void showDocumentaryPhoto(PhotoLibrary.Photo item){
        android.app.Dialog overlay=new android.app.Dialog(this);
        FrameLayout content=new FrameLayout(this);
        content.setBackgroundColor(t.bg);
        ImageView photo=new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setImageResource(R.drawable.ic_airplane);
        photo.setColorFilter(t.muted);
        content.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        // Keep only a close control. No credits, source/license buttons or load banners.
        android.widget.TextView close=text("✕",24,t.fg,true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(panel(t.panel,16));
        FrameLayout.LayoutParams closeParams=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);
        closeParams.setMargins(0,dp(12),dp(12),0);
        content.addView(close,closeParams);
        close.setOnClickListener(v->overlay.dismiss());
        overlay.setContentView(content);
        overlay.show();
        if(overlay.getWindow()!=null){
            overlay.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            overlay.getWindow().setLayout(-1,-1);
        }
        // Original bytes are loaded locally; tap the image to retry if decoding failed.
        Runnable retry=()->PhotoLibrary.into(this,item,photo,2800,ok->{
            if(!ok)android.widget.Toast.makeText(this,PhotoLibrary.unavailableMessage(this),android.widget.Toast.LENGTH_SHORT).show();
        });
        photo.setOnClickListener(v->retry.run());
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
    @Override public void onBackPressed(){
        if(showingDetail||showingSettings){returnToHome();return;}
        super.onBackPressed();
    }
}
