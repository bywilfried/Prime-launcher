package com.android.launcher3.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional same-origin site icon cache. Failures never prevent saving or launching a link.
 * No third-party favicon proxy or browser-specific integration is used.
 */
public final class PrimeWebIconCache {
    private static final String TAG = "PrimeWebIconCache";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PrimeWebIconCache() {}

    public static void fetch(Context context, String url) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Uri uri = Uri.parse(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getUserInfo() != null) return;
                File file = fileFor(app, url);
                if (file.isFile()) return;
                Uri favicon = uri.buildUpon().path("/favicon.ico").clearQuery().fragment(null).build();
                HttpURLConnection connection = (HttpURLConnection) new URL(favicon.toString()).openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(false);
                try {
                    if (connection.getResponseCode() != 200) return;
                    if (connection.getContentLengthLong() > 524288) return;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    try (java.io.InputStream stream = connection.getInputStream()) {
                        int count;
                        while ((count = stream.read(buffer)) != -1) {
                            if (bytes.size() + count > 524288) return;
                            bytes.write(buffer, 0, count);
                        }
                    }
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size());
                    if (bitmap == null) return;
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 144, 144, true);
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, output);
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception error) {
                Log.d(TAG, "Site icon unavailable", error);
            }
        });
    }

    public static Bitmap load(Context context, String url) {
        try {
            File file = fileFor(context, url);
            return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void remove(Context context, String url) {
        try {
            fileFor(context, url).delete();
        } catch (Exception ignored) {
            // Cache cleanup is best effort.
        }
    }

    private static File fileFor(Context context, String url) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(url.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder("web_");
        for (byte value : digest) {
            name.append(Character.forDigit((value >> 4) & 15, 16));
            name.append(Character.forDigit(value & 15, 16));
        }
        return new File(context.getCacheDir(), name + ".png");
    }
}
