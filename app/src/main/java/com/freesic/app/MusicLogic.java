package com.freesic.app;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public final class MusicLogic {
    private static final int[] GAIN_PRESETS = {100, 150, 200, 300};
    private static final int[] GAIN_MILLIBELS = {0, 1000, 4320, 7480};
    /** Lower gains follow Lark; 300% is a Freesic extreme preset requiring explicit confirmation.
     * These are loudness presets, not a linear amplitude or acoustic-power ratio. */
    public static int gainPresetIndex(int percent) {
        int nearest=0;
        for(int i=1;i<GAIN_PRESETS.length;i++)
            if(Math.abs((long)percent-GAIN_PRESETS[i]) < Math.abs((long)percent-GAIN_PRESETS[nearest])) nearest=i;
        return nearest;
    }
    public static int gainPresetPercent(int index){return GAIN_PRESETS[Math.max(0,Math.min(3,index))];}
    public static int clampGain(int percent){return GAIN_PRESETS[gainPresetIndex(percent)];}
    public static int gainMillibels(int percent){return GAIN_MILLIBELS[gainPresetIndex(percent)];}
    private MusicLogic() {}
    public static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
    public static boolean localUri(String uri) {
        return uri != null && (uri.startsWith("content://") || uri.startsWith("file://"));
    }
    public static boolean isYouTubeUrl(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.isEmpty() || s.matches(".*\\s+.*")) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) s = s.substring(8);
        else if (lower.startsWith("http://")) s = s.substring(7);
        else if (lower.contains("://")) return false;
        int end = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' || c == '?' || c == '#' || c == ':') { end = i; break; }
        }
        String host = s.substring(0, end).toLowerCase(Locale.ROOT);
        if (!host.equals("youtu.be") && !host.equals("youtube.com") && !host.endsWith(".youtube.com")) return false;
        String rest = s.substring(end);
        return !rest.isEmpty() && !rest.equals("/");
    }
    public static String normalizeYouTubeUrl(String input) {
        if (!isYouTubeUrl(input)) return "";
        String s = input.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")) return "https://" + s.substring(7);
        if (lower.startsWith("https://")) return s;
        return "https://" + s;
    }
    public static String sanitizeFilename(String name) {
        if (name == null) return "Freesic_audio";
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        while (clean.startsWith(".")) clean = clean.substring(1).trim();
        while (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).trim();
        if (clean.length() > 100) clean = clean.substring(0, 100).trim();
        while (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).trim();
        return clean.isEmpty() ? "Freesic_audio" : clean;
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
