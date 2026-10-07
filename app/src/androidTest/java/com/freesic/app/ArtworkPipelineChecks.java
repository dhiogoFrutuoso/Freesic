package com.freesic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled local fixtures, no music library or network is required. */
public final class ArtworkPipelineChecks {
    private static void check(boolean value, String name, List<String> passed) {
        if (!value) throw new AssertionError(name);
        passed.add(name);
    }
    private static Object field(Class<?> type, Object instance, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }
    private static void fixture(File file, int color) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
    }
    public static List<String> run(Context context) throws Exception {
        List<String> passed = new ArrayList<>();
        String id = UUID.randomUUID().toString();
        Track track = new Track("content://com.freesic.test/" + id, "Controlled artwork", "Test", "", "", "", 1000, 1, false);
        File source = new File(context.getCacheDir(), "artwork-fixture-" + id + ".png");
        Store store = new Store(context);
        String preference = "art_override_" + track.uri;
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        try {
            fixture(source, Color.RED);
            store.prefs.edit().putString(preference, Uri.fromFile(source).toString()).commit();
            Bitmap small = Artwork.request(context, track, 96).get(10, TimeUnit.SECONDS);
            check(small != null && small.getWidth() == 128 && small.getHeight() == 64,
                    "artwork: row decodes 2048px source to 128px preserving aspect", passed);
            check(Artwork.request(context, track, 96).get(2, TimeUnit.SECONDS) == small,
                    "artwork: repeated row request reuses the cached bitmap", passed);
            Bitmap large = Artwork.request(context, track, 500).get(10, TimeUnit.SECONDS);
            check(large != null && large.getWidth() == 512 && large != small,
                    "artwork: full player receives an independent 512px image", passed);
            ((LruCache<?, ?>)field(Artwork.class, null, "cache")).evictAll();
            check(source.delete(), "artwork: fixture source removed for disk-cache check", passed);
            Bitmap disk = Artwork.request(context, track, 96).get(10, TimeUnit.SECONDS);
            check(disk != null && disk.getPixel(20, 20) == Color.RED,
                    "artwork: persisted thumbnail works without reopening original source", passed);
            fixture(source, Color.BLUE);
            Artwork.invalidate(track.uri);
            Bitmap updated = Artwork.request(context, track, 96).get(10, TimeUnit.SECONDS);
            check(updated != null && updated.getPixel(20, 20) == Color.BLUE,
                    "artwork: explicit revision replaces stale memory and disk artwork", passed);

            ThreadPoolExecutor workers = (ThreadPoolExecutor)field(Artwork.class, null, "workers");
            CountDownLatch entered = new CountDownLatch(3);
            for (int i = 0; i < 3; i++) workers.execute(() -> {
                entered.countDown();
                try { releaseWorkers.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            });
            if (!entered.await(10, TimeUnit.SECONDS)) throw new AssertionError("workers did not enter fixture gate");
            Artwork.invalidate(track.uri);
            CompletableFuture<Bitmap> first = Artwork.request(context, track, 250);
            CompletableFuture<Bitmap> second = Artwork.request(context, track, 250);
            Map<?, ?> pending = (Map<?, ?>)field(Artwork.class, null, "pending");
            Object job = pending.entrySet().stream().filter(entry -> entry.getKey().toString().startsWith(track.uri + "\n"))
                    .map(Map.Entry::getValue).findFirst().orElseThrow(() -> new AssertionError("missing shared job"));
            check(((AtomicInteger)field(job.getClass(), job, "consumers")).get() == 2,
                    "artwork: concurrent targets share one queued decoder job", passed);
            first.cancel(false);
            check(!second.isDone(), "artwork: recycling one target preserves the other subscriber", passed);
            releaseWorkers.countDown();
            check(second.get(10, TimeUnit.SECONDS) != null, "artwork: remaining target receives shared result", passed);

            Track missing = new Track("content://com.freesic.test/missing-" + id, "Missing image", "", "", "", "", 0, 0, false);
            check(Artwork.request(context, missing, 128).get(10, TimeUnit.SECONDS) == null,
                    "artwork: absent cover remains absent", passed);
            check(Artwork.request(context, missing, 512).isDone(),
                    "artwork: negative result is reused across display sizes", passed);
            return passed;
        } finally {
            releaseWorkers.countDown();
            store.prefs.edit().remove(preference).remove("art_revision_" + track.uri).commit();
            source.delete();
        }
    }
}
