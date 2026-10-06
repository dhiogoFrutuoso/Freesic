package com.freesic.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;

@androidx.media3.common.util.UnstableApi
public final class FreesicWidget extends AppWidgetProvider {
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);String action=intent.getAction();if(action==null||!action.startsWith("com.freesic.widget."))return;
        PendingResult pending=goAsync();ListenableFuture<MediaController> future=new MediaController.Builder(c,new SessionToken(c,new ComponentName(c,PlaybackService.class))).buildAsync();
        future.addListener(()->{try{MediaController controller=future.get();
            if(action.endsWith("previous"))controller.seekToPrevious();else if(action.endsWith("next"))controller.seekToNext();else if(controller.isPlaying())controller.pause();else if(controller.getMediaItemCount()>0){if(controller.getPlaybackState()==Player.STATE_IDLE)controller.prepare();controller.play();}
        }catch(Exception ignored){}finally{MediaController.releaseFuture(future);pending.finish();}},command->new android.os.Handler(android.os.Looper.getMainLooper()).post(command));
    }
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){update(c,null);}
    public static void update(Context c,MediaItem item){
        AppWidgetManager m=AppWidgetManager.getInstance(c);int[] ids=m.getAppWidgetIds(new ComponentName(c,FreesicWidget.class));
        for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
            if(item!=null){v.setTextViewText(R.id.widget_title,item.mediaMetadata.title);v.setTextViewText(R.id.widget_subtitle,item.mediaMetadata.artist);}
            v.setTextViewText(R.id.widget_play,PlaybackService.active!=null&&PlaybackService.active.player.isPlaying()?"Ⅱ":"▶");
            int[] buttons={R.id.widget_previous,R.id.widget_play,R.id.widget_next};String[] actions={"previous","play","next"};
            for(int n=0;n<3;n++)v.setOnClickPendingIntent(buttons[n],PendingIntent.getBroadcast(c,n,new Intent(c,FreesicWidget.class).setAction("com.freesic.widget."+actions[n]),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            Intent intent=new Intent(c,MainActivity.class).putExtra("show_player",true);
            v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,5,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);
        }
    }
}
