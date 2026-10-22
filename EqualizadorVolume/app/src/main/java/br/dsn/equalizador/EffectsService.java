package br.dsn.equalizador;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.IBinder;

/**
 * Serviço em primeiro plano: mantém o app vivo e escutando as sessões de áudio
 * dos players, e tenta aplicar os efeitos na mixagem global (sessão 0).
 */
public class EffectsService extends Service {

    public static final String ACTION_STOP = "br.dsn.equalizador.STOP";
    private static final String CHANNEL_ID = "efeitos";
    private static final int NOTIF_ID = 42;

    private static volatile boolean running = false;

    public static boolean isRunning() { return running; }

    private final AudioSessionReceiver receiver = new AudioSessionReceiver();
    private boolean receiverRegistered = false;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createChannel();
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, f);
        }
        receiverRegistered = true;

        // Tentativa de efeito global (funciona só em alguns aparelhos/versões)
        EffectsEngine.get(this).attach(0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        EffectsEngine.get(this).detach(0);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Efeitos de áudio", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), piFlags);
        Intent stop = new Intent(this, EffectsService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Equalizador ativo")
                .setContentText("Aplicando efeitos aos players de áudio")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(0, "Parar", stopPi);
        return b.build();
    }
}
