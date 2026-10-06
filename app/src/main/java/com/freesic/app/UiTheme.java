package com.freesic.app;
import android.graphics.*;
import android.graphics.drawable.*;
public final class UiTheme {
 public static Drawable background(){return gradient(false);}
 public static Drawable player(){return gradient(true);}
 private static Drawable gradient(boolean player){return new Drawable(){
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  public void draw(Canvas canvas){Rect b=getBounds();paint.setShader(new LinearGradient(0,0,0,Math.max(1,b.height()),player?new int[]{0xff0E2A4C,0xff0E1E33,0xff0E1A2D}:new int[]{0xff121F34,0xff0F1C2D,0xff0F1A29},new float[]{0,.45f,1},Shader.TileMode.CLAMP));canvas.drawRect(b,paint);}
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
 };}
}
