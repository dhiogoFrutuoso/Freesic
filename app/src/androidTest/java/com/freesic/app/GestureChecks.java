package com.freesic.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Real ViewGroup dispatch checks with a non-clickable artwork target. No playback required. */
final class GestureChecks {
    private static final class Fixture {
        final FrameLayout host;
        final PlayerGestureLayout player;
        final FrameLayout art;
        final BrandView cover;
        int nextCalls, direction, minimized;
        Fixture(Activity activity) {
            host=new FrameLayout(activity);
            player=new PlayerGestureLayout(activity,new PlayerGestureLayout.Callbacks(){
                public boolean next(int value){nextCalls++;direction=value;return false;}
                public void minimize(){minimized++;}
            });
            LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
            art=new FrameLayout(activity);cover=new BrandView(activity);
            art.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            content.addView(art,new LinearLayout.LayoutParams(320,240));
            content.addView(new View(activity),new LinearLayout.LayoutParams(320,700));
            player.addView(content);player.artwork(art);
            host.addView(player,new FrameLayout.LayoutParams(320,600));
            ((ViewGroup)activity.getWindow().getDecorView()).addView(host,new ViewGroup.LayoutParams(320,600));
            host.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));
            host.layout(0,0,320,600);
        }
        void event(long down,long now,int action,float x,float y){
            int[] origin=new int[2];host.getLocationOnScreen(origin);
            MotionEvent event=MotionEvent.obtain(down,now,action,origin[0]+x,origin[1]+y,0);
            event.offsetLocation(-origin[0],-origin[1]);
            try{host.dispatchTouchEvent(event);}finally{event.recycle();}
        }
        void remove(){player.animate().cancel();cover.animate().cancel();((ViewGroup)host.getParent()).removeView(host);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static Fixture create(Instrumentation instrumentation,Activity activity){
        AtomicReference<Fixture> fixture=new AtomicReference<>();instrumentation.runOnMainSync(()->fixture.set(new Fixture(activity)));return fixture.get();
    }
    private static void swipe(Instrumentation instrumentation,Fixture f,float fromX,float fromY,float toX,float toY,boolean cancel){
        long down=SystemClock.uptimeMillis();
        instrumentation.runOnMainSync(()->f.event(down,down,MotionEvent.ACTION_DOWN,fromX,fromY));
        for(int step=1;step<=6;step++){
            final int current=step;SystemClock.sleep(100);
            instrumentation.runOnMainSync(()->f.event(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,fromX+(toX-fromX)*current/6f,fromY+(toY-fromY)*current/6f));
        }
        instrumentation.runOnMainSync(()->f.event(down,SystemClock.uptimeMillis(),cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,toX,toY));
    }
    static List<String> run(Instrumentation instrumentation,Activity activity){
        List<String> passed=new ArrayList<>();Fixture f=create(instrumentation,activity);
        try{
            instrumentation.runOnMainSync(()->require(!f.cover.isClickable()&&!f.art.isClickable(),"gesture fixture artwork must be non-clickable"));
            swipe(instrumentation,f,280,120,40,120,false);
            instrumentation.runOnMainSync(()->require(f.nextCalls==1&&f.direction==1,"non-clickable artwork left swipe must request next exactly once"));
            passed.add("real dispatch on non-clickable artwork triggers next exactly once");
            SystemClock.sleep(400);
            instrumentation.runOnMainSync(()->require(Math.abs(f.cover.getTranslationX())<1,"rejected queue edge must settle artwork to its original position"));
            passed.add("queue boundary restores artwork after rejected swipe");
            swipe(instrumentation,f,40,120,280,120,false);
            instrumentation.runOnMainSync(()->require(f.nextCalls==2&&f.direction==-1,"right swipe must request previous exactly once"));
            passed.add("right swipe requests previous exactly once");
            SystemClock.sleep(400);
            swipe(instrumentation,f,160,120,160,490,true);
            instrumentation.runOnMainSync(()->require(f.minimized==0&&f.nextCalls==2,"cancelled vertical gesture must neither minimize nor skip"));
            SystemClock.sleep(500);
            instrumentation.runOnMainSync(()->require(Math.abs(f.player.getTranslationY())<1,"cancelled vertical gesture must settle to zero"));
            passed.add("cancelled vertical drag settles without minimizing or skipping");
            swipe(instrumentation,f,160,120,160,490,false);
            instrumentation.runOnMainSync(()->require(f.minimized==1,"committed downward gesture must minimize exactly once"));
            passed.add("downward drag minimizes exactly once");
            instrumentation.runOnMainSync(()->f.player.setTranslationY(0));
            swipe(instrumentation,f,240,120,96,120,false);
            instrumentation.runOnMainSync(()->require(f.nextCalls==3,"45 percent artwork drag must commit below the previous 50 percent threshold"));
            passed.add("slow 45 percent artwork drag commits with reduced threshold");
            SystemClock.sleep(400);
            swipe(instrumentation,f,240,120,128,120,false);
            instrumentation.runOnMainSync(()->require(f.nextCalls==3,"35 percent slow drag must not change track"));
            passed.add("slow drag below 40 percent settles without changing track");
            SystemClock.sleep(400);
            swipe(instrumentation,f,160,120,160,390,false);
            instrumentation.runOnMainSync(()->require(f.minimized==2,"45 percent player drag must minimize below the previous threshold"));
            passed.add("slow 45 percent player drag minimizes with reduced threshold");
        }finally{instrumentation.runOnMainSync(f::remove);}
        return passed;
    }
    private GestureChecks(){}
}
