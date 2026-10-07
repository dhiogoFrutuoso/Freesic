package com.freesic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.media.AudioManager;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

final class VolumeKeyChecks {
    static List<String> run(Instrumentation instrumentation,Activity activity){
        List<String> passed=new ArrayList<>();
        instrumentation.runOnMainSync(()->{
            AudioManager audio=(AudioManager)activity.getSystemService(Activity.AUDIO_SERVICE);
            Store store=new Store(activity);PlaybackService service=PlaybackService.active;
            int original=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            try{
                PlaybackService.active=null;store.prefs.edit().putInt("gain_percent",300).commit();
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,2,0);
                if(!activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)))throw new AssertionError("Volume down must be consumed");
                if(!activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_DOWN)))throw new AssertionError("Volume up-event must be consumed");
                passed.add("physical volume events handled by Activity without system UI");
                if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)!=1)throw new AssertionError("Missing service must still lower actual volume");
                passed.add("volume down lowers stream when saved boost has no service");
                if(store.prefs.getInt("gain_percent",0)!=100)throw new AssertionError("Unavailable boost must reset stale preset");
                passed.add("stale boost resets to normal on unavailable service");
            }finally{PlaybackService.active=service;store.prefs.edit().putInt("gain_percent",100).commit();audio.setStreamVolume(AudioManager.STREAM_MUSIC,original,0);}
        });
        return passed;
    }
}
