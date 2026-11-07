package dev.infend.bplainfo;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent themed sorting panel: selecting order, origin or favorite never closes it.
 * Filters are independent of the physical airframe category.
 */
public final class SortSheet {
    public static final class State {
        public int sortBy;
        public boolean descending,favorites,photos;
        public String country="",role="";
    }
    private final Activity a;
    private final ThemePrefs t;
    private final State state;
    private final List<String> countries;
    private final Runnable changed;
    private final LinearLayout body;
    private Dialog dialog;
    private SortSheet(Activity a,ThemePrefs t,State state,List<String> countries,Runnable changed){
        this.a=a;this.t=t;this.state=state;this.countries=countries;this.changed=changed;
        body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);
    }
    public static void open(Activity a,ThemePrefs theme,State current,List<String> countries,Runnable changed){
        SortSheet panel=new SortSheet(a,theme,current,countries,changed);
        panel.draw();
        panel.dialog=UiDialogs.shell(a,theme,"Сортировка и фильтры",panel.body);
    }
    private int dp(float n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable background(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);d.setStroke(dp(1),stroke);d.setCornerRadius(dp(radius));return d;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView v=new TextView(a);v.setText(text);v.setTextColor(color);v.setTextSize(size);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",0));
        return v;
    }
    private void padBottom(View view,int height){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(height));
        lp.bottomMargin=dp(7);
        body.addView(view,lp);
    }
    private void title(String text){
        TextView textView=label(text,11,t.muted,true);
        textView.setPadding(dp(5),dp(9),0,dp(9));
        body.addView(textView);
    }
    private TextView action(String icon,boolean chosen,Runnable onClick){
        TextView v=label(icon,17,chosen?t.accent:t.muted,true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(background(chosen?t.soft:t.buttonBg,chosen?t.accent:t.stroke,11));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(41),dp(38));
        params.leftMargin=dp(6);v.setLayoutParams(params);
        v.setOnClickListener(view->{
            if(t.haptics)view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            onClick.run();
        });
        return v;
    }
    private void draw(){
        body.removeAllViews();
        title("ПОРЯДОК СПИСКА");
        String[] entries={"По названию","По производителю","По скорости","По дальности",
            "По массе","По размаху крыла","По автономности"};
        for(int i=0;i<entries.length;i++){
            final int item=i;
            boolean selected=state.sortBy==i;
            LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11),dp(5),dp(7),dp(5));
            row.setBackground(background(selected?t.soft:t.buttonBg,selected?t.accent:t.stroke,13));
            TextView name=label(entries[i],14,selected?t.accent:t.fg,selected);
            row.addView(name,new LinearLayout.LayoutParams(0,-1,1));
            name.setOnClickListener(v->{
                if(state.sortBy==item)state.descending=!state.descending;
                else {state.sortBy=item;state.descending=false;}
                apply();
            });
            row.addView(action("↑",selected&&!state.descending,()->{state.sortBy=item;state.descending=false;apply();}));
            row.addView(action("↓",selected&&state.descending,()->{state.sortBy=item;state.descending=true;apply();}));
            padBottom(row,50);
        }
        title("ФИЛЬТРЫ");
        toggle("★","Только избранное",state.favorites,()->{state.favorites=!state.favorites;apply();});
        toggle("▣","Только с фотографиями",state.photos,()->{state.photos=!state.photos;apply();});
        selector("🌐","Страна",state.country.isEmpty()?"Все страны":
            CatalogFacets.flag(state.country)+" "+state.country,countries,true);
        selector("◇","Назначение",state.role.isEmpty()?"Все назначения":state.role,
            java.util.Arrays.asList("","Барражирующий","Разведывательный","Ударный"),false);
        TextView clear=label("СБРОСИТЬ ФИЛЬТРЫ",12,t.accent,true);
        clear.setGravity(Gravity.CENTER);
        clear.setBackground(background(t.buttonBg,t.stroke,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(43));
        lp.topMargin=dp(10);body.addView(clear,lp);
        clear.setOnClickListener(v->{
            state.country="";state.role="";state.favorites=false;state.photos=false;apply();
        });
    }
    private void toggle(String glyph,String name,boolean enabled,Runnable onClick){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13),0,dp(12),0);
        row.setBackground(background(enabled?t.soft:t.buttonBg,enabled?t.accent:t.stroke,13));
        TextView marker=label(glyph,18,enabled?t.accent:t.muted,true);
        marker.setGravity(Gravity.CENTER);row.addView(marker,new LinearLayout.LayoutParams(dp(36),-1));
        TextView text=label(name,14,t.fg,true);
        row.addView(text,new LinearLayout.LayoutParams(0,-1,1));
        TextView status=label(enabled?"ВКЛ":"ВЫКЛ",11,enabled?t.accent:t.muted,true);
        row.addView(status);
        padBottom(row,47);
        row.setOnClickListener(v->onClick.run());
    }
    private void selector(String glyph,String heading,String value,List<String> choices,boolean origin){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(13),dp(5),dp(12),dp(5));
        row.setBackground(background(t.buttonBg,t.stroke,13));
        TextView mark=label(glyph,17,t.accent,true);mark.setGravity(Gravity.CENTER);
        row.addView(mark,new LinearLayout.LayoutParams(dp(36),-1));
        LinearLayout labels=new LinearLayout(a);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        labels.addView(label(heading,11,t.muted,false));
        labels.addView(label(value,14,t.fg,true));
        row.addView(label("⌄",19,t.muted,true));
        padBottom(row,57);
        row.setOnClickListener(v->popup(row,choices,origin));
    }
    private void popup(View anchor,List<String> choices,boolean origin){
        LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(5),dp(7),dp(5),dp(7));
        rows.setBackground(background(t.panel,t.stroke,15));
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);scroll.addView(rows);
        PopupWindow popup=new PopupWindow(scroll,dp(280),Math.min(dp(350),dp(50)*choices.size()+dp(15)),true);
        popup.setBackgroundDrawable(background(t.panel,t.accent,15));
        popup.setOutsideTouchable(true);popup.setElevation(dp(12));
        for(String option:choices){
            String name=option.isEmpty()?(origin?"Все страны":"Все назначения"):
                origin?CatalogFacets.flag(option)+" "+option:option;
            TextView item=label(name,14,option.equals(origin?state.country:state.role)?t.accent:t.fg,
                option.equals(origin?state.country:state.role));
            item.setPadding(dp(15),0,dp(12),0);
            rows.addView(item,new LinearLayout.LayoutParams(-1,dp(43)));
            item.setOnClickListener(v->{
                if(origin)state.country=option;else state.role=option;
                popup.dismiss();apply();
            });
        }
        popup.showAsDropDown(anchor,0,dp(2));
    }
    private void apply(){
        changed.run();draw();
    }
}
