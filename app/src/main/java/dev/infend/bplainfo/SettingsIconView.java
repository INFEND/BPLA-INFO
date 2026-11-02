package dev.infend.bplainfo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** One stroke language for every theme and settings icon, centered on a 48x48 grid. */
public final class SettingsIconView extends View {
    private final String kind;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    public SettingsIconView(Context context,String kind,int tint){
        super(context);
        this.kind=kind;
        pen.setColor(tint);pen.setStrokeWidth(2.35f);
        pen.setStrokeJoin(Paint.Join.ROUND);pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStyle(Paint.Style.STROKE);
    }
    private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,pen);}
    private void circle(Canvas c,float x,float y,float r){c.drawCircle(x,y,r,pen);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=Math.min(getWidth()-getPaddingLeft()-getPaddingRight(),
            getHeight()-getPaddingTop()-getPaddingBottom());
        if(size<=0)return;
        canvas.save();
        canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
        canvas.scale(size/48f,size/48f);
        switch(kind){
            case "amoled":
                circle(canvas,24,24,12);circle(canvas,24,24,7);
                pen.setStyle(Paint.Style.FILL);circle(canvas,24,24,2);
                break;
            case "moon":{
                Path moon=new Path();moon.moveTo(28,9);
                moon.cubicTo(17,12,13,24,18,32);
                moon.cubicTo(21,37,27,39,34,35);
                moon.cubicTo(25,43,10,35,11,23);
                moon.cubicTo(12,14,19,9,28,9);
                canvas.drawPath(moon,pen);break;
            }
            case "sun":
                circle(canvas,24,24,7.5f);
                for(int i=0;i<8;i++){
                    double a=i*Math.PI/4d;
                    line(canvas,24+(float)Math.cos(a)*12,24+(float)Math.sin(a)*12,
                        24+(float)Math.cos(a)*16,24+(float)Math.sin(a)*16);
                }
                break;
            case "palette":{
                Path palette=new Path();palette.moveTo(23,9);
                palette.cubicTo(9,9,6,20,10,30);
                palette.cubicTo(13,38,23,41,30,35);
                palette.cubicTo(34,31,25,29,27,25);
                palette.cubicTo(29,22,39,28,39,23);
                palette.cubicTo(39,14,32,9,23,9);
                canvas.drawPath(palette,pen);
                pen.setStyle(Paint.Style.FILL);
                circle(canvas,16,19,2);circle(canvas,24,15,2);
                circle(canvas,32,18,2);circle(canvas,17,28,2);
                break;
            }
            case "motion":{
                Path star=new Path();star.moveTo(24,9);star.lineTo(28,20);
                star.lineTo(39,24);star.lineTo(28,28);star.lineTo(24,39);
                star.lineTo(20,28);star.lineTo(9,24);star.lineTo(20,20);star.close();
                canvas.drawPath(star,pen);break;
            }
            case "refresh":
                canvas.drawArc(11,11,37,37,-53,292,false,pen);
                line(canvas,31,11,37,12);line(canvas,37,12,37,18);
                break;
            case "info":
                circle(canvas,24,24,14);
                line(canvas,24,22,24,32);
                pen.setStyle(Paint.Style.FILL);circle(canvas,24,16.5f,1.7f);
                break;
            default:circle(canvas,24,24,13);
        }
        pen.setStyle(Paint.Style.STROKE);
        canvas.restore();
    }
}