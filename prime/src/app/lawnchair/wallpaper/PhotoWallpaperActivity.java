package app.lawnchair.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.List;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Bridges the Android image chooser to WallpaperManager without requiring storage permission.
 * The chosen image is applied only to the home screen; cancelling changes nothing.
 */
public class PhotoWallpaperActivity extends Activity {
    private static final int REQUEST_IMAGE = 1;
    private static final String TAG = "PhotoWallpaper";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        // One Android chooser: galleries/files via GET_CONTENT and wallpaper
        // providers via explicit SET_WALLPAPER intents. The chooser returns a
        // selected image to this activity so Prime can apply it system-wide.
        Intent images = new Intent(Intent.ACTION_GET_CONTENT);
        images.setType("image/*");
        images.addCategory(Intent.CATEGORY_OPENABLE);
        Intent wallpaperQuery = new Intent(Intent.ACTION_SET_WALLPAPER);
        List<ResolveInfo> providers = getPackageManager().queryIntentActivities(
                wallpaperQuery, 0);
        ArrayList<Intent> wallpaperIntents = new ArrayList<>();
        for (ResolveInfo provider : providers) {
            if (provider.activityInfo == null) continue;
            Intent wallpaper = new Intent(wallpaperQuery);
            wallpaper.setClassName(provider.activityInfo.packageName,
                    provider.activityInfo.name);
            wallpaperIntents.add(wallpaper);
        }
        Intent chooser = Intent.createChooser(images, "Fonds d'écran",
                wallpaperIntents.toArray(new Intent[0]));
        try {
            startActivityForResult(chooser, REQUEST_IMAGE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Aucune application de fond d'écran disponible",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMAGE) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            finish();
            return;
        }
        new Thread(() -> {
            boolean success = false;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream != null) {
                    WallpaperManager.getInstance(this).setStream(stream, null, true,
                            WallpaperManager.FLAG_SYSTEM);
                    success = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Unable to apply selected wallpaper", e);
            }
            final boolean applied = success;
            runOnUiThread(() -> {
                Toast.makeText(this, applied ? "Fond d'écran appliqué"
                        : "Impossible d'appliquer cette image", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
