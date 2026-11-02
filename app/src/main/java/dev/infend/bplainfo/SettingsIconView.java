package dev.infend.bplainfo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Unified 24x24 vector icons, stroke width 1.8, optically centered. */
public final class SettingsIconView extends View {
    private final String kind;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;

    public SettingsIconView(Context context,String kind,int color){
        super(context);this.kind=kind;this.color=color;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=Math.min(getWidth(),getHeight());
        if(size<=0)return;
        canvas.save();
        canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
        canvas.scale(size/24f,size/24f);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case "appearance":
                canvas.drawCircle(12,12,8.3f,p);
                line(canvas,12,3.7f,12,20.3f);
                canvas.drawArc(5.7f,5.7f,18.3f,18.3f,95,170,false,p);
                canvas.drawCircle(16.3f,9.3f,1f,p);
                break;
            case "motion":
                line(canvas,12,2.8f,12,21.2f);
                line(canvas,2.8f,12,21.2f,12);
                line(canvas,6.8f,6.8f,17.2f,17.2f);
                line(canvas,17.2f,6.8f,6.8f,17.2f);
                break;
            case "ota":
                canvas.drawArc(3.6f,3.6f,20.4f,20.4f,-72,291,false,p);
                line(canvas,17,2.9f,20.5f,4.7f);
                line(canvas,20.5f,4.7f,20.1f,9f);
                break;
            case "about":
                canvas.drawCircle(12,12,9,p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12,7.6f,1.1f,p);
                canvas.drawRoundRect(11.1f,10.2f,12.9f,17.1f,0.9f,0.9f,p);
                break;
            case "amoled":
                canvas.drawRoundRect(4,4,20,20,4,4,p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12,12,4.25f,p);
                break;
            case "dark":
                p.setStyle(Paint.Style.FILL);
                Path moon=new Path();
                moon.moveTo(15.8f,3.2f);
                moon.cubicTo(8f,3.5f,5.5f,9f,7.7f,14.3f);
                moon.cubicTo(9.8f,19.5f,15.8f,19.2f,19.8f,16.1f);
                moon.cubicTo(17.3f,20.5f,11.2f,22f,7f,18.8f);
                moon.cubicTo(1.2f,14.5f,2.2f,4.2f,15.8f,3.2f);
                canvas.drawPath(moon,p);
                break;
            case "light":
                canvas.drawCircle(12,12,4.4f,p);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4;
                    float c=(float)Math.cos(a),s=(float)Math.sin(a);
                    line(canvas,12+7*c,12+7*s,12+9.5f*c,12+9.5f*s);
                }
                break;
            default:canvas.drawCircle(12,12,5,p);
        }
        canvas.restore();
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
}
