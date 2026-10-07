package com.freesic.app;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/** Axis-locked gestures; controls keep their normal touch and accessibility behavior. */
final class PlayerGestureLayout extends ScrollView {
    interface Callbacks { boolean next(int direction); void minimize(); default boolean begin(){return true;} }
    private final Callbacks callbacks;
    private View artwork;
    private float x,y;
    private int axis;
    private boolean artworkStart, topStart, blocked;private float startTranslation;
    private VelocityTracker velocity;
    private final int slop, fling;
    PlayerGestureLayout(Context context,Callbacks callbacks){
        super(context);this.callbacks=callbacks;
        ViewConfiguration config=ViewConfiguration.get(context);slop=Math.max(1,Math.round(config.getScaledTouchSlop()*.8f));
        fling=Math.max(config.getScaledMinimumFlingVelocity(),(int)(480*getResources().getDisplayMetrics().density));
        setFillViewport(true);setVerticalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);
    }
    void artwork(View view){artwork=view;}
    private void start(MotionEvent e){
        blocked=!callbacks.begin();if(blocked)return;animate().cancel();startTranslation=getTranslationY();x=e.getRawX();y=e.getRawY();axis=0;topStart=getScrollY()==0;
        Rect bounds=new Rect();artworkStart=artwork!=null&&artwork.getVisibility()==VISIBLE&&artwork.getGlobalVisibleRect(bounds)&&bounds.contains((int)e.getRawX(),(int)e.getRawY());
        if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(e);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN)start(e);
        if(blocked)return true;
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            float dx=e.getRawX()-x,dy=e.getRawY()-y;
            if(axis==0&&Math.max(Math.abs(dx),Math.abs(dy))>slop){
                if(artworkStart&&Math.abs(dx)>Math.abs(dy)*1.2f)axis=1;
                else if(topStart&&dy>0&&Math.abs(dy)>Math.abs(dx)*1.2f)axis=2;
            }
            if(axis!=0){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;}
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)releaseVelocity();
        return super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&velocity==null)start(e);
        if(velocity!=null)velocity.addMovement(e);
        float dx=e.getRawX()-x,dy=e.getRawY()-y;
        if(blocked)return true;
        if(axis==0&&e.getActionMasked()==MotionEvent.ACTION_MOVE&&Math.max(Math.abs(dx),Math.abs(dy))>slop){if(artworkStart&&Math.abs(dx)>Math.abs(dy)*1.2f)axis=1;else if(topStart&&dy>0&&Math.abs(dy)>Math.abs(dx)*1.2f)axis=2;}
        if(axis==0){if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(getTranslationY()!=0)animate().translationY(0).setDuration(MotionCurves.duration(400)).setInterpolator(MotionCurves.CLOSE).start();releaseVelocity();}return super.onTouchEvent(e);}
        if(blocked)return true;
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE){
            if(axis==1&&artwork!=null)moveCovers(dx,false);else setTranslationY(Math.max(0,startTranslation+dy));return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
            boolean cancelled=e.getActionMasked()==MotionEvent.ACTION_CANCEL;float vx=0,vy=0;
            if(velocity!=null){velocity.computeCurrentVelocity(1000);vx=velocity.getXVelocity();vy=velocity.getYVelocity();}
            if(axis==1&&artwork!=null){
                boolean switched=!cancelled&&(Math.abs(dx)>artwork.getWidth()*.4f||(Math.abs(vx)>fling&&Math.abs(dx)>slop*2))&&callbacks.next(dx<0?1:-1);
                if(!switched)moveCovers(0,true);
            }else if(!cancelled&&(startTranslation+dy>getHeight()*.4f||(vy>fling&&dy>slop*2)))callbacks.minimize();
            else animate().translationY(0).setDuration(MotionCurves.duration(400)).setInterpolator(MotionCurves.CLOSE).start();
            axis=0;releaseVelocity();return true;
        }
        return true;
    }
    private void moveCovers(float offset,boolean animate){
        if(!(artwork instanceof android.view.ViewGroup))return;
        android.view.ViewGroup group=(android.view.ViewGroup)artwork;
        float progress=Math.min(1,Math.abs(offset)/Math.max(1,artwork.getWidth()));
        for(int i=0;i<group.getChildCount();i++){
            View child=group.getChildAt(i);if(!(child instanceof BrandView))continue;
            float base="neighbor-left".equals(child.getTag())?-artwork.getWidth():"neighbor-right".equals(child.getTag())?artwork.getWidth():0;
            float scale=base==0?1-.1f*Math.min(1,progress/.3f):.9f+.1f*Math.max(0,(progress-.7f)/.3f);
            if(animate)child.animate().translationX(base).scaleX(1).scaleY(1).setDuration(MotionCurves.duration(300)).setInterpolator(MotionCurves.TRACK).start();
            else {child.animate().cancel();child.setTranslationX(base+offset);child.setScaleX(scale);child.setScaleY(scale);}
        }
    }
    private void releaseVelocity(){if(velocity!=null){velocity.recycle();velocity=null;}}
    @Override protected void onDetachedFromWindow(){releaseVelocity();super.onDetachedFromWindow();}
}
