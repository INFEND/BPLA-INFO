package dev.infend.bplainfo;
import android.content.Context;
public final class ThemePrefs {
 public static final String[] MODES={"AMOLED","Тёмная","Светлая"};
 public static final String[] ACCENTS={
 "Мята","Лазурь","Сапфир","Лаванда","Сирень","Фуксия",
 "Коралл","Персик","Янтарь","Лимон","Лайм","Весна",
 "Изумруд","Бирюза","Океан","Ирис","Аметист","Пион",
 "Рубин","Бронза","Сталь","Лёд","Шалфей","Слива"
 };
 public static final int[] PALETTE={
 0xff55dfcb,0xff43baf5,0xff658aff,0xffac8dff,0xffdb91ff,0xffff77ce,
 0xffff7085,0xffff936d,0xffffba63,0xffffd45a,0xffd0e46d,0xff90df79,
 0xff57cda3,0xff4ad5de,0xff35a6de,0xff8773e4,0xffba6ef6,0xffed77a3,
 0xffe75f5f,0xffd39c78,0xffa3bbc8,0xffe8edf5,0xff76a4ab,0xff9a86ab
 };
 public int mode,accentIndex,bg,panel,fg,muted,stroke,accent,buttonBg,soft,subtle;
 public boolean animations,haptics;
 private static int safeInt(android.content.SharedPreferences p,String key,int fallback){
  try{return p.getInt(key,fallback);}catch(ClassCastException bad){
   android.util.Log.w("BPLA-INFO","Invalid theme integer: "+key);return fallback;
  }
 }
 private static boolean safeBoolean(android.content.SharedPreferences p,String key,boolean fallback){
  try{return p.getBoolean(key,fallback);}catch(ClassCastException bad){
   android.util.Log.w("BPLA-INFO","Invalid theme setting: "+key);return fallback;
  }
 }
 public ThemePrefs(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("bpla_ui",0);
  mode=Math.max(0,Math.min(2,safeInt(p,"mode",0)));
  accentIndex=Math.max(0,Math.min(PALETTE.length-1,safeInt(p,"accent",0)));
  animations=safeBoolean(p,"animations",true);
  haptics=safeBoolean(p,"haptics",true);
  accent=PALETTE[accentIndex];
  if(mode==0){
   bg=0xff000000;panel=0xff0b0b0d;fg=0xfff5f7f9;
   muted=0xff9ba8b1;stroke=0xff27282e;buttonBg=0xff191b20;
   soft=0xff101315;subtle=0xff0d0e10;
  }else if(mode==2){
   bg=0xfff5f7fb;panel=0xffffffff;fg=0xff152536;
   muted=0xff607587;stroke=0xffdce5ed;buttonBg=0xffe8eef5;
   soft=0xffedf2f7;subtle=0xfff9fbfd;
  }else{
   bg=0xff0a111b;panel=0xff121e2a;fg=0xfff1f6fb;
   muted=0xff9caebd;stroke=0xff283947;buttonBg=0xff203140;
   soft=0xff101924;subtle=0xff0d1722;
  }
 }
 public void save(Context c){
  c.getSharedPreferences("bpla_ui",0).edit()
  .putInt("mode",mode).putInt("accent",accentIndex)
  .putBoolean("animations",animations).putBoolean("haptics",haptics).apply();
 }
}
