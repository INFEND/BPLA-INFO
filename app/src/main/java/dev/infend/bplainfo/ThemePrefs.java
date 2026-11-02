package dev.infend.bplainfo;
import android.content.Context;
public final class ThemePrefs {
 public static final String[] MODES={"AMOLED","Тёмная","Светлая"},ACCENTS={"Неон","Океан","Фиолетовый","Янтарь","Коралл"};
 private static final int[] PALETTE={0xFF60FFAE,0xFF66CFFF,0xFFC19BFF,0xFFFFC86A,0xFFFF808C};
 public int mode,accentIndex,bg,panel,fg,muted,stroke,accent;
 public boolean animations;
 public ThemePrefs(Context c){android.content.SharedPreferences p=c.getSharedPreferences("bpla_ui",0);mode=Math.max(0,Math.min(2,p.getInt("mode",0)));accentIndex=Math.max(0,Math.min(4,p.getInt("accent",0)));animations=p.getBoolean("animations",true);accent=PALETTE[accentIndex];if(mode==2){bg=0xFFF4F7F5;panel=0xFFFFFFFF;fg=0xFF10251B;muted=0xFF68786E;stroke=0xFFDCE5DE;}else{bg=mode==0?0xFF000000:0xFF0A1013;panel=mode==0?0xFF101815:0xFF162024;fg=0xFFF3FFF8;muted=0xFF91A59A;stroke=0xFF283C33;}}
 public void save(Context c){c.getSharedPreferences("bpla_ui",0).edit().putInt("mode",mode).putInt("accent",accentIndex).putBoolean("animations",animations).apply();}
}
