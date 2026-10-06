package com.freesic.app;

import android.content.*;
import org.json.*;
import java.util.*;

public final class Store {
    public final SharedPreferences prefs;
    public Store(Context c){prefs=c.getSharedPreferences("freesic",Context.MODE_PRIVATE);}
    public Set<String> favorites(){return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));}
    public boolean favorite(String uri){Set<String>s=favorites();boolean yes=!s.remove(uri);if(yes)s.add(uri);prefs.edit().putStringSet("favorites",s).apply();return yes;}
    public JSONObject playlists(){try{return new JSONObject(prefs.getString("playlists","{}"));}catch(Exception e){return new JSONObject();}}
    public List<String> playlistNames(){List<String> n=new ArrayList<>();playlists().keys().forEachRemaining(n::add);n.sort(String.CASE_INSENSITIVE_ORDER);return n;}
    public void createPlaylist(String name){name=name.trim();if(name.isEmpty()||name.length()>80)return;JSONObject j=playlists();if(j.has(name))return;try{j.put(name,new JSONArray());savePlaylists(j);}catch(Exception ignored){}}
    public void deletePlaylist(String name){JSONObject j=playlists();j.remove(name);savePlaylists(j);}
    public void addToPlaylist(String name,Track t){JSONObject j=playlists();JSONArray a=j.optJSONArray(name);if(a==null)a=new JSONArray();for(int i=0;i<a.length();i++)if(t.uri.equals(a.optString(i)))return;a.put(t.uri);try{j.put(name,a);savePlaylists(j);}catch(Exception ignored){}}
    public void removeFromPlaylist(String name,String uri){JSONObject j=playlists();JSONArray a=j.optJSONArray(name),b=new JSONArray();if(a!=null)for(int i=0;i<a.length();i++)if(!uri.equals(a.optString(i)))b.put(a.optString(i));try{j.put(name,b);savePlaylists(j);}catch(Exception ignored){}}
    public List<String> playlist(String name){JSONArray a=playlists().optJSONArray(name);List<String> r=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)r.add(a.optString(i));return r;}
    private void savePlaylists(JSONObject j){prefs.edit().putString("playlists",j.toString()).apply();}
    public List<Track> tracks(String key){List<Track> r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){Track t=Track.json(a.getJSONObject(i));if(MusicLogic.localUri(t.uri))r.add(t);}}catch(Exception ignored){}return r;}
    public void saveTracks(String key,List<Track> tracks){JSONArray a=new JSONArray();for(Track t:tracks)a.put(t.json());prefs.edit().putString(key,a.toString()).apply();}
    public void addImport(Track t){List<Track> r=tracks("imports");r.removeIf(x->x.uri.equals(t.uri));r.add(t);saveTracks("imports",r);}
    public void played(Track t){List<Track> r=tracks("history");r.removeIf(x->x.uri.equals(t.uri));r.add(0,t);if(r.size()>100)r=r.subList(0,100);saveTracks("history",r);}
    public String lyric(String uri){return prefs.getString("lyric:"+uri,"");}
    public void lyric(String uri,String text){prefs.edit().putString("lyric:"+uri,text).apply();}
    public String backup() throws JSONException {
        JSONObject root=new JSONObject().put("format","freesic-backup").put("version",1).put("playlists",playlists()).put("favorites",new JSONArray(favorites()));
        return root.toString(2);
    }
    public void restore(String raw) throws JSONException {
        JSONObject root=new JSONObject(raw);if(!"freesic-backup".equals(root.optString("format"))||root.optInt("version")!=1)throw new JSONException("Formato de backup inválido");
        JSONObject incoming=root.getJSONObject("playlists"),merged=playlists();Set<String> fav=favorites();JSONArray f=root.getJSONArray("favorites");
        if(incoming.length()>500||f.length()>50000)throw new JSONException("Backup grande demais");
        for(int i=0;i<f.length();i++){String uri=f.getString(i);if(MusicLogic.localUri(uri))fav.add(uri);}
        Iterator<String> names=incoming.keys();while(names.hasNext()){
            String name=names.next();if(name.trim().isEmpty()||name.length()>80)throw new JSONException("Nome de playlist inválido");
            JSONArray a=incoming.getJSONArray(name),old=merged.optJSONArray(name),dest=new JSONArray();LinkedHashSet<String> ids=new LinkedHashSet<>();
            if(a.length()>50000)throw new JSONException("Playlist grande demais");
            if(old!=null)for(int i=0;i<old.length();i++)ids.add(old.optString(i));
            for(int i=0;i<a.length();i++)if(MusicLogic.localUri(a.optString(i)))ids.add(a.getString(i));
            for(String id:ids)dest.put(id);merged.put(name,dest);
        }
        prefs.edit().putString("playlists",merged.toString()).putStringSet("favorites",fav).apply();
    }
}
