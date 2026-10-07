package com.freesic.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;

/** Scroll/enter-always behavior including the tabs, as requested for Freesic. */
final class CollapsingChrome extends ViewGroup {
    private final View header, body, scroller;
    private final ScrollView headerViewport;
    private float lastY, startX, startY;
    private int offset, headerHeight;
    private boolean tracking, fromHeader, layoutAdjustment,touchActive;
    private int lastPosition=-1,lastTop;
    private final int slop;

    CollapsingChrome(Context context, View header, View body, View scroller) {
        super(context); this.header=header; this.body=body; this.scroller=scroller;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        headerViewport=new ScrollView(context);headerViewport.setVerticalScrollBarEnabled(false);headerViewport.addView(header);
        addView(body); addView(headerViewport); setClipChildren(true);
        if(scroller instanceof AbsListView)((AbsListView)scroller).setOnScrollListener(new AbsListView.OnScrollListener(){
            public void onScrollStateChanged(AbsListView view,int state){}
            public void onScroll(AbsListView view,int first,int visible,int total){
                if(view.getChildCount()==0){lastPosition=-1;return;}
                View row=view.getChildAt(0);int top=row.getTop();
                if(lastPosition>=0&&!layoutAdjustment&&!touchActive)move((first-lastPosition)*row.getHeight()+lastTop-top);
                lastPosition=first;lastTop=top;
            }
        });
        else if(scroller instanceof ScrollView)scroller.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(!layoutAdjustment&&!touchActive)move(y-oldY);});
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec), height=MeasureSpec.getSize(heightSpec);
        header.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int minimumBody=(int)(64*getResources().getDisplayMetrics().density);
        headerHeight=Math.min(header.getMeasuredHeight(), Math.max(0,height-minimumBody));
        offset=Math.min(offset,headerHeight);
        headerViewport.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(headerHeight,MeasureSpec.EXACTLY));
        body.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,height-headerHeight+offset),MeasureSpec.EXACTLY));
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        headerViewport.layout(0,-offset,r-l,headerHeight-offset);
        body.layout(0,headerHeight-offset,r-l,b-t);
        header.setImportantForAccessibility(offset>=headerHeight?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        layoutAdjustment=false;
    }
    private void move(int delta){int next=Math.max(0,Math.min(headerHeight,offset+delta));if(next!=offset){offset=next;layoutAdjustment=true;requestLayout();}}
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchActive=true;lastY=startY=event.getY();startX=event.getX();tracking=false;fromHeader=startY<headerHeight-offset;break;
            case MotionEvent.ACTION_MOVE:
                float dy=lastY-event.getY();
                if(!tracking && Math.abs(event.getY()-startY)>slop && Math.abs(event.getY()-startY)>Math.abs(event.getX()-startX)) tracking=true;
                // Consumed scrolling (including flings/accessibility) is handled above.
                // At the top edge, a downward drag must reveal chrome even if the list cannot scroll.
                if(tracking && (!fromHeader||(!headerViewport.canScrollVertically(1)&&!headerViewport.canScrollVertically(-1))))move(Math.round(dy));
                lastY=event.getY();break;
            case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_UP:tracking=false;touchActive=false;break;
        }
        return super.dispatchTouchEvent(event);
    }
}
