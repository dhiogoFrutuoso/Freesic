package com.freesic.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class MusicLogicTest {
    @Test public void searchIgnoresAccentsAndCase(){assertEquals("joao gilberto",MusicLogic.normalize(" João Gilberto "));}
    @Test public void onlyLocalSchemesAccepted(){assertTrue(MusicLogic.localUri("content://media/external/audio/media/4"));assertTrue(MusicLogic.localUri("file:///sdcard/song.mp3"));assertFalse(MusicLogic.localUri("https://example.com/song"));assertFalse(MusicLogic.localUri("content:https://example.com"));assertFalse(MusicLogic.localUri(null));}
    @Test public void lrcMultipleTimestampsFractionAndOffset(){List<MusicLogic.LyricLine> lines=MusicLogic.lyrics("[offset:-200]\n[00:10.5][01:02.050]Refrão\n[00:03]Introdução\n[ar:Teste]");assertEquals(3,lines.size());assertEquals(2800,lines.get(0).ms);assertEquals(10300,lines.get(1).ms);assertEquals(61850,lines.get(2).ms);assertEquals("Refrão",lines.get(2).text);}
    @Test public void lyricBoundaryBeforeFirstAndExact(){List<MusicLogic.LyricLine> l=MusicLogic.lyrics("[00:02]A\n[00:03]B");assertEquals(-1,MusicLogic.lyricIndex(l,1999));assertEquals(0,MusicLogic.lyricIndex(l,2000));assertEquals(1,MusicLogic.lyricIndex(l,9999));}
    @Test public void malformedLyricsRemainSafe(){assertTrue(MusicLogic.lyrics("plain lyrics\n[00:99]bad\n[999999999999999999999:01]bad").isEmpty());assertEquals(0,MusicLogic.lyrics("[offset:-5000]\n[00:01]A").get(0).ms);}
    @Test public void timeSupportsLongAudio(){assertEquals("0:00",MusicLogic.time(-5));assertEquals("2:05",MusicLogic.time(125000));assertEquals("1:01:01",MusicLogic.time(3661000));}
    @org.junit.Test public void amplificationConvertsAmplitudeToMillibels(){org.junit.Assert.assertEquals(0,MusicLogic.gainMillibels(100));org.junit.Assert.assertEquals(602,MusicLogic.gainMillibels(200));org.junit.Assert.assertEquals(954,MusicLogic.gainMillibels(300));}
    @org.junit.Test public void amplificationClampsOutOfRangeValues(){org.junit.Assert.assertEquals(0,MusicLogic.gainMillibels(-1));org.junit.Assert.assertEquals(954,MusicLogic.gainMillibels(999));}
}
