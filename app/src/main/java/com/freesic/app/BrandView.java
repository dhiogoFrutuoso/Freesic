package com.freesic.app;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
import java.util.concurrent.*;
public final class BrandView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;private String key="";
    private int generation=-1;
    public BrandView(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void track(Track t){String next=t==null?"":t.uri;if(next.equals(key)&&generation==Artwork.revision)return;key=next;generation=Artwork.revision;bitmap=null;invalidate();if(t==null)return;Artwork.request(getContext(),t).thenAccept(b->post(()->{if(key.equals(t.uri)){bitmap=b;invalidate();}}));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),s=Math.min(w,h);Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),s*.09f,s*.09f,Path.Direction.CW);c.save();c.clipPath(clip);
        if(bitmap!=null){float scale=Math.max(w/bitmap.getWidth(),h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;c.drawBitmap(bitmap,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),paint);c.restore();return;}
        boolean brand=key.isEmpty();paint.setShader(new LinearGradient(0,0,w,h,new int[]{brand?0xff91CBFF:0xff244D79,brand?0xff65ADFF:0xff152A48,brand?0xff367AE5:0xff101B31},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);paint.setShader(null);
        Drawable icon=getContext().getDrawable(brand?R.drawable.ic_logo:R.drawable.ic_music).mutate();icon.setTint(brand?0xff0C315B:0xff78B8FF);int side=(int)(s*.48f);icon.setBounds((int)(w-side)/2,(int)(h-side)/2,(int)(w+side)/2,(int)(h+side)/2);icon.draw(c);c.restore();
    }
}
