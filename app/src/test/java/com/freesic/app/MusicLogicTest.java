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
    @Test public void amplificationUsesConfirmedExtremeMaximum(){
        assertEquals(0,MusicLogic.gainMillibels(100));
        assertEquals(1000,MusicLogic.gainMillibels(150));
        assertEquals(3000,MusicLogic.gainMillibels(200));
        assertEquals(10000,MusicLogic.gainMillibels(300));
    }
    @Test public void amplificationClampsAndSnapsWithoutIntegerOverflow(){
        assertEquals(100,MusicLogic.clampGain(Integer.MIN_VALUE));
        assertEquals(300,MusicLogic.clampGain(Integer.MAX_VALUE));
        assertEquals(100,MusicLogic.clampGain(125));
        assertEquals(150,MusicLogic.clampGain(126));
        assertEquals(200,MusicLogic.clampGain(250));
        assertEquals(300,MusicLogic.clampGain(251));
    }
    @Test public void amplificationSliderRoundTripsAndClampsIndices(){
        int[] presets={100,150,200,300};
        for(int i=0;i<presets.length;i++){
            assertEquals(presets[i],MusicLogic.gainPresetPercent(i));
            assertEquals(i,MusicLogic.gainPresetIndex(presets[i]));
        }
        assertEquals(100,MusicLogic.gainPresetPercent(-1));
        assertEquals(300,MusicLogic.gainPresetPercent(99));
    }
}
