package com.freesic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.*;

/** Actual platform scrolling: reverse scrolling in the middle never reveals the header. */
final class ChromeChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static List<String> run(Instrumentation instrumentation,Activity activity){
        List<String> passed=new ArrayList<>();
        instrumentation.runOnMainSync(()->{
            FrameLayout host=new FrameLayout(activity);
            ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();
            decor.addView(host,new ViewGroup.LayoutParams(320,600));
            try {
                TextView header=new TextView(activity);header.setText("Header");header.setHeight(120);
                ListView list=new ListView(activity);
                ArrayList<String> rows=new ArrayList<>();for(int i=0;i<80;i++)rows.add("Row "+i);
                ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,rows);
                list.setAdapter(adapter);
                FrameLayout body=new FrameLayout(activity);body.addView(list,new FrameLayout.LayoutParams(-1,-1));
                TextView empty=new TextView(activity);empty.setText("Empty");body.addView(empty);list.setEmptyView(empty);
                CollapsingChrome chrome=new CollapsingChrome(activity,header,body,list);host.addView(chrome);
                Runnable layout=()->{host.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));host.layout(0,0,320,600);};
                layout.run();int height=list.getHeight();
                require(header.getGlobalVisibleRect(new Rect()),"header must be visible at start");
                passed.add("list header visible at tab start");
                list.setSelection(30);layout.run();list.setSelection(20);layout.run();
                require(list.getFirstVisiblePosition()>0&&list.getHeight()==height,"reverse scrolling must not reveal header or resize viewport: first="+list.getFirstVisiblePosition()+", height="+list.getHeight()+", initial="+height);
                passed.add("list reverse scrolling keeps header offscreen and viewport stable");
                list.setSelection(0);layout.run();require(header.getGlobalVisibleRect(new Rect()),"return to top restores header");
                passed.add("list header returns only at beginning");
                adapter.clear();layout.run();require(list.getVisibility()==View.VISIBLE&&empty.getVisibility()==View.VISIBLE&&header.getGlobalVisibleRect(new Rect()),"empty list retains header and empty controls");
                adapter.add("Restored row");layout.run();require(empty.getVisibility()==View.GONE,"empty state disappears when songs return");
                passed.add("empty and refilled library retain usable header and correct empty state");
                host.removeAllViews();
                ScrollView scroll=new ScrollView(activity);View tall=new View(activity);scroll.addView(tall,new ScrollView.LayoutParams(-1,2400));
                TextView second=new TextView(activity);second.setHeight(120);second.setText("Settings header");
                host.addView(new CollapsingChrome(activity,second,scroll,scroll));layout.run();
                scroll.scrollTo(0,900);scroll.scrollTo(0,600);
                require(!second.getGlobalVisibleRect(new Rect())&&scroll.getHeight()==height,"scrollview reverse scrolling must not reveal header");
                scroll.scrollTo(0,0);require(second.getGlobalVisibleRect(new Rect()),"scrollview top restores header");
                passed.add("playlist/settings ScrollView header follows content only at top");
            } finally {decor.removeView(host);}
        });
        return passed;
    }
}
