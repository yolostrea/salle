package com.benoit.salle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (RunPlan.ACT.equals(i.getAction())) {
            RunPlan.phase(c, i);
            return;
        }
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } catch (Exception e) {
            // pas de vibreur
        }
        // bip sur le volume « alarme » : s'entend même téléphone en mode vibreur ou silencieux
        try {
            final android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 90);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 900);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tg::release, 1500);
        } catch (Exception e) {
            // pas de son disponible
        }
        Rest.done(c);
    }
}
