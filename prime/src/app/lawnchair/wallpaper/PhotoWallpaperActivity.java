package app.lawnchair.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
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
        // ACTION_PICK opens a photo-oriented gallery instead of the last Files folder.
        // Fall back to the generic image document picker if no gallery is installed.
        Intent pick = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pick.setType("image/*");
        try {
            startActivityForResult(pick, REQUEST_IMAGE);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(fallback, REQUEST_IMAGE);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "Aucune application pour choisir une photo",
                        Toast.LENGTH_LONG).show();
                finish();
            }
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
