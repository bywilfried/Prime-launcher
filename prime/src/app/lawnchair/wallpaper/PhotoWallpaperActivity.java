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
        // Use GET_CONTENT as the primary action: Samsung may directly launch
        // the gallery when ACTION_PICK is the primary chooser target.
        // Alternate actions let gallery and wallpaper apps participate.
        Intent gallery = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        gallery.setType("image/*");

        Intent files = new Intent(Intent.ACTION_GET_CONTENT);
        files.setType("image/*");
        files.addCategory(Intent.CATEGORY_OPENABLE);

        Intent documents = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        documents.setType("image/*");
        documents.addCategory(Intent.CATEGORY_OPENABLE);

        Intent wallpapers = new Intent(Intent.ACTION_SET_WALLPAPER);
        Intent chooser = Intent.createChooser(files, "Fonds d'écran");
        chooser.putExtra(Intent.EXTRA_ALTERNATE_INTENTS,
                new Intent[] { gallery, documents, wallpapers });
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
