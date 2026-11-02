package dev.infend.bplainfo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

public final class RadarView extends View {
    private final ThemePrefs theme;
    private final Paint p=new Paint(3);
    public RadarView(Context c,ThemePrefs t){super(c);theme=t;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float cx=getWidth()*.5f,cy=getHeight()*.52f,r=Math.min(getWidth()*.46f,getHeight()*.46f);
        if(r<10)return;
        int faded=theme.mode==2?0xFFBBCFC4:0xFF284638;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(faded);
        for(int i=1;i<=4;i++)c.drawCircle(cx,cy,r*i/4,p);
        c.drawLine(cx-r,cy,cx+r,cy,p);c.drawLine(cx,cy-r,cx,cy+r,p);
        float angle=theme.animations?(SystemClock.uptimeMillis()%5200L)*360f/5200f:42f;
        c.save();c.rotate(angle,cx,cy);
        p.setColor(theme.accent);p.setStrokeWidth(2.4f);
        c.drawLine(cx,cy,cx+r,cy,p);
        p.setStyle(Paint.Style.FILL);p.setColor((theme.accent&0x00FFFFFF)|0x14000000);
        RectF rect=new RectF(cx-r,cy-r,cx+r,cy+r);
        c.drawArc(rect,-25,25,true,p);
        c.restore();
        p.setStyle(Paint.Style.FILL);p.setColor(theme.accent);
        c.drawCircle(cx-r*.45f,cy-r*.32f,3.5f,p);
        c.drawCircle(cx+r*.68f,cy+r*.12f,2.5f,p);
        c.drawCircle(cx+r*.16f,cy-r*.72f,2.5f,p);
        if(theme.animations&&getWindowToken()!=null)postInvalidateDelayed(55);
    }
}
