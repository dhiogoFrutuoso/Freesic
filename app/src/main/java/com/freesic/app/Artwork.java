package com.freesic.app;
import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.*;
import java.io.*;
import java.util.concurrent.*;
/** Shared artwork resolver for rows, playlists, player and media-session notifications. */
public final class Artwork {
    public static int revision=0;
    private static final ExecutorService workers=Executors.newFixedThreadPool(2);
    private static final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(20*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private static final LruCache<String,Long> misses=new LruCache<>(256);
    private static final ConcurrentHashMap<String,CompletableFuture<Bitmap>> pending=new ConcurrentHashMap<>();
    public static boolean soRock(String title){String n=MusicLogic.normalize(title);return n.contains("so rock 2")&&(n.contains("major")||n.contains("rock danger")||n.equals("so rock 2"))&&!n.contains("remix")&&!n.contains("ao vivo");}
    public static void invalidate(String uri){cache.remove(uri);misses.remove(uri);revision++;}
    public static CompletableFuture<Bitmap> request(Context c,Track t){Bitmap b=cache.get(t.uri);if(b!=null)return CompletableFuture.completedFuture(b);Long miss=misses.get(t.uri);if(miss!=null&&System.currentTimeMillis()-miss<60000)return CompletableFuture.completedFuture(null);CompletableFuture<Bitmap> f=new CompletableFuture<>(),old=pending.putIfAbsent(t.uri,f);if(old!=null)return old;Context app=c.getApplicationContext();workers.execute(()->{Bitmap found=null;try{found=load(app,t);}catch(Exception ignored){}if(found!=null)cache.put(t.uri,found);else misses.put(t.uri,System.currentTimeMillis());pending.remove(t.uri);f.complete(found);});return f;}
    private static Bitmap decode(byte[] data){if(data==null||data.length>15000000)return null;BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);if(o.outWidth<=0||o.outHeight<=0)return null;o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>800)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,o);}
    private static Bitmap read(InputStream input)throws IOException{if(input==null)return null;try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>15000000)return null;out.write(buf,0,n);}return decode(out.toByteArray());}}
    private static Bitmap uri(Context c,String uri){if(!MusicLogic.localUri(uri))return null;try{return read(c.getContentResolver().openInputStream(Uri.parse(uri)));}catch(Exception e){return null;}}
    private static Bitmap load(Context c,Track t){
        String override=new Store(c).prefs.getString("art_override_"+t.uri,"");Bitmap b=uri(c,override);if(b!=null)return b;
        MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(c,Uri.parse(t.uri));b=decode(r.getEmbeddedPicture());if(b!=null)return b;if(t.video){b=r.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);if(b!=null){int max=Math.max(b.getWidth(),b.getHeight());return max>800?Bitmap.createScaledBitmap(b,b.getWidth()*800/max,b.getHeight()*800/max,true):b;}}}catch(Exception ignored){}finally{try{r.release();}catch(Exception ignored){}}
        if(Build.VERSION.SDK_INT>=29)try{b=c.getContentResolver().loadThumbnail(Uri.parse(t.uri),new Size(640,640),null);if(b!=null)return b;}catch(Exception ignored){}
        b=uri(c,t.art);if(b!=null)return b;
        if(soRock(t.title))try{return read(c.getAssets().open("artwork/so_rock_2.jpg"));}catch(Exception ignored){}
        return null;
    }
}
