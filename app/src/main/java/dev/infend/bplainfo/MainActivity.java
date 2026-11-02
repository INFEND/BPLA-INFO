package dev.infend.bplainfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
    private String searchTerm="",category="Все";
    private boolean onlyFavorites=false,descending=false,showingDetail=false;
    private int sortBy=0;
    private Drone pendingImage;
    private final String[] sortNames={"По названию","По производителю","По скорости","По дальности","По массе","По размаху крыла"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        t=new ThemePrefs(this);
        records.addAll(DroneStore.load(this));
        home();
        long last=getPreferences(0).getLong("ota_checked",0);
        if(System.currentTimeMillis()-last>24*60*60*1000L){
            getPreferences(0).edit().putLong("ota_checked",System.currentTimeMillis()).apply();
            new OtaUpdater(this).check(true);
        }
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
        getWindow().setStatusBarColor(t.bg);getWindow().setNavigationBarColor(t.bg);
        int flags=t.mode==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        root=vertical();root.setBackgroundColor(t.bg);
        setContentView(root);
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
        showingDetail=false;
        setup();
        titleBar("БПЛА  ИНФО","КАТАЛОГ  /  СПРАВОЧНИК  /  INFEND",null);
        LinearLayout inner=vertical();inner.setPadding(dp(14),0,dp(14),dp(30));
        ScrollView sc=scroll(root);sc.addView(inner);
        LinearLayout hero=vertical();hero.setPadding(dp(18),dp(17),dp(18),dp(14));
        hero.setBackground(panel(t.panel,22));inner.addView(hero,margins(-1,-2,0,4));
        hero.addView(text("АВИАЦИОННЫЙ АРХИВ",11,t.accent,true));
        hero.addView(text("Исследуй. Сравнивай. Сохраняй.",18,t.fg,true),margins(-1,-2,0,6));
        hero.addView(text("Личный справочник с редактированием и офлайн-доступом",12,t.muted,false));
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
        for(String group:new String[]{"Все","Самолётный","Мультикоптер","Другие"}){
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
            String kind=d.type().toLowerCase(Locale.ROOT);
            if(category.equals("Самолётный")&&!kind.contains("самол"))continue;
            if(category.equals("Мультикоптер")&&!(kind.contains("коптер")||kind.contains("мульт")||kind.contains("квадр")))continue;
            if(category.equals("Другие")&&(kind.contains("самол")||kind.contains("коптер")||kind.contains("мульт")||kind.contains("квадр")))continue;
            if(!q.isEmpty()){
                boolean match=false;
                for(String field:d.values){if(field!=null&&field.toLowerCase(Locale.ROOT).contains(q)){match=true;break;}}
                if(!match)continue;
            }
            a.add(d);
        }
        a.sort((x,y)->{
            int result;
            if(sortBy>=2){
                int field=sortBy==2?5:sortBy==3?6:sortBy==4?10:3;
                double vx=Drone.numeric(x.get(field)),vy=Drone.numeric(y.get(field));
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
        listHost.removeAllViews();
        List<Drone> found=filtered();
        View parent=(View)listHost.getParent();
        if(parent!=null){View count=((View)parent).getRootView().findViewById(1234001);if(count instanceof TextView)((TextView)count).setText("НАЙДЕНО: "+found.size()+"   ·   ВСЕГО: "+records.size());}
        if(found.isEmpty()){
            LinearLayout empty=vertical();empty.setPadding(dp(20),dp(36),dp(20),dp(36));empty.setGravity(Gravity.CENTER);
            empty.setBackground(panel(t.panel,22));
            empty.addView(text("⌕  Карточки не найдены",18,t.fg,true));
            empty.addView(text("Измени фильтр или создай новую запись",13,t.muted,false));
            listHost.addView(empty,margins(-1,-2,0,8));return;
        }
        int index=0;
        for(Drone d:found){
            LinearLayout card=vertical();card.setPadding(dp(13),dp(12),dp(13),dp(12));card.setBackground(panel(t.panel,20));
            listHost.addView(card,margins(-1,-2,0,5));
            LinearLayout head=horizontal();card.addView(head);
            LinearLayout labels=vertical();head.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            labels.addView(text(d.name(),20,t.fg,true));
            labels.addView(text(d.type()+"  •  "+d.manufacturer(),12,t.muted,false));
            TextView star=button(d.favorite?"★":"☆",false,()->{d.favorite=!d.favorite;save();cards();});
            star.setTextColor(t.accent);head.addView(star,margins(42,42,3,0));
            SilhouetteView graphic=new SilhouetteView(this,t,0);
            graphic.setBackground(panel(t.mode==2?0xFFEAF4EE:0xFF091B15,13));
            card.addView(graphic,margins(-1,112,0,7));
            LinearLayout stats=horizontal();card.addView(stats);
            stat(stats,"РАЗМАХ",d.get(3));
            stat(stats,"СКОРОСТЬ",d.get(5));
            stat(stats,"ДАЛЬНОСТЬ",d.get(6));
            card.setOnClickListener(v->detail(d));
            if(t.animations){card.setAlpha(0f);card.setTranslationY(dp(13));card.animate().alpha(1).translationY(0).setDuration(180).setStartDelay(Math.min(index,8)*25L).start();}
            index++;
        }
    }
    private void stat(LinearLayout row,String label,String value){
        LinearLayout group=vertical();group.setPadding(dp(4),dp(3),dp(3),dp(2));
        row.addView(group,new LinearLayout.LayoutParams(0,-2,1));
        group.addView(text(label,9,t.muted,true));
        group.addView(text(value.isEmpty()?"Н/Д":value,13,t.accent,true));
    }
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(this,t.mode==2?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert);}
    private void save(){DroneStore.save(this,records);}
    private void detail(Drone d){
        hideKeyboard();showingDetail=true;setup();
        titleBar(d.name(),"ТЕХНИЧЕСКАЯ КАРТОЧКА",this::home);
        LinearLayout content=vertical();content.setPadding(dp(15),0,dp(15),dp(26));scroll(root).addView(content);
        LinearLayout visuals=vertical();visuals.setPadding(dp(10),dp(12),dp(10),dp(12));visuals.setBackground(panel(t.panel,22));
        content.addView(visuals,margins(-1,-2,0,6));
        if(!d.imageUri.isEmpty()){
            ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{iv.setImageURI(Uri.parse(d.imageUri));visuals.addView(iv,margins(-1,180,0,6));}
            catch(Exception ex){visuals.addView(text("Фотография недоступна",12,t.muted,false));}
        }
        TextView tag=text("СХЕМАТИЧЕСКИЕ ИЛЛЮСТРАЦИИ · НЕ ЧЕРТЕЖИ",10,t.muted,true);visuals.addView(tag,margins(-1,-2,3,2));
        visuals.addView(text("ВИД СНИЗУ",11,t.accent,true),margins(-1,-2,3,0));
        visuals.addView(new SilhouetteView(this,t,0),margins(-1,160,0,2));
        LinearLayout views=horizontal();visuals.addView(views);
        for(int kind=1;kind<=2;kind++){LinearLayout half=vertical();views.addView(half,new LinearLayout.LayoutParams(0,-2,1));half.addView(text(kind==1?"ВИД СПЕРЕДИ":"ВИД СБОКУ",10,t.muted,true));half.addView(new SilhouetteView(this,t,kind),margins(-1,86,1,2));}
        LinearLayout actions=horizontal();content.addView(actions,margins(-1,49,0,6));
        actions.addView(button(d.favorite?"★ В ИЗБРАННОМ":"☆ ИЗБРАННОЕ",false,()->{d.favorite=!d.favorite;save();detail(d);}),new LinearLayout.LayoutParams(0,-1,1));
        actions.addView(button("✎ ИЗМЕНИТЬ",true,()->edit(d)),margins(-2,-1,4,0));
        actions.addView(button("⋮",false,()->more(d)),margins(45,-1,4,0));
        LinearLayout table=vertical();table.setPadding(dp(15),dp(14),dp(15),dp(14));table.setBackground(panel(t.panel,19));
        content.addView(table,margins(-1,-2,0,6));table.addView(text("ХАРАКТЕРИСТИКИ БПЛА",15,t.accent,true),margins(-1,-2,0,9));
        for(int i=1;i<Drone.FIELDS.length;i++){
            if(d.get(i).isEmpty())continue;
            LinearLayout row=vertical();row.setPadding(0,dp(6),0,dp(8));table.addView(row);
            row.addView(text(Drone.TITLES[i].toUpperCase(Locale.ROOT),10,t.muted,true));
            row.addView(text(d.get(i),15,t.fg,true));
            View divider=new View(this);divider.setBackgroundColor(t.stroke);table.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        content.addView(text("Проверяй данные по первоисточнику. Схемы носят исключительно иллюстративный характер.",11,t.muted,false),margins(-1,-2,4,10));
    }
    private void more(Drone d){
        dialog().setTitle(d.name()).setItems(new String[]{"Добавить / сменить фото","Поделиться карточкой","Удалить карточку"},(dialog,which)->{
            if(which==0){pendingImage=d;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_IMAGE);}
            if(which==1){StringBuilder b=new StringBuilder(d.name()+"\n");for(int i=1;i<Drone.FIELDS.length;i++)if(!d.get(i).isEmpty())b.append(Drone.TITLES[i]).append(": ").append(d.get(i)).append('\n');Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TEXT,b.toString());startActivity(Intent.createChooser(intent,"Поделиться карточкой"));}
            if(which==2)dialog().setTitle("Удалить «"+d.name()+"»?").setMessage("Карточка будет удалена из локального каталога.").setNegativeButton("ОТМЕНА",null).setPositiveButton("УДАЛИТЬ",(dd,w)->{records.remove(d);save();home();}).show();
        }).show();
    }
    private void edit(Drone existing){
        Drone d=existing==null?new Drone():existing;
        ScrollView sc=new ScrollView(this);LinearLayout fields=vertical();fields.setPadding(dp(18),dp(8),dp(18),dp(7));sc.addView(fields);
        EditText[] inputs=new EditText[Drone.FIELDS.length];
        for(int i=0;i<inputs.length;i++){
            fields.addView(text(Drone.TITLES[i],12,t.mode==2?0xFF425348:t.muted,true),margins(-1,-2,0,0));
            EditText e=new EditText(this);e.setTextSize(15);e.setSingleLine(i!=15);e.setText(d.get(i));
            if(i==15)e.setMinLines(2);
            e.setHint(Drone.TITLES[i]);e.setSelectAllOnFocus(false);inputs[i]=e;fields.addView(e,margins(-1,43,0,4));
        }
        AlertDialog dialog=dialog().setTitle(existing==null?"Новая карточка":"Редактирование").setView(sc)
            .setNegativeButton("ОТМЕНА",null).setPositiveButton("СОХРАНИТЬ",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
            if(inputs[0].getText().toString().trim().isEmpty()){inputs[0].setError("Название обязательно");return;}
            for(int i=0;i<inputs.length;i++)d.set(i,inputs[i].getText().toString());
            if(existing==null)records.add(d);
            save();dialog.dismiss();detail(d);
        }));
        dialog.show();
    }
    private void sortDialog(){
        String[] names=new String[sortNames.length+1];System.arraycopy(sortNames,0,names,0,sortNames.length);names[sortNames.length]=descending?"Направление: ↓ убывание":"Направление: ↑ возрастание";
        dialog().setTitle("Сортировка").setItems(names,(dialog,choice)->{
            if(choice==sortNames.length)descending=!descending;else sortBy=choice;
            home();
        }).show();
    }
    private void settings(){
        String[] options={"Тема: "+ThemePrefs.MODES[t.mode],"Акцент: "+ThemePrefs.ACCENTS[t.accentIndex],"Анимации: "+(t.animations?"ВКЛ":"ВЫКЛ"),"Проверить обновления OTA","О приложении"};
        dialog().setTitle("НАСТРОЙКИ").setItems(options,(dialog,index)->{
            if(index==0)dialog().setTitle("Оформление").setSingleChoiceItems(ThemePrefs.MODES,t.mode,(dd,i)->{t.mode=i;t.save(this);t=new ThemePrefs(this);dd.dismiss();home();}).show();
            if(index==1)dialog().setTitle("Цветовой акцент").setSingleChoiceItems(ThemePrefs.ACCENTS,t.accentIndex,(dd,i)->{t.accentIndex=i;t.save(this);t=new ThemePrefs(this);dd.dismiss();home();}).show();
            if(index==2){t.animations=!t.animations;t.save(this);home();}
            if(index==3)new OtaUpdater(this).check(false);
            if(index==4)dialog().setTitle("БПЛА ИНФО 1.0.0").setMessage("Справочник INFEND.\nЛокальные карточки, сортировки и фильтры, импорт/экспорт, темы и встроенное OTA.\n\nНачальные сведения переписаны с изображения и не проверены независимо.").setPositiveButton("ГОТОВО",null).show();
        }).show();
    }
    private void importFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");startActivityForResult(i,IMPORT_JSON);}
    private void exportFile(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"BPLA-INFO-catalog.json");startActivityForResult(i,EXPORT_JSON);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{
            if(request==PICK_IMAGE&&pendingImage!=null){
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                pendingImage.imageUri=uri.toString();save();detail(pendingImage);
            } else if(request==EXPORT_JSON){
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Нет доступа");out.write(DroneStore.exportJson(records).getBytes(StandardCharsets.UTF_8));}
                toast("Каталог экспортирован");
            } else if(request==IMPORT_JSON){
                byte[] content;
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Нет доступа");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>=0){out.write(buf,0,n);if(out.size()>2_000_000)throw new IOException("Файл слишком большой");}content=out.toByteArray();}
                int n=DroneStore.importJson(this,records,new String(content,StandardCharsets.UTF_8));toast("Импортировано / обновлено: "+n);home();
            }
        }catch(Exception e){dialog().setTitle("Ошибка файла").setMessage(e.getMessage()).setPositiveButton("ОК",null).show();}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}catch(Exception ignored){}}
    @Override protected void onResume(){super.onResume();OtaUpdater.resumePending(this);}
    @Override public void onBackPressed(){if(showingDetail){home();return;}super.onBackPressed();}
}
