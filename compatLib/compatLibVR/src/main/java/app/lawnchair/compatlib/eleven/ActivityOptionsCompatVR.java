package app.lawnchair.compatlib.eleven;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.ten.ActivityOptionsCompatVQ;

@RequiresApi(30)
public class ActivityOptionsCompatVR extends ActivityOptionsCompatVQ {

    @NonNull
    @Override
    public ActivityOptions makeCustomAnimation(
            @NonNull Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener) {
        // Android 11 OEM frameworks can call the pre-Android-12 no-arg
        // OnAnimationStartedListener method. Building against newer platform
        // stubs may produce a listener with only the long-argument method,
        // crashing asynchronously with AbstractMethodError on the main thread.
        // Use basic options on API 30: no framework animation listener is
        // registered, including during widget configuration activity launches.
        // Complete the callback ourselves so Launcher does not retain pending
        // post-launch work waiting for an animation callback that never fires.
        if (finishedListener != null) {
            callbackHandler.post(finishedListener);
        }
        if (startedListener != null) {
            callbackHandler.post(startedListener);
        }
        return ActivityOptions.makeBasic();
    }
}
