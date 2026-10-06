package com.freesic.app;
import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONObject;
public final class Track {
    public final String uri,title,artist,album,folder,art;
    public final long duration,added;
    public final boolean video;
    public Track(String uri,String title,String artist,String album,String folder,String art,long duration,long added,boolean video) {
        this.uri=uri;boolean known=Artwork.soRock(title==null?"":title);this.title=known?"SÓ ROCK 2":clean(title,"Sem título");String credit=clean(artist,"Artista desconhecido");this.artist=known&&credit.equals("Artista desconhecido")?"Rock Danger, Young Ganni, Major RD e Xamã":credit;
        this.album=clean(album,"Sem álbum");this.folder=clean(folder,"Arquivos");this.art=art==null?"":art;
        this.duration=Math.max(0,duration);this.added=added;this.video=video;
    }
    private static String clean(String s,String fallback){return s==null||s.trim().isEmpty()||s.equals("<unknown>")?fallback:s;}
    public MediaItem item() {
        if(!MusicLogic.localUri(uri))throw new IllegalArgumentException("Somente arquivos locais");
        Bundle extras=new Bundle();extras.putBoolean("video",video);extras.putString("folder",folder);extras.putLong("duration",duration);
        MediaMetadata.Builder meta=new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setExtras(extras);
        if(!art.isEmpty()&&MusicLogic.localUri(art))meta.setArtworkUri(Uri.parse(art));
        return new MediaItem.Builder().setMediaId(uri).setUri(uri).setMediaMetadata(meta.build()).build();
    }
    public JSONObject json() {
        JSONObject j=new JSONObject();try{j.put("uri",uri).put("title",title).put("artist",artist).put("album",album).put("folder",folder).put("art",art).put("duration",duration).put("added",added).put("video",video);}catch(Exception ignored){}return j;
    }
    public static Track json(JSONObject j){return new Track(j.optString("uri"),j.optString("title"),j.optString("artist"),j.optString("album"),j.optString("folder"),j.optString("art"),j.optLong("duration"),j.optLong("added"),j.optBoolean("video"));}
    public static Track item(MediaItem i) {
        MediaMetadata m=i.mediaMetadata;Bundle b=m.extras;
        return new Track(i.mediaId,str(m.title),str(m.artist),str(m.albumTitle),b==null?"":b.getString("folder"),m.artworkUri==null?"":m.artworkUri.toString(),b==null?0:b.getLong("duration"),0,b!=null&&b.getBoolean("video"));
    }
    private static String str(CharSequence s){return s==null?"":s.toString();}
}
