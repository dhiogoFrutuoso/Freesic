package com.freesic.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.TextView;
import androidx.media3.common.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Device integration checks. Fixtures are generated, never bundled in the release APK. */
@androidx.media3.common.util.UnstableApi
public final class FreesicInstrumentation extends Instrumentation {
    private boolean artworkOnly;
    private boolean iconOnly;
    private final List<String> passed=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);iconOnly=args!=null&&args.getString("icons", "false").equals("true");artworkOnly=args!=null&&args.getString("artwork", "false").equals("true");start();}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);passed.add(message);}
    private void waitFor(java.util.function.BooleanSupplier condition,long timeout)throws Exception {long until=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<until){if(condition.getAsBoolean())return;Thread.sleep(150);}throw new AssertionError("Timeout waiting for state");}
    private boolean mainCondition(java.util.function.BooleanSupplier condition){AtomicBoolean result=new AtomicBoolean();runOnMainSync(()->result.set(condition.getAsBoolean()));return result.get();}
    private View findText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findText(g.getChildAt(i),text);if(found!=null)return found;}}return null;}

    private void artworkChecks(){Bundle report=new Bundle();try{
        Context c=getTargetContext();Store store=new Store(c);List<Track> tracks=Library.scan(c,store);
        Track embedded=tracks.stream().filter(t->t.uri.contains("content:")&&t.title.equals("Horizonte")&&t.folder.contains("FreesicReference")).findFirst().orElseThrow(()->new AssertionError("Embedded-cover fixture missing"));
        android.graphics.Bitmap cover=Artwork.request(c,embedded).get(10,java.util.concurrent.TimeUnit.SECONDS);check(cover!=null&&cover.getWidth()>100,"embedded FLAC artwork decoded");
        Track real=tracks.stream().filter(t->Artwork.soRock(t.title)&&t.folder.contains("FreesicReal")).findFirst().orElseThrow(()->new AssertionError("Real MP4 audio missing"));
        android.media.MediaMetadataRetriever retriever=new android.media.MediaMetadataRetriever();retriever.setDataSource(c,Uri.parse(real.uri));check(retriever.getEmbeddedPicture()==null,"actual downloaded audio has no embedded picture");retriever.release();
        Artwork.invalidate(real.uri);check(Artwork.request(c,real).get(10,java.util.concurrent.TimeUnit.SECONDS)!=null,"actual downloaded song resolves its published offline cover");
        check(!Artwork.soRock("SO ROCK 2 remix Major RD"),"known-cover matcher excludes remixes");
        Track absent=new Track("content://com.freesic.missing/no-art","Unidentified recording","Unknown","","","",0,0,false);
        check(Artwork.request(c,absent).get(10,java.util.concurrent.TimeUnit.SECONDS)==null,"unknown missing artwork stays absent instead of inventing an album cover");
        Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));passed.addAll(GestureChecks.run(this,activity));passed.addAll(ChromeChecks.run(this,activity));passed.addAll(VolumeKeyChecks.run(this,activity));Thread.sleep(3800);
        waitFor(()->PlaybackService.active!=null,10000);
        runOnMainSync(()->{PlaybackService.active.player.setMediaItem(real.item());PlaybackService.active.player.prepare();PlaybackService.active.player.play();});
        waitFor(()->mainCondition(()->PlaybackService.active.player.isPlaying()),15000);
        waitFor(()->mainCondition(()->PlaybackService.active.player.getCurrentMediaItem().mediaMetadata.artworkData!=null),10000);
        check(mainCondition(()->PlaybackService.active.player.getCurrentMediaItem().mediaMetadata.artworkData.length>1000),"current media item retains resolved cover bytes");
        android.app.NotificationManager notifications=(android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        waitFor(()->java.util.Arrays.stream(notifications.getActiveNotifications()).anyMatch(n->n.getNotification().getLargeIcon()!=null),10000);
        check(java.util.Arrays.stream(notifications.getActiveNotifications()).anyMatch(n->n.getNotification().getLargeIcon()!=null),"actual posted media notification contains the song artwork");
        runOnMainSync(()->{PlaybackService.active.player.pause();PlaybackService.active.player.seekTo(10000);});Thread.sleep(300);
        java.io.File custom=new java.io.File(c.getFilesDir(),"qa-artwork.png");android.graphics.Bitmap red=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);red.eraseColor(android.graphics.Color.RED);try(java.io.FileOutputStream out=new java.io.FileOutputStream(custom)){red.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        store.prefs.edit().putString("art_override_"+real.uri,Uri.fromFile(custom).toString()).commit();Artwork.invalidate(real.uri);
        check(Artwork.request(c,real).get(10,java.util.concurrent.TimeUnit.SECONDS).getPixel(30,30)==android.graphics.Color.RED,"manual cover overrides cached artwork");
        runOnMainSync(()->PlaybackService.active.forceArtworkRefresh());Thread.sleep(1000);
        check(mainCondition(()->Math.abs(PlaybackService.active.player.getCurrentPosition()-10000)<200),"artwork metadata refresh preserves playback position");
        store.prefs.edit().remove("art_override_"+real.uri).commit();Artwork.invalidate(real.uri);custom.delete();runOnMainSync(()->PlaybackService.active.forceArtworkRefresh());
        report.putString("stream","PASS "+passed.size()+" artwork checks\n"+String.join("\n",passed));finish(Activity.RESULT_OK,report);
    }catch(Throwable e){report.putString("stream","FAIL after "+passed.size()+" artwork checks: "+e);finish(Activity.RESULT_CANCELED,report);}}

    private Uri fixture(String title,double hz)throws Exception {
        Context c=getTargetContext();ContentValues values=new ContentValues();values.put(MediaStore.Audio.Media.DISPLAY_NAME,title+".wav");values.put(MediaStore.Audio.Media.TITLE,title);values.put(MediaStore.Audio.Media.ARTIST,"Freesic · teste local");values.put(MediaStore.Audio.Media.ALBUM,"Sons de teste");values.put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav");values.put(MediaStore.Audio.Media.RELATIVE_PATH,"Music/Freesic QA/");values.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri uri=c.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException();
        int samples=44100*20;ByteBuffer b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes()).putInt(36+samples*2).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)1).putShort((short)1).putInt(44100).putInt(88200).putShort((short)2).putShort((short)16).put("data".getBytes()).putInt(samples*2);
        for(int i=0;i<samples;i++)b.putShort((short)(Math.sin(i*2*Math.PI*hz/44100)*1500));try(OutputStream out=c.getContentResolver().openOutputStream(uri)){out.write(b.array());}
        values.clear();values.put(MediaStore.Audio.Media.IS_PENDING,0);c.getContentResolver().update(uri,values,null,null);return uri;
    }
    @Override public void onStart(){if(iconOnly){new IconChecks().run(this);return;}if(artworkOnly){artworkChecks();return;}Bundle report=new Bundle();try{
        Context c=getTargetContext();passed.addAll(ArtworkPipelineChecks.run(c));new Store(c).prefs.edit().clear().commit();c.getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,"relative_path = ?",new String[]{"Music/Freesic QA/"});Uri one=fixture("Aurora Teste",261.63),two=fixture("Maré calma",329.63);
        Store store=new Store(c);Track a=new Track(one.toString(),"Aurora Teste","Freesic · teste local","Sons de teste","Music/Freesic QA/","",20000,0,false),b=new Track(two.toString(),"Maré calma","Freesic · teste local","Sons de teste","Music/Freesic QA/","",20000,0,false);
        store.createPlaylist("Minha seleção");store.addToPlaylist("Minha seleção",a);store.addToPlaylist("Minha seleção",b);store.addToPlaylist("Minha seleção",a);
        check(store.playlist("Minha seleção").size()==2,"playlist deduplicates items");store.favorite(a.uri);String backup=store.backup();store.restore(backup);check(store.playlist("Minha seleção").size()==2,"backup merge preserves order without duplication");
        store.lyric(a.uri,"[00:00.00]Sua música. Seu espaço.\n[00:04.00]Livre para ouvir.\n[00:08.00]Tudo no seu aparelho.");
        check(Library.scan(c,store).stream().anyMatch(t->t.uri.equals(a.uri)),"library discovers indexed local audio without file import");
        Thread.sleep(500);List<Track> indexed=Library.scan(c,store);int expectedQueue=(int)indexed.stream().filter(t->!t.video).count();String fixtureTitle=indexed.stream().filter(t->t.uri.equals(a.uri)).findFirst().get().title;
        store.prefs.edit().putInt("gain_profile_version",4).putInt("gain_percent",300).commit();
        Activity activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitFor(()->PlaybackService.active!=null,10000);
        check(store.prefs.getInt("gain_profile_version",0)==5 && store.prefs.getInt("gain_percent",0)==100,"upgrading previous 300 percent requires a new gain selection");
        passed.addAll(GestureChecks.run(this,activity));passed.addAll(ChromeChecks.run(this,activity));passed.addAll(VolumeKeyChecks.run(this,activity));Thread.sleep(3800);
        waitFor(()->PlaybackService.active!=null,10000);
        waitFor(()->mainCondition(()->findText(activity.getWindow().getDecorView(),fixtureTitle)!=null),10000);
        Thread.sleep(800);int[] location=new int[2];runOnMainSync(()->{View title=findText(activity.getWindow().getDecorView(),fixtureTitle);title.getLocationOnScreen(location);location[0]+=title.getWidth()/2;location[1]+=title.getHeight()/2;});
        long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,location[0],location[1],0),up=MotionEvent.obtain(time,time+80,MotionEvent.ACTION_UP,location[0],location[1],0);sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();
        waitFor(()->mainCondition(()->PlaybackService.active.player.isPlaying()),15000);
        check(mainCondition(()->PlaybackService.active.player.getMediaItemCount()==expectedQueue&&PlaybackService.active.player.getCurrentMediaItem().mediaId.equals(a.uri)),"touching a library row starts the selected track and queue");
        Thread.sleep(1200);check(mainCondition(()->PlaybackService.active.player.getCurrentPosition()>500),"local WAV decodes and playback clock advances");
        runOnMainSync(()->PlaybackService.active.player.pause());check(mainCondition(()->!PlaybackService.active.player.getPlayWhenReady()),"pause command changes state");
        runOnMainSync(()->{PlaybackService.active.player.seekTo(5000);PlaybackService.active.player.setPlaybackSpeed(1.25f);PlaybackService.active.player.setShuffleModeEnabled(true);PlaybackService.active.player.setRepeatMode(Player.REPEAT_MODE_ALL);});
        check(mainCondition(()->PlaybackService.active.player.getCurrentPosition()>=4900),"seek reaches requested position");
        check(mainCondition(()->PlaybackService.active.player.getPlaybackParameters().speed==1.25f),"speed control applied");
        check(mainCondition(()->PlaybackService.active.player.getShuffleModeEnabled()&&PlaybackService.active.player.getRepeatMode()==Player.REPEAT_MODE_ALL),"shuffle and repeat commands applied");
        runOnMainSync(()->{PlaybackService.active.player.seekToDefaultPosition(1);PlaybackService.active.player.play();});
        waitFor(()->mainCondition(()->PlaybackService.active.player.isPlaying()),10000);check(mainCondition(()->PlaybackService.active.player.getCurrentMediaItemIndex()==1),"queue changes selected item");
        runOnMainSync(()->{PlaybackService.active.setTimer(5);});check(mainCondition(()->PlaybackService.active.timerRemaining()>290000),"sleep timer configured");runOnMainSync(()->PlaybackService.active.setTimer(0));
        runOnMainSync(()->{store.prefs.edit().putBoolean("eq_enabled",true).apply();PlaybackService.active.applyEffects();});
        passed.addAll(ExtremeGainChecks.run(this,activity));
        for(int[] preset:new int[][]{{150,1000},{200,3000},{300,8800}}){
            runOnMainSync(()->{if(!PlaybackService.active.setGain(preset[0]))throw new AssertionError("LoudnessEnhancer unavailable on test device");});
            check(mainCondition(()->PlaybackService.active.loudness.getEnabled()&&Math.abs(PlaybackService.active.loudness.getTargetGain()-preset[1])<1),preset[0]+" percent preset applies "+preset[1]+" millibels to active audio session");
        }
        runOnMainSync(()->PlaybackService.active.setGain(100));check(mainCondition(()->PlaybackService.active.loudness==null),"normal gain disables amplification");
        check(c.checkSelfPermission("android.permission.INTERNET")!=0,"APK has no INTERNET permission");
        runOnMainSync(()->{PlaybackService.active.player.setShuffleModeEnabled(false);PlaybackService.active.player.setPlaybackSpeed(1f);PlaybackService.active.player.seekTo(0,0);PlaybackService.active.player.play();activity.moveTaskToBack(true);});
        Thread.sleep(1200);check(mainCondition(()->PlaybackService.active.player.isPlaying()),"playback continues with activity in background");
        Thread.sleep(5200);check(!store.tracks("queue").isEmpty(),"queue snapshot persisted");
        runOnMainSync(()->{c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));PlaybackService.active.player.pause();});
        report.putString("stream","\nPASS "+passed.size()+" integration checks\n"+String.join("\n",passed)+"\n");finish(Activity.RESULT_OK,report);
    }catch(Throwable e){report.putString("stream","\nFAIL after "+passed.size()+" checks\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,report);}}
}
