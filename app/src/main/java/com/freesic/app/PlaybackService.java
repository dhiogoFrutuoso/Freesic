package com.freesic.app;

import android.app.PendingIntent;
import android.content.*;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.media3.common.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.*;
import com.google.common.util.concurrent.*;
import java.io.IOException;
import java.util.*;

@UnstableApi
public final class PlaybackService extends MediaSessionService {
    public static volatile PlaybackService active;
    public ExoPlayer player;
    public Equalizer equalizer;
    public android.media.audiofx.LoudnessEnhancer loudness;
    private MediaSession session;private String artworkPending="";
    private Store store;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long sleepAt=0;
    private boolean restoring=true;
    private final Runnable pulse=new Runnable(){public void run(){
        if(player==null)return;
        if(sleepAt>0&&System.currentTimeMillis()>=sleepAt){sleepAt=0;player.pause();}
        save();handler.postDelayed(this,5000);
    }};
    @Override public void onCreate(){
        super.onCreate();active=this;store=new Store(this);
        // The old continuous amplitude scale differs from these louder presets.
        // Never turn an old saved 300% into the new target without a new choice.
        if(store.prefs.getInt("gain_profile_version",0)<2)
            store.prefs.edit().putInt("gain_percent",100).putInt("gain_profile_version",2).apply();
        DataSource.Factory localFactory=()->new LocalSource(this);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(localFactory))
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
                .setHandleAudioBecomingNoisy(true).setWakeMode(C.WAKE_MODE_LOCAL).build();
        player.addListener(new Player.Listener(){
            @Override public void onAudioSessionIdChanged(int id){rebuildEffects(id);}
            @Override public void onMediaItemTransition(@Nullable MediaItem item,int reason){
                if(!restoring&&item!=null)store.played(Track.item(item));save();FreesicWidget.update(PlaybackService.this,item);refreshArtwork();
            }
            @Override public void onIsPlayingChanged(boolean playing){save();FreesicWidget.update(PlaybackService.this,player.getCurrentMediaItem());}
            @Override public void onPlayerError(PlaybackException e){store.prefs.edit().putString("last_error","Não foi possível tocar este arquivo. Verifique o acesso ou o formato.").apply();}
        });
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).putExtra("show_player",true),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        androidx.media3.datasource.DataSourceBitmapLoader imageDecoder=new androidx.media3.datasource.DataSourceBitmapLoader(this);
        androidx.media3.common.util.BitmapLoader sharedArtwork=new androidx.media3.common.util.BitmapLoader(){
            public boolean supportsMimeType(String mime){return imageDecoder.supportsMimeType(mime);}
            public ListenableFuture<android.graphics.Bitmap> decodeBitmap(byte[] data){return imageDecoder.decodeBitmap(data);}
            public ListenableFuture<android.graphics.Bitmap> loadBitmap(Uri uri){return MusicLogic.localUri(uri.toString())?imageDecoder.loadBitmap(uri):Futures.immediateFailedFuture(new IOException("Somente capas locais"));}
            @Override public ListenableFuture<android.graphics.Bitmap> loadBitmapFromMetadata(MediaMetadata metadata){
                MediaItem item=player==null?null:player.getCurrentMediaItem();if(item==null)return null;
                SettableFuture<android.graphics.Bitmap> result=SettableFuture.create();Artwork.request(PlaybackService.this,Track.item(item)).whenComplete((bitmap,error)->{if(error!=null)result.setException(error);else result.set(bitmap);});return result;
            }
        };
        session=new MediaSession.Builder(this,player).setBitmapLoader(sharedArtwork).setSessionActivity(open).setCallback(new MediaSession.Callback(){
            @Override public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession session,MediaSession.ControllerInfo controller,List<MediaItem> items){
                List<MediaItem> safe=new ArrayList<>();for(MediaItem i:items){
                    Uri uri=i.localConfiguration==null?Uri.parse(i.mediaId):i.localConfiguration.uri;
                    if(!MusicLogic.localUri(uri.toString()))return Futures.immediateFailedFuture(new IllegalArgumentException("O Freesic toca somente arquivos locais"));
                    safe.add(i.buildUpon().setUri(uri).build());
                }return Futures.immediateFuture(safe);
            }
        }).build();
        DefaultMediaNotificationProvider notifications=new DefaultMediaNotificationProvider(this){
            @Override protected int[] addNotificationActions(MediaSession s,com.google.common.collect.ImmutableList<CommandButton> buttons,androidx.core.app.NotificationCompat.Builder builder,MediaNotification.ActionFactory factory){builder.setColor(0xff102C49).setColorized(true);return super.addNotificationActions(s,buttons,builder,factory);}
        };notifications.setSmallIcon(R.drawable.ic_notification);setMediaNotificationProvider(notifications);
        List<Track> queue=store.tracks("queue");
        if(!queue.isEmpty()){
            List<MediaItem> items=new ArrayList<>();for(Track t:queue)items.add(t.item());
            int index=Math.max(0,Math.min(store.prefs.getInt("queue_index",0),items.size()-1));
            player.setMediaItems(items,index,Math.max(0,store.prefs.getLong("queue_position",0)));
        }
        player.setRepeatMode(store.prefs.getInt("repeat",Player.REPEAT_MODE_OFF));
        player.setShuffleModeEnabled(store.prefs.getBoolean("shuffle",false));
        player.setPlaybackSpeed(Math.max(.5f,Math.min(2f,store.prefs.getFloat("speed",1f))));
        restoring=false;handler.postDelayed(pulse,5000);
    }
    public void forceArtworkRefresh(){artworkPending="";refreshArtwork();}
    public void refreshArtwork(){
        if(player==null||player.getCurrentMediaItem()==null)return;MediaItem original=player.getCurrentMediaItem();String id=original.mediaId;if(artworkPending.equals(id)&&original.mediaMetadata.artworkData!=null)return;artworkPending=id;
        Artwork.request(this,Track.item(original)).thenAccept(bitmap->{if(bitmap==null)return;java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,bitmap.getWidth()*480/Math.max(bitmap.getWidth(),bitmap.getHeight())),Math.max(1,bitmap.getHeight()*480/Math.max(bitmap.getWidth(),bitmap.getHeight())),true);scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);byte[] data=bytes.toByteArray();handler.post(()->{if(player==null||player.getCurrentMediaItem()==null||!player.getCurrentMediaItem().mediaId.equals(id))return;MediaItem current=player.getCurrentMediaItem();if(java.util.Arrays.equals(data,current.mediaMetadata.artworkData))return;player.replaceMediaItem(player.getCurrentMediaItemIndex(),current.buildUpon().setMediaMetadata(current.mediaMetadata.buildUpon().setArtworkUri(null).setArtworkData(data,MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()).build());if(session!=null)onUpdateNotification(session);});});
    }
    public void setTimer(int minutes){sleepAt=minutes<=0?0:System.currentTimeMillis()+minutes*60000L;}
    public long timerRemaining(){return Math.max(0,sleepAt-System.currentTimeMillis());}
    private void rebuildEffects(int sessionId){
        if(equalizer!=null){equalizer.release();equalizer=null;}
        if(loudness!=null){loudness.release();loudness=null;}
        if(sessionId==C.AUDIO_SESSION_ID_UNSET||sessionId==0)return;
        try{equalizer=new Equalizer(0,sessionId);applyEffects();}catch(RuntimeException ignored){equalizer=null;}
        setGain(store.prefs.getInt("gain_percent",100));
    }
    public boolean setGain(int value){
        int percent=MusicLogic.clampGain(value);
        try {
            if(percent==100) {
                if(loudness!=null) {
                    if(loudness.setEnabled(false)!=android.media.audiofx.AudioEffect.SUCCESS)throw new IllegalStateException("Effect disable failed");
                    loudness.release();loudness=null;
                }
            } else {
                if(loudness==null) {
                    int id=player==null?C.AUDIO_SESSION_ID_UNSET:player.getAudioSessionId();
                    if(id==C.AUDIO_SESSION_ID_UNSET||id==0){store.prefs.edit().putInt("gain_percent",100).apply();return false;}
                    loudness=new android.media.audiofx.LoudnessEnhancer(id);
                }
                if(!loudness.hasControl())throw new IllegalStateException("Effect control unavailable");
                int target=MusicLogic.gainMillibels(percent);
                loudness.setTargetGain(target);
                if(Math.abs(loudness.getTargetGain()-target)>1f
                        ||loudness.setEnabled(true)!=android.media.audiofx.AudioEffect.SUCCESS
                        ||!loudness.getEnabled())throw new IllegalStateException("Effect target unavailable");
            }
            store.prefs.edit().putInt("gain_percent",percent).apply();return true;
        } catch(RuntimeException e) {
            // A device without this effect must report failure instead of fake success.
            if(loudness!=null){try{loudness.release();}catch(RuntimeException ignored){}loudness=null;}
            store.prefs.edit().putInt("gain_percent",100).apply();
            return false;
        }
    }
    public void applyEffects(){
        if(equalizer==null)return;
        try{
            short[] range=equalizer.getBandLevelRange();
            for(short b=0;b<equalizer.getNumberOfBands();b++)equalizer.setBandLevel(b,(short)Math.max(range[0],Math.min(range[1],store.prefs.getInt("eq_band_"+b,0))));
            equalizer.setEnabled(store.prefs.getBoolean("eq_enabled",false));
        }catch(RuntimeException ignored){}
    }
    private void save(){
        if(restoring||player==null)return;
        List<Track> q=new ArrayList<>();for(int i=0;i<player.getMediaItemCount();i++)q.add(Track.item(player.getMediaItemAt(i)));
        store.saveTracks("queue",q);
        store.prefs.edit().putInt("queue_index",Math.max(0,player.getCurrentMediaItemIndex())).putLong("queue_position",Math.max(0,player.getCurrentPosition()))
                .putInt("repeat",player.getRepeatMode()).putBoolean("shuffle",player.getShuffleModeEnabled()).putFloat("speed",player.getPlaybackParameters().speed).apply();
    }
    @Override @Nullable public MediaSession onGetSession(MediaSession.ControllerInfo controller){
        return controller.isTrusted()||getPackageName().equals(controller.getPackageName())?session:null;
    }
    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);save();if(equalizer!=null)equalizer.release();if(loudness!=null)loudness.release();if(session!=null)session.release();if(player!=null){player.release();player=null;}active=null;super.onDestroy();
    }
    private static final class LocalSource implements DataSource {
        private final DefaultDataSource delegate;
        LocalSource(Context c){delegate=new DefaultDataSource(c,new DataSource(){
            public long open(DataSpec s)throws IOException{throw new IOException("Origem remota desativada");}
            public int read(byte[] b,int o,int l)throws IOException{throw new IOException("Origem remota desativada");}
            @Nullable public Uri getUri(){return null;}
            public void close(){}
            public void addTransferListener(TransferListener t){}
        });}
        public long open(DataSpec spec)throws IOException{if(!MusicLogic.localUri(spec.uri.toString()))throw new IOException("Somente arquivos locais");return delegate.open(spec);}
        public int read(byte[] b,int o,int l)throws IOException{return delegate.read(b,o,l);}
        @Nullable public Uri getUri(){return delegate.getUri();}
        public void close()throws IOException{delegate.close();}
        public void addTransferListener(TransferListener t){delegate.addTransferListener(t);}
        public Map<String,List<String>> getResponseHeaders(){return delegate.getResponseHeaders();}
    }
}
