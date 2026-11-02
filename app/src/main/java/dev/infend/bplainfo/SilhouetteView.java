package dev.infend.bplainfo;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class SilhouetteView extends View{
 private final Paint p=new Paint(3);private final ThemePrefs t;private final int mode;
 public SilhouetteView(Context c,ThemePrefs theme,int orientation){super(c);t=theme;mode=orientation;}
 private void line(Canvas c,float a,float b,float x,float y,int col,float width){p.setStyle(Paint.Style.STROKE);p.setColor(col);p.setStrokeWidth(width);c.drawLine(a,b,x,y,p);p.setStyle(Paint.Style.FILL);}
 @Override protected void onDraw(Canvas c){super.onDraw(c);if(getWidth()<10||getHeight()<10)return;c.save();c.translate(getWidth()/2f,getHeight()/2f);float sc=Math.min(getWidth()/230f,getHeight()/112f);c.scale(sc,sc);for(int i=-110;i<=110;i+=22)line(c,i,-60,i,60,t.stroke,.4f);for(int j=-55;j<=55;j+=22)line(c,-120,j,120,j,t.stroke,.4f);int ink=t.mode==2?0xFF224B37:0xFFDAFFE9;p.setColor(ink);Path q=new Path();if(mode==0){q.moveTo(0,-51);q.cubicTo(7,-48,8,-20,7,-9);q.lineTo(109,-2);q.quadTo(117,1,109,6);q.lineTo(9,11);q.lineTo(5,45);q.lineTo(32,54);q.lineTo(32,59);q.lineTo(0,55);q.lineTo(-32,59);q.lineTo(-32,54);q.lineTo(-5,45);q.lineTo(-9,11);q.lineTo(-109,6);q.quadTo(-117,1,-109,-2);q.lineTo(-7,-9);q.cubicTo(-8,-20,-7,-48,0,-51);q.close();c.drawPath(q,p);line(c,-19,-50,19,-50,t.accent,2f);line(c,0,-61,0,-41,t.accent,2f);}else if(mode==1){q.moveTo(-113,-4);q.lineTo(-11,3);q.quadTo(0,11,11,3);q.lineTo(113,-4);q.lineTo(113,2);q.lineTo(11,13);q.quadTo(0,26,-11,13);q.lineTo(-113,2);q.close();c.drawPath(q,p);line(c,-16,-19,16,-19,t.accent,2f);line(c,0,-20,0,23,t.accent,2f);}else{q.moveTo(-105,14);q.lineTo(-91,1);q.lineTo(-25,-8);q.lineTo(8,-10);q.lineTo(68,-4);q.lineTo(95,-32);q.lineTo(103,-32);q.lineTo(94,7);q.lineTo(62,14);q.lineTo(-91,18);q.close();c.drawPath(q,p);line(c,-100,-25,-100,29,t.accent,2f);line(c,-109,3,-90,3,t.accent,2f);}c.restore();}
}
