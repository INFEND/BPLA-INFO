package dev.infend.bplainfo;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Consistent theme-aware modal sheets, independent of Android's default grey AlertDialog. */
public final class UiDialogs {
    public interface Selection { void selected(int index); }
    public interface Accept { boolean run(); }
    private UiDialogs(){}

    private static int dp(Activity a,float value){
        return (int)(a.getResources().getDisplayMetrics().density*value+0.5f);
    }
    private static GradientDrawable bg(Activity a,int color,int border,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(a,radius));g.setStroke(dp(a,1),border);
        return g;
    }
    private static TextView label(Activity a,String value,int sp,int color,boolean bold){
        TextView v=new TextView(a);v.setText(value);v.setTextSize(sp);v.setTextColor(color);
        v.setTypeface(android.graphics.Typeface.create(bold?"sans-serif-medium":"sans-serif",0));
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private static LinearLayout column(Activity a){
        LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    public static Dialog shell(Activity a,ThemePrefs t,String title,View body){
        Dialog dialog=new Dialog(a);
        LinearLayout root=column(a);
        root.setPadding(dp(a,18),dp(a,18),dp(a,18),dp(a,14));
        root.setBackground(bg(a,t.panel,t.stroke,22));
        TextView heading=label(a,title,20,t.fg,true);
        heading.setPadding(dp(a,1),0,0,dp(a,12));
        root.addView(heading);
        if(body!=null){
            ScrollView sc=new ScrollView(a);
            sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);
            sc.addView(body);
            root.addView(sc,new LinearLayout.LayoutParams(-1,-2));
        }
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams flags=dialog.getWindow().getAttributes();
        flags.dimAmount=0.72f;flags.gravity=Gravity.CENTER;
        dialog.getWindow().setAttributes(flags);
        dialog.show();
        int width=(int)(a.getResources().getDisplayMetrics().widthPixels*0.90f);
        int maxHeight=(int)(a.getResources().getDisplayMetrics().heightPixels*0.83f);
        dialog.getWindow().setLayout(width,-2);
        if(root.getMeasuredHeight()>maxHeight)dialog.getWindow().setLayout(width,maxHeight);
        if(t.animations){
            root.setAlpha(0f);root.setScaleX(.96f);root.setScaleY(.96f);
            root.animate().alpha(1).scaleX(1).scaleY(1).setDuration(180).start();
        }
        return dialog;
    }
    public static void options(Activity a,ThemePrefs t,String title,String[] labels,int checked,Selection choice){
        LinearLayout list=column(a);
        final Dialog[] parent=new Dialog[1];
        for(int i=0;i<labels.length;i++){
            final int at=i;
            LinearLayout row=new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a,14),dp(a,4),dp(a,10),dp(a,4));
            row.setBackground(bg(a,i==checked?t.soft:t.buttonBg,i==checked?t.accent:t.stroke,14));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(a,54));
            params.bottomMargin=dp(a,6);list.addView(row,params);
            TextView item=label(a,labels[i],15,i==checked?t.accent:t.fg,i==checked);
            row.addView(item,new LinearLayout.LayoutParams(0,-1,1));
            if(i==checked)row.addView(label(a,"✓",18,t.accent,true));
            row.setOnClickListener(v->{if(t.haptics)v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);parent[0].dismiss();choice.selected(at);});
        }
        parent[0]=shell(a,t,title,list);
    }
    public static void message(Activity a,ThemePrefs t,String title,String message){
        confirmation(a,t,title,message,"ЗАКРЫТЬ",null);
    }
    public static void confirmation(Activity a,ThemePrefs t,String title,String message,String accept,Runnable action){
        LinearLayout root=column(a);
        TextView description=label(a,message,14,t.muted,false);
        description.setPadding(dp(a,3),dp(a,3),dp(a,3),dp(a,15));root.addView(description);
        LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.END);root.addView(buttons);
        final Dialog[] parent=new Dialog[1];
        if(action!=null){
            TextView cancel=label(a,"ОТМЕНА",13,t.fg,true);
            cancel.setGravity(Gravity.CENTER);cancel.setBackground(bg(a,t.buttonBg,t.stroke,12));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(a,46),1);cp.rightMargin=dp(a,8);buttons.addView(cancel,cp);
            cancel.setOnClickListener(v->parent[0].dismiss());
        }
        TextView ok=label(a,accept,13,t.mode==2?Color.WHITE:Color.BLACK,true);
        ok.setGravity(Gravity.CENTER);ok.setBackground(bg(a,t.accent,t.accent,12));
        buttons.addView(ok,new LinearLayout.LayoutParams(0,dp(a,46),1));
        ok.setOnClickListener(v->{parent[0].dismiss();if(action!=null)action.run();});
        parent[0]=shell(a,t,title,root);
    }
    public static Dialog editor(Activity a,ThemePrefs t,String title,View form,Accept onSave){
        LinearLayout root=column(a);
        root.addView(form);
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0,dp(a,16),0,0);
        root.addView(actions);
        final Dialog[] parent=new Dialog[1];
        TextView cancel=label(a,"ОТМЕНА",13,t.fg,true);cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(bg(a,t.buttonBg,t.stroke,12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(a,46),1);cp.rightMargin=dp(a,8);
        actions.addView(cancel,cp);cancel.setOnClickListener(v->parent[0].dismiss());
        TextView save=label(a,"СОХРАНИТЬ",13,t.mode==2?Color.WHITE:Color.BLACK,true);
        save.setGravity(Gravity.CENTER);save.setBackground(bg(a,t.accent,t.accent,12));
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(a,46),1));
        save.setOnClickListener(v->{if(onSave.run())parent[0].dismiss();});
        parent[0]=shell(a,t,title,root);
        return parent[0];
    }
}
