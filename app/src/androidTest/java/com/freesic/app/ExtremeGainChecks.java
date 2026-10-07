package com.freesic.app;

import android.app.*;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class ExtremeGainChecks {
    private static void check(boolean ok,String name,List<String> passed){if(!ok)throw new AssertionError(name);passed.add(name);}
    static List<String> run(Instrumentation test,Activity activity)throws Exception{
        List<String> passed=new ArrayList<>();Store store=new Store(activity);
        Method request=MainActivity.class.getDeclaredMethod("requestGain",int.class,Consumer.class);request.setAccessible(true);
        Field warning=MainActivity.class.getDeclaredField("gainWarning");warning.setAccessible(true);
        AtomicReference<Boolean> result=new AtomicReference<>();
        test.runOnMainSync(()->{
            store.prefs.edit().remove("extreme_gain_ack_v1").commit();
            if(!PlaybackService.active.setGain(200))throw new AssertionError("Base effect unavailable");
            check(!PlaybackService.active.setGain(300),"extreme target rejected by service without consent",passed);
            check(store.prefs.getInt("gain_percent",0)==200 && PlaybackService.active.loudness.getTargetGain()==3000,"rejected extreme request preserves previous gain",passed);
            try{request.invoke(activity,300,(Consumer<Boolean>)result::set);}catch(Exception e){throw new RuntimeException(e);}
        });
        test.waitForIdleSync();
        AlertDialog dialog=(AlertDialog)warning.get(activity);
        check(dialog!=null&&dialog.isShowing(),"extreme warning appears before gain is changed",passed);
        test.runOnMainSync(()->{
            dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP));
            dialog.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_UP));
            check(PlaybackService.active.loudness.getTargetGain()==3000,"physical volume up cannot bypass pending warning",passed);
        });
        Thread.sleep(350);
        Bitmap screen=test.getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"extreme-warning.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}
        test.runOnMainSync(()->dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());test.waitForIdleSync();
        check(Boolean.FALSE.equals(result.get())&&!store.prefs.getBoolean("extreme_gain_ack_v1",false)&&store.prefs.getInt("gain_percent",0)==200,"cancel keeps prior gain and does not grant consent",passed);
        test.runOnMainSync(()->{try{request.invoke(activity,300,(Consumer<Boolean>)result::set);}catch(Exception e){throw new RuntimeException(e);}});test.waitForIdleSync();
        AlertDialog accepted=(AlertDialog)warning.get(activity);
        test.runOnMainSync(()->accepted.getButton(AlertDialog.BUTTON_POSITIVE).performClick());test.waitForIdleSync();
        check(Boolean.TRUE.equals(result.get())&&store.prefs.getBoolean("extreme_gain_ack_v1",false),"explicit confirmation authorizes extreme target",passed);
        test.runOnMainSync(()->{
            check(PlaybackService.active.loudness.getEnabled()&&PlaybackService.active.loudness.getTargetGain()==10000,"confirmed extreme target returns 10000 millibels from Android",passed);
            PlaybackService.active.setGain(100);
        });
        return passed;
    }
}
