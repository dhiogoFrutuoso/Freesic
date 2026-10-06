package com.freesic.app;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.*;
import java.util.*;

public final class Library {
    public static boolean permission(Context c,boolean video){return c.checkSelfPermission(Build.VERSION.SDK_INT>=33?(video?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_AUDIO):Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED || (video&&Build.VERSION.SDK_INT>=34&&c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED);}
    public static List<Track> scan(Context c,Store store) {
        LinkedHashMap<String,Track> all=new LinkedHashMap<>();
        for(boolean video:new boolean[]{false,true}){
            if(!permission(c,video))continue;
            Uri base=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection=video?new String[]{"_id","title","duration","date_added","_display_name"}:new String[]{"_id","title","artist","album","album_id","duration","date_added","_display_name"};
            List<String> fields=new ArrayList<>(Arrays.asList(projection));fields.add(Build.VERSION.SDK_INT>=29?"relative_path":"_data");
            try(Cursor cur=c.getContentResolver().query(base,fields.toArray(new String[0]),null,null,"title COLLATE NOCASE ASC")){
                if(cur==null)continue;
                while(cur.moveToNext()){
                    long id=num(cur,"_id"),duration=num(cur,"duration"),albumId=num(cur,"album_id");
                    String folder=str(cur,Build.VERSION.SDK_INT>=29?"relative_path":"_data");
                    if(Build.VERSION.SDK_INT<29){int slash=folder.lastIndexOf('/');folder=slash>=0?folder.substring(0,slash+1):folder;}
                    Track t=new Track(ContentUris.withAppendedId(base,id).toString(),(str(cur,"title").trim().isEmpty()?str(cur,"_display_name"):str(cur,"title")),video?"Vídeo local":str(cur,"artist"),video?"Vídeos":str(cur,"album"),folder,!video&&albumId>0?"content://media/external/audio/albumart/"+albumId:"",duration,num(cur,"date_added"),video);
                    all.put(t.uri,t);
                }
            }catch(SecurityException|IllegalArgumentException ignored){}
        }
        for(Track t:store.tracks("imports"))all.put(t.uri,t);
        List<Track> result=new ArrayList<>(all.values());result.sort(Comparator.comparing(t->MusicLogic.normalize(t.title)));return result;
    }
    private static String str(Cursor c,String name){int i=c.getColumnIndex(name);return i<0?"":c.getString(i)==null?"":c.getString(i);}
    private static long num(Cursor c,String name){int i=c.getColumnIndex(name);return i<0?0:c.getLong(i);}
    public static Track importUri(Context c,Uri uri) throws Exception {
        if(!MusicLogic.localUri(uri.toString()))throw new IllegalArgumentException("Escolha um arquivo local");
        String title="Arquivo local",artist="",album="",mime=c.getContentResolver().getType(uri);long duration=0;
        try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst())title=cur.getString(0);}
        MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{r.setDataSource(c,uri);String value=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);if(value!=null&&!value.trim().isEmpty())title=value;artist=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);album=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);try{duration=Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}catch(Exception ignored){}}
        catch(RuntimeException ignored){}finally{r.release();}
        return new Track(uri.toString(),title,artist,album,"Importados","",duration,System.currentTimeMillis()/1000,mime!=null&&mime.startsWith("video/"));
    }
}
