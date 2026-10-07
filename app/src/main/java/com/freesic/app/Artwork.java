package com.freesic.app;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.LruCache;
import android.util.Size;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Size-aware local artwork pipeline shared by views and the media notification. */
public final class Artwork {
    public static volatile int revision;
    private static final int INPUT_LIMIT = 15_000_000;
    private static final long MISS_TTL = 5 * 60_000L;
    private static final long DISK_TTL = 24 * 60 * 60_000L;
    private static final long DISK_LIMIT = 32L * 1024 * 1024;
    private static final int MEMORY_LIMIT = (int)Math.max(4L * 1024 * 1024,
            Math.min(24L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12));
    private static final ThreadPoolExecutor workers = new ThreadPoolExecutor(3, 3, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "Freesic-artwork");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(MEMORY_LIMIT) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private static final LruCache<String, Long> misses = new LruCache<>(512);
    private static final ConcurrentHashMap<String, Job> pending = new ConcurrentHashMap<>();
    private static volatile Context application;
    private static final Object diskLock = new Object();
    static { workers.allowCoreThreadTimeOut(true); }

    private static final class Job {
        final String key;
        final CompletableFuture<Bitmap> result = new CompletableFuture<>();
        final AtomicInteger consumers = new AtomicInteger();
        volatile FutureTask<Void> task;
        Job(String key) { this.key = key; }
        CompletableFuture<Bitmap> subscribe() {
            consumers.incrementAndGet();
            CompletableFuture<Bitmap> consumer = new CompletableFuture<>();
            consumer.whenComplete((bitmap, failure) -> {
                if (consumers.decrementAndGet() == 0 && !result.isDone()) {
                    result.cancel(false);
                    FutureTask<Void> active = task;
                    if (active != null) active.cancel(true);
                    pending.remove(key, this);
                    workers.purge();
                }
            });
            result.whenComplete((bitmap, failure) -> {
                if (failure == null) consumer.complete(bitmap);
                else consumer.completeExceptionally(failure);
            });
            return consumer;
        }
    }

    private Artwork() {}

    private static synchronized Context initialize(Context context) {
        if (application == null) {
            application = context.getApplicationContext();
            application.registerComponentCallbacks(new ComponentCallbacks2() {
                @Override public void onConfigurationChanged(Configuration config) {}
                @Override public void onLowMemory() { cache.evictAll(); }
                @Override public void onTrimMemory(int level) {
                    if (level >= TRIM_MEMORY_BACKGROUND) cache.evictAll();
                    else if (level >= TRIM_MEMORY_RUNNING_LOW) cache.trimToSize(MEMORY_LIMIT / 2);
                }
            });
        }
        return application;
    }

    public static boolean soRock(String title) {
        String n = MusicLogic.normalize(title);
        return n.contains("so rock 2") && (n.contains("major") || n.contains("rock danger") || n.equals("so rock 2"))
                && !n.contains("remix") && !n.contains("ao vivo");
    }

    /** Persist the revision so an old disk entry cannot reappear after a process restart. */
    public static synchronized void invalidate(String uri) {
        if (application != null) {
            SharedPreferences prefs = new Store(application).prefs;
            String preference = "art_revision_" + uri;
            prefs.edit().putLong(preference, prefs.getLong(preference, 0) + 1).apply();
        }
        cache.evictAll();
        misses.evictAll();
        revision++;
    }

    static int sizeBucket(int size) {
        if (size <= 128) return 128;
        if (size <= 256) return 256;
        if (size <= 512) return 512;
        return 768;
    }

    public static CompletableFuture<Bitmap> request(Context context, Track track) {
        return request(context, track, 512);
    }

    public static CompletableFuture<Bitmap> request(Context context, Track track, int requestedSize) {
        Context app = initialize(context);
        int size = sizeBucket(requestedSize);
        SharedPreferences prefs = new Store(app).prefs;
        String override = prefs.getString("art_override_" + track.uri, "");
        String sourceKey = track.uri + '\n' + track.art + '\n' + track.title + '\n' + track.duration
                + '\n' + track.video + '\n' + override + '\n' + prefs.getLong("art_revision_" + track.uri, 0);
        String key = sourceKey + '\n' + size;
        Bitmap ready = cache.get(key);
        if (ready != null) return CompletableFuture.completedFuture(ready);
        Long miss = misses.get(sourceKey);
        if (miss != null && SystemClock.elapsedRealtime() - miss < MISS_TTL)
            return CompletableFuture.completedFuture(null);
        Job job = new Job(key);
        Job existing = pending.putIfAbsent(key, job);
        // Cancel the decoder only when its final view/notification consumer no longer needs it.
        if (existing != null) return existing.subscribe();
        CompletableFuture<Bitmap> consumer = job.subscribe();
        CompletableFuture<Bitmap> work = job.result;
        try {
            job.task = new FutureTask<>(() -> {
                Bitmap result = null;
                try {
                    if (work.isCancelled()) return null;
                    File disk = diskFile(app, key);
                    result = readDisk(disk, size);
                    if (result == null) {
                        result = load(app, track, override, size);
                        if (result != null && !work.isCancelled()) writeDisk(disk, result);
                    }
                    if (work.isCancelled()) return null;
                    if (result != null) cache.put(key, result);
                    else misses.put(sourceKey, SystemClock.elapsedRealtime());
                    work.complete(result);
                } catch (Exception failure) {
                    work.complete(null);
                } finally {
                    pending.remove(key, job);
                }
                return null;
            });
            workers.execute(job.task);
        } catch (RejectedExecutionException busy) {
            pending.remove(key, job);
            // A full queue is transient, never negative-cache it as "no artwork".
            work.completeExceptionally(busy);
        }
        return consumer;
    }

