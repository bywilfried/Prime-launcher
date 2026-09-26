/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util;

import android.content.Context;
import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;

import android.os.Vibrator;

import app.lawnchair.preferences.PreferenceManager;

import androidx.annotation.Nullable;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.logging.DumpManager;

import com.google.android.msdl.data.model.MSDLToken;
import com.google.android.msdl.domain.InteractionProperties;
import com.google.android.msdl.domain.MSDLPlayer;
import com.google.android.msdl.logging.MSDLEvent;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

/**
 * Wrapper around {@link com.google.android.msdl.domain.MSDLPlayer} to perform MSDL feedback.
 */
@LauncherAppSingleton
public class MSDLPlayerWrapper {

    public static final DaggerSingletonObject<MSDLPlayerWrapper> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getMSDLPlayerWrapper);

    /** Internal player */
    private final MSDLPlayer mMSDLPlayer;
    private final Context mContext;

    @Inject
    public MSDLPlayerWrapper(@ApplicationContext Context context,
            DumpManager dumpManager, DaggerSingletonTracker lifeCycle) {
        mContext = context;
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        mMSDLPlayer = MSDLPlayer.Companion.createPlayer(vibrator,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                null /* useHapticFeedbackForToken */);
        lifeCycle.addCloseable(dumpManager.register(this::dump));
    }

    /** Perform MSDL feedback for a token with interaction properties */
    public void playToken(MSDLToken token, InteractionProperties properties) {
        mMSDLPlayer.playToken(token, properties);
    }

    /** Perform MSDL feedback for a token without properties */
    public void playToken(MSDLToken token) {
        playPrimeScaledToken(token, getPrimeHapticPercent(token));
    }

    /** Plays the settings reorder movement token with its independent Prime intensity. */
    public void playPrimeReorderMoveToken(MSDLToken token) {
        int percent = PreferenceManager.getInstance(mContext).getPrimeHapticReorderMove().get();
        playPrimeScaledToken(token, percent);
    }

    private void playPrimeScaledToken(MSDLToken token, int percent) {
        percent = Math.max(0, Math.min(100, percent));
        if (percent <= 0) return;
        if (percent >= 100) {
            mMSDLPlayer.playToken(token, null);
            return;
        }
        int primitive = token == MSDLToken.DRAG_INDICATOR_DISCRETE
                || token == MSDLToken.SWIPE_THRESHOLD_INDICATOR
                ? PRIMITIVE_TICK
                : PRIMITIVE_CLICK;
        long fallbackDurationMs = primitive == PRIMITIVE_TICK ? 8L : 12L;
        boolean scaled = VibratorWrapper.INSTANCE.get(mContext).vibrateScaled(
                primitive, percent / 100f, fallbackDurationMs);
        if (!scaled) {
            mMSDLPlayer.playToken(token, null);
        }
    }

    private int getPrimeHapticPercent(MSDLToken token) {
        PreferenceManager prefs = PreferenceManager.getInstance(mContext);
        if (token == MSDLToken.DRAG_INDICATOR_DISCRETE) {
            return prefs.getPrimeHapticIconDrag().get();
        } else if (token == MSDLToken.SWIPE_THRESHOLD_INDICATOR) {
            return prefs.getPrimeHapticDrawerThreshold().get();
        } else if (token == MSDLToken.TAP_HIGH_EMPHASIS) {
            return prefs.getPrimeHapticDrawerTap().get();
        } else if (token == MSDLToken.START) {
            return prefs.getPrimeHapticReorderStart().get();
        } else if (token == MSDLToken.STOP) {
            return prefs.getPrimeHapticReorderEnd().get();
        } else if (token == MSDLToken.CANCEL) {
            return prefs.getPrimeHapticReorderCancel().get();
        }
        return 100;
    }

    public List<MSDLEvent> getHistory() {
        return mMSDLPlayer.getHistory();
    }

    /** Print the latest history of MSDL tokens played */
    private void dump(String prefix, PrintWriter writer, @Nullable String[] args) {
        writer.println(prefix + mMSDLPlayer.toString());
        writer.println(prefix + "MSDLPlayerWrapper history of latest events:");
        List<MSDLEvent> events = getHistory();
        for (MSDLEvent event: events) {
            writer.println(prefix + "\t" + event);
        }
    }
}
