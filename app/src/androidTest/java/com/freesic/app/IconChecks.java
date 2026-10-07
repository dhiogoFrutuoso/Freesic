package com.freesic.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;

/** Renders the actual packaged resources through Android, without modifying the artwork. */
public final class IconChecks {
    private int checks;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    public void run(Instrumentation instrumentation){Bundle report=new Bundle();try{
        Context c=instrumentation.getTargetContext();
        BitmapDrawable brand=(BitmapDrawable)c.getDrawable(R.drawable.brand_logo);
        check(brand.isFilterBitmap(),"bitmap filtering");
        check(brand.hasMipMap(),"mipmap reduction");
        for(int size:new int[]{48,72,108,216}){
            InsetDrawable icon=(InsetDrawable)c.getDrawable(R.drawable.ic_logo);
            icon.setBounds(0,0,size,size);
            check(Math.abs(icon.getDrawable().getBounds().left-size/6f)<1.1f,"proportional inset at "+size);
            AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)c.getDrawable(R.mipmap.ic_launcher);
            adaptive.setBounds(0,0,size,size);
            Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);adaptive.draw(new Canvas(b));
            check(Color.alpha(b.getPixel(size/2,size/2))==255,"opaque center at "+size);
        }
        Activity activity=instrumentation.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(()->{
            LinearLayout grid=new LinearLayout(activity);grid.setOrientation(1);grid.setPadding(32,48,32,32);grid.setBackgroundColor(Color.rgb(5,8,32));
            TextView title=new TextView(activity);title.setText("Freesic - Icone Android\nAnterior                         Corrigido");title.setTextColor(Color.WHITE);title.setTextSize(22);grid.addView(title);
            float density=c.getResources().getDisplayMetrics().density;
            for(int dp:new int[]{48,72,96}){
                LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER);row.setPadding(0,32,0,32);
                Drawable old=new AdaptiveIconDrawable(new ColorDrawable(Color.rgb(15,23,40)),new InsetDrawable(instrumentation.getContext().getDrawable(instrumentation.getContext().getResources().getIdentifier("brand_logo_legacy","drawable",instrumentation.getContext().getPackageName())),(int)(18*density)));
                for(Drawable drawable:new Drawable[]{old,c.getDrawable(R.mipmap.ic_launcher)}){
                    LinearLayout box=new LinearLayout(activity);box.setOrientation(1);box.setGravity(Gravity.CENTER);
                    ImageView image=new ImageView(activity);image.setImageDrawable(drawable);box.addView(image,new LinearLayout.LayoutParams((int)(dp*density),(int)(dp*density)));
                    TextView label=new TextView(activity);label.setText("Freesic - "+dp+" dp");label.setTextColor(Color.WHITE);label.setTextSize(14);box.addView(label);row.addView(box,new LinearLayout.LayoutParams(0,-2,1));
                }grid.addView(row);
            }activity.setContentView(grid);
        });
        Thread.sleep(1200);
        Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"icon-comparison.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}
        report.putString("stream","PASS "+checks+" icon resource checks; Android screenshot saved");instrumentation.finish(Activity.RESULT_OK,report);
    }catch(Throwable e){report.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));instrumentation.finish(Activity.RESULT_CANCELED,report);}}
}
