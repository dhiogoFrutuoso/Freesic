package com.freesic.app;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class MusicLogic {
    public static int clampGain(int percent){return Math.max(100,Math.min(300,percent));}
    public static int gainMillibels(int percent){return (int)Math.round(2000*Math.log10(clampGain(percent)/100.0));}
    private MusicLogic() {}
    public static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
    public static boolean localUri(String uri) {
        return uri != null && (uri.startsWith("content://") || uri.startsWith("file://"));
    }
    public static String time(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds/3600, seconds/60%60, seconds%60)
                : String.format(Locale.ROOT, "%d:%02d", seconds/60, seconds%60);
    }
    public static final class LyricLine {
        public final long ms; public final String text;
        public LyricLine(long ms, String text) { this.ms=ms; this.text=text; }
    }
    public static List<LyricLine> lyrics(String raw) {
        List<LyricLine> result=new ArrayList<>();
        Pattern timestamp=Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
        Matcher offsetMatch=Pattern.compile("\\[offset:([+-]?\\d+)\\]",Pattern.CASE_INSENSITIVE).matcher(raw);
        long offset=0;
        if(offsetMatch.find()) try { offset=Long.parseLong(offsetMatch.group(1)); } catch(NumberFormatException ignored) {}
        for(String line:raw.split("\\r?\\n")) {
            Matcher m=timestamp.matcher(line); List<Long> times=new ArrayList<>(); int end=0;
            while(m.find()) {
                try {
                    long min=Long.parseLong(m.group(1)),sec=Long.parseLong(m.group(2));
                    String frac=m.group(3); long milli=frac==null?0:Long.parseLong((frac+"00").substring(0,3));
                    if(sec<60 && min<10000) times.add(Math.max(0,min*60000+sec*1000+milli+offset));
                } catch(NumberFormatException ignored) {}
                end=m.end();
            }
            String text=line.substring(end).trim();
            for(long t:times) result.add(new LyricLine(t,text));
        }
        result.sort(Comparator.comparingLong(a->a.ms)); return result;
    }
    public static int lyricIndex(List<LyricLine> lines,long position) {
        int low=0,high=lines.size()-1,result=-1;
        while(low<=high) { int mid=(low+high)>>>1; if(lines.get(mid).ms<=position){result=mid;low=mid+1;}else high=mid-1; }
        return result;
    }
}
