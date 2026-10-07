package com.freesic.app;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;

/** Chrome is the first scrolling content, never an overlay revealed by reverse scrolling. */
final class CollapsingChrome extends FrameLayout {
    CollapsingChrome(Context context, View header, View body, View scroller) {
        super(context);
        if (scroller instanceof ListView) {
            ListView list = (ListView) scroller;
            ListAdapter tracks = list.getAdapter();
            View empty = list.getEmptyView();
            // Keep the header and empty state in the same native scroll container.
            list.setEmptyView(null);
            list.setVisibility(VISIBLE);
            list.addHeaderView(header, null, false);
            if (empty != null) {
                ((ViewGroup) empty.getParent()).removeView(empty);
                LinearLayout footer = new LinearLayout(context);
                footer.setOrientation(LinearLayout.VERTICAL);
                footer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                list.addFooterView(footer, null, false);
                DataSetObserver observer = new DataSetObserver() {
                    @Override public void onChanged() { update(); }
                    @Override public void onInvalidated() { update(); }
                    private void update() { empty.setVisibility(tracks.getCount() == 0 ? VISIBLE : GONE); }
                };
                tracks.registerDataSetObserver(observer);
                observer.onChanged();
            }
        } else if (scroller instanceof ScrollView) {
            ScrollView scroll = (ScrollView) scroller;
            View contents = scroll.getChildAt(0);
            scroll.removeView(contents);
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.addView(header, new LinearLayout.LayoutParams(-1, -2));
            column.addView(contents, new LinearLayout.LayoutParams(-1, contents.getLayoutParams().height));
            scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        } else {
            throw new IllegalArgumentException("Unsupported tab scroll container");
        }
        addView(body, new FrameLayout.LayoutParams(-1, -1));
    }
}
