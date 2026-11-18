package com.benoit.salle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            Vibrator v = c.getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1));
        } catch (Exception e) {
            // pas de vibreur
        }
        Rest.done(c);
    }
}
