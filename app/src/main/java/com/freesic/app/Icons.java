package com.freesic.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.Button;
import java.util.*;
/** Official Google Material icons, Apache-2.0. Text labels remain available to accessibility. */
public final class Icons extends Button {
    private static final Map<String,String> names=new HashMap<>();
    static {String[][] pairs={{"▶","play"},{"Ⅱ","pause"},{"❮❮","previous"},{"❯❯","next"},{"⇄","shuffle"},{"↻","repeat"},{"↻¹","repeat_one"},{"♡","heart"},{"♥︎","heart_filled"},{"⋮","more"},{"⌄","down"},{"⇅","sort"},{"+","add"}};for(String[] pair:pairs)names.put(pair[0],pair[1]);}
    public float glyphSize=24;public Icons(Context c){super(c);for(String n:new String[]{"search","up","down","right","more","volume","equalizer","queue","playlist","settings","music","lyrics","add"})names.put("@"+n,n);}
    public static boolean has(String s){return s.startsWith("@")||names.containsKey(s);}
    public static Drawable named(Context c,String name){return c.getDrawable(c.getResources().getIdentifier("ic_"+name,"drawable",c.getPackageName())).mutate();}
    @Override public void setText(CharSequence value,android.widget.TextView.BufferType type){if(value!=null&&value.toString().contentEquals(getText()))return;super.setText(value,type);}
    @Override protected void onDraw(Canvas canvas){String name=names.get(getText().toString());if(name==null){super.onDraw(canvas);return;}Drawable d=named(getContext(),name);int size=(int)(glyphSize*getResources().getDisplayMetrics().density);d.setTint(getCurrentTextColor());d.setBounds((getWidth()-size)/2,(getHeight()-size)/2,(getWidth()+size)/2,(getHeight()+size)/2);d.draw(canvas);}
}
