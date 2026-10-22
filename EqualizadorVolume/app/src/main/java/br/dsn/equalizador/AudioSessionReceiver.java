package br.dsn.equalizador;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;

/**
 * Players que seguem o padrão do Android avisam quando abrem/fecham uma sessão
 * de áudio. Aqui anexamos (ou soltamos) os efeitos nessa sessão.
 */
public class AudioSessionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
        if (session <= 0) return;

        EffectsEngine engine = EffectsEngine.get(context);
        String action = intent.getAction();
        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            engine.attach(session);
        } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            engine.detach(session);
        }
    }
}
