package com.freesic.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.concurrent.CompletableFuture;

/** Recyclable artwork target. It only requests pixels when the view is actually drawn. */
public final class BrandView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private final RectF imageBounds = new RectF();
    private final Drawable logo;
    private final Drawable music;
    private Bitmap bitmap;
    private Track track;
    private String key = "";
    private int generation = -1;
    private int binding;
    private int requestedSize;
    private boolean requested;
    private CompletableFuture<Bitmap> request;
    private final Runnable retry = this::invalidate;

    public BrandView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        logo = context.getDrawable(R.drawable.brand_logo).mutate();
        music = context.getDrawable(R.drawable.ic_music).mutate();
        music.setTint(0xff78B8FF);
    }

    public void track(Track value) {
        String next = value == null ? "" : value.uri + '\n' + value.art + '\n' + value.added + '\n' + value.duration;
        if (next.equals(key) && generation == Artwork.revision) return;
        cancelBinding();
        key = next;
        track = value;
        generation = Artwork.revision;
        bitmap = null;
        invalidate();
    }

    private void cancelBinding() {
        binding++;
        requested = false;
        removeCallbacks(retry);
        if (request != null) request.cancel(false);
        request = null;
    }

    @Override protected void onDetachedFromWindow() {
        cancelBinding();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        bounds.set(0, 0, width, height);
        clip.rewind();
        float radius = Math.min(width, height) * .09f;
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        int size = Artwork.sizeBucket(Math.max(width, height));
        if (size != requestedSize) {
            cancelBinding();
            requestedSize = size;
        }
    }

    private void requestVisibleArtwork() {
        if (track == null || getWidth() == 0 || getHeight() == 0) return;
        if (generation != Artwork.revision) {
            generation = Artwork.revision;
            cancelBinding();
            bitmap = null;
        }
        if (requested) return;
        requested = true;
        int token = binding;
        request = Artwork.request(getContext(), track, Math.max(getWidth(), getHeight()));
        request.whenComplete((result, error) -> post(() -> {
            if (token != binding || !isAttachedToWindow() || generation != Artwork.revision) return;
            if (error != null) {
                requested = false;
                postDelayed(retry, 200);
                return;
            }
            bitmap = result;
            invalidate();
        }));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        requestVisibleArtwork();
        float width = getWidth(), height = getHeight(), side = Math.min(width, height);
        canvas.save();
        canvas.clipPath(clip);
        if (bitmap != null) {
            float scale = Math.max(width / bitmap.getWidth(), height / bitmap.getHeight());
            float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
            imageBounds.set((width - bw) / 2, (height - bh) / 2, (width + bw) / 2, (height + bh) / 2);
            canvas.drawBitmap(bitmap, null, imageBounds, paint);
        } else {
            canvas.drawColor(0xff101827);
            if (track == null) {
                logo.setBounds(0, 0, (int)width, (int)height);
                logo.draw(canvas);
            } else {
                int iconSide = (int)(side * .48f);
                music.setBounds(((int)width - iconSide) / 2, ((int)height - iconSide) / 2,
                        ((int)width + iconSide) / 2, ((int)height + iconSide) / 2);
                music.draw(canvas);
            }
        }
        canvas.restore();
    }
}