    private static Bitmap decode(byte[] data, int size) {
        if (data == null || data.length > INPUT_LIMIT) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, size);
        // Lark's default Glide options prefer RGB_565. Restrict that tradeoff to opaque row JPEGs;
        // preserve alpha and full player color precision for PNG/WebP and larger artwork.
        options.inPreferredConfig = size <= 256 && "image/jpeg".equals(options.outMimeType)
                ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        options.inJustDecodeBounds = false;
        return fit(BitmapFactory.decodeByteArray(data, 0, data.length, options), size);
    }

    static int sampleSize(int width, int height, int size) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= size) sample *= 2;
        return sample;
    }

    private static Bitmap fit(Bitmap bitmap, int size) {
        if (bitmap == null) return null;
        int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (max <= size) return bitmap;
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, Math.max(1, bitmap.getWidth() * size / max),
                Math.max(1, bitmap.getHeight() * size / max), true);
        if (resized != bitmap) bitmap.recycle();
        return resized;
    }

    private static Bitmap read(InputStream input, int size) throws IOException {
        if (input == null) return null;
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) return null;
                if (out.size() + count > INPUT_LIMIT) return null;
                out.write(buffer, 0, count);
            }
            return decode(out.toByteArray(), size);
        }
    }

    private static Bitmap uri(Context context, String source, int size) {
        if (!MusicLogic.localUri(source)) return null;
        try { return read(context.getContentResolver().openInputStream(Uri.parse(source)), size); }
        catch (Exception unavailable) { return null; }
    }

    private static Bitmap load(Context context, Track track, String override, int size) {
        Bitmap bitmap = uri(context, override, size);
        if (bitmap != null) return bitmap;
        // Android's thumbnail provider can reuse its own indexed media cache before opening a decoder.
        if (Build.VERSION.SDK_INT >= 29) try {
            bitmap = context.getContentResolver().loadThumbnail(Uri.parse(track.uri), new Size(size, size), null);
            if (bitmap != null && (!track.video || !uniform(bitmap))) return fit(bitmap, size);
        } catch (Exception unavailable) { /* Older providers may not implement thumbnails. */ }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.parse(track.uri));
            bitmap = decode(retriever.getEmbeddedPicture(), size);
            if (bitmap != null && (!track.video || !uniform(bitmap))) return bitmap;
            if (track.video) {
                long duration = track.duration;
                try { duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)); }
                catch (Exception ignored) {}
                // Lark Lo/xt.c tries the middle, then 5%, 20% and 40% to avoid blank frames.
                long[] positions = {duration * 1000 / 2, track.duration * 5 / 100 * 1000,
                        track.duration * 20 / 100 * 1000, track.duration * 40 / 100 * 1000};
                for (long position : positions) {
                    if (Thread.currentThread().isInterrupted()) return null;
                    bitmap = Build.VERSION.SDK_INT >= 27
                            ? retriever.getScaledFrameAtTime(position, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
                            : retriever.getFrameAtTime(position, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bitmap != null && !uniform(bitmap)) break;
                }
                if (bitmap != null) return fit(bitmap, size);
            }
        } catch (Exception unavailable) {
            // A malformed or removed file must not block the remaining local artwork sources.
        } finally { try { retriever.release(); } catch (Exception ignored) {} }
        bitmap = uri(context, track.art, size);
        if (bitmap != null) return bitmap;
        if (soRock(track.title)) try { return read(context.getAssets().open("artwork/so_rock_2.jpg"), size); }
        catch (Exception unavailable) { return null; }
        return null;
    }

    /** The three sampling lines used by Lark's Lo/xt.d; not a face/content classifier. */
    static boolean uniform(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int previous = bitmap.getPixel(0, height / 2);
        for (int x = 1; x < width; x++) if (bitmap.getPixel(x, height / 2) != previous) return false;
        previous = bitmap.getPixel(width / 2, 0);
        for (int y = 1; y < height; y++) if (bitmap.getPixel(width / 2, y) != previous) return false;
        previous = bitmap.getPixel(0, 0);
        for (int x = 1; x < width; x++) if (bitmap.getPixel(x, x * height / width) != previous) return false;
        return true;
    }

    private static File diskFile(Context context, String key) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder(64);
        for (byte b : bytes) name.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return new File(new File(context.getCacheDir(), "artwork-v2"), name + ".png");
    }

    private static Bitmap readDisk(File file, int size) {
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > DISK_TTL) return null;
        try (InputStream in = new FileInputStream(file)) {
            Bitmap bitmap = read(in, size);
            // Last access provides LRU eviction; source changes are tracked separately in the key.
            if (bitmap != null) file.setLastModified(System.currentTimeMillis());
            return bitmap;
        } catch (IOException unavailable) { return null; }
    }

    private static void writeDisk(File file, Bitmap bitmap) {
        synchronized (diskLock) {
            File directory = file.getParentFile();
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return;
            File temporary = new File(directory, file.getName() + ".tmp");
            try {
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return;
                }
                java.nio.file.Files.move(temporary.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                File[] files = directory.listFiles();
                if (files == null) return;
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                long total = 0;
                for (File item : files) total += item.length();
                int remaining = files.length;
                for (File item : files) {
                    if (total <= DISK_LIMIT && remaining <= 512) break;
                    long length = item.length();
                    if (item.delete()) { total -= length; remaining--; }
                }
            } catch (IOException unavailable) {
                // Disk cache failure must not prevent displaying an already decoded cover.
            } finally { temporary.delete(); }
        }
    }
}
