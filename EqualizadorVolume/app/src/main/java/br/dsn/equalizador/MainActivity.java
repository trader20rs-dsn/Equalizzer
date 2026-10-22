package br.dsn.equalizador;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 11;
    private static final int REQ_NOTIF = 12;

    private static final int BG = Color.parseColor("#0F1B22");
    private static final int FG = Color.parseColor("#ECEFF1");
    private static final int MUTED = Color.parseColor("#90A4AE");
    private static final int ACCENT = Color.parseColor("#26C6DA");
    private static final int WARN = Color.parseColor("#FFB74D");

    private EffectsEngine engine;
    private boolean updating = false;

    private Switch masterSwitch;
    private TextView statusText, boostLabel, bassLabel, virtLabel, playerLabel;
    private SeekBar boostBar, bassBar, virtBar;
    private SeekBar[] bandBars;
    private TextView[] bandLabels;
    private Spinner presetSpinner;
    private Button serviceBtn, playBtn;

    private MediaPlayer player;
    private int playerSession = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 1500);
        }
    };

    // ------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = EffectsEngine.get(this);
        setContentView(buildUi());
        syncControlsFromEngine();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncControlsFromEngine();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // Construção da interface (programática, sem XML de layout)
    // ------------------------------------------------------------------
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Equalizador e Volume+", 24, FG, true);
        root.addView(title);

        masterSwitch = new Switch(this);
        masterSwitch.setText("Efeitos ativados");
        masterSwitch.setTextColor(FG);
        masterSwitch.setTextSize(16);
        masterSwitch.setPadding(0, dp(12), 0, dp(4));
        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!updating) engine.setEnabled(checked);
            }
        });
        root.addView(masterSwitch);

        statusText = text("", 13, MUTED, false);
        root.addView(statusText);

        serviceBtn = new Button(this);
        serviceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleService(); }
        });
        root.addView(serviceBtn);

        // ---- Amplificador de volume ----
        root.addView(section("Amplificador de volume"));
        boostLabel = text("", 15, FG, false);
        root.addView(boostLabel);
        boostBar = new SeekBar(this);
        boostBar.setMax(EffectsEngine.MAX_BOOST_MB / EffectsEngine.STEP_MB);
        boostBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int progress, boolean fromUser) {
                if (fromUser) engine.setBoostMb(progress * EffectsEngine.STEP_MB);
                boostLabel.setText(String.format(Locale.getDefault(), "Ganho extra: +%.1f dB",
                        progress * EffectsEngine.STEP_MB / 100f));
            }
        });
        root.addView(boostBar);
        TextView warn = text("Atenção: ganho alto pode distorcer o som, danificar alto-falantes "
                + "e prejudicar sua audição. Aumente aos poucos.", 12, WARN, false);
        root.addView(warn);
        if (!engine.loudSupported) {
            root.addView(text("Este aparelho não informou suporte ao amplificador (LoudnessEnhancer).",
                    12, WARN, false));
        }

        // ---- Equalizador ----
        root.addView(section("Equalizador"));
        if (!engine.eqSupported) {
            root.addView(text("Equalizador não disponível neste aparelho.", 13, WARN, false));
        }

        String[] names = new String[engine.presetNames.length + 1];
        names[0] = "Personalizado";
        for (int i = 0; i < engine.presetNames.length; i++) names[i + 1] = engine.presetNames[i];
        presetSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names);
        presetSpinner.setAdapter(adapter);
        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updating || position == 0) return;
                engine.applyPreset(position - 1);
                syncControlsFromEngine();
                presetSelectionKeep(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(presetSpinner);

        int n = engine.numBands;
        bandBars = new SeekBar[n];
        bandLabels = new TextView[n];
        final int stepMax = (engine.maxLevel - engine.minLevel) / EffectsEngine.STEP_MB;
        for (int i = 0; i < n; i++) {
            final int band = i;
            bandLabels[i] = text("", 13, FG, false);
            bandLabels[i].setPadding(0, dp(10), 0, 0);
            root.addView(bandLabels[i]);
            bandBars[i] = new SeekBar(this);
            bandBars[i].setMax(stepMax);
            bandBars[i].setOnSeekBarChangeListener(new SimpleSeek() {
                @Override
                void changed(int progress, boolean fromUser) {
                    int level = engine.minLevel + progress * EffectsEngine.STEP_MB;
                    if (fromUser) {
                        engine.setBand(band, level);
                        updating = true;
                        presetSpinner.setSelection(0);
                        updating = false;
                    }
                    bandLabels[band].setText(bandText(band, level));
                }
            });
            root.addView(bandBars[i]);
        }

        // ---- Graves e Surround ----
        root.addView(section("Graves e Surround"));
        bassLabel = text("", 14, FG, false);
        root.addView(bassLabel);
        bassBar = new SeekBar(this);
        bassBar.setMax(100);
        bassBar.setEnabled(engine.bassSupported);
        bassBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int progress, boolean fromUser) {
                if (fromUser) engine.setBass(progress * 10);
                bassLabel.setText("Reforço de graves: " + progress + "%"
                        + (engine.bassSupported ? "" : " (não suportado)"));
            }
        });
        root.addView(bassBar);

        virtLabel = text("", 14, FG, false);
        virtLabel.setPadding(0, dp(10), 0, 0);
        root.addView(virtLabel);
        virtBar = new SeekBar(this);
        virtBar.setMax(100);
        virtBar.setEnabled(engine.virtSupported);
        virtBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            void changed(int progress, boolean fromUser) {
                if (fromUser) engine.setVirt(progress * 10);
                virtLabel.setText("Surround (virtualizador): " + progress + "%"
                        + (engine.virtSupported ? "" : " (não suportado)"));
            }
        });
        root.addView(virtBar);

        Button reset = new Button(this);
        reset.setText("Redefinir tudo");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.reset();
                syncControlsFromEngine();
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(16);
        root.addView(reset, rlp);

        // ---- Player de teste ----
        root.addView(section("Player de teste"));
        root.addView(text("Toque um arquivo de áudio aqui para ouvir os efeitos funcionando "
                + "mesmo que seu player de música não seja compatível.", 12, MUTED, false));
        playerLabel = text("Nenhum arquivo tocando", 13, FG, false);
        playerLabel.setPadding(0, dp(8), 0, 0);
        root.addView(playerLabel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button pick = new Button(this);
        pick.setText("Escolher música");
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { pickAudio(); }
        });
        playBtn = new Button(this);
        playBtn.setText("Pausar");
        playBtn.setEnabled(false);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { togglePlay(); }
        });
        row.addView(pick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(playBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(row);

        return scroll;
    }

    private void presetSelectionKeep(int position) {
        updating = true;
        presetSpinner.setSelection(position);
        updating = false;
    }

    // ------------------------------------------------------------------
    // Sincroniza controles com o estado do motor
    // ------------------------------------------------------------------
    private void syncControlsFromEngine() {
        if (masterSwitch == null) return;
        updating = true;
        masterSwitch.setChecked(engine.isEnabled());
        boostBar.setProgress(engine.getBoostMb() / EffectsEngine.STEP_MB);
        boostLabel.setText(String.format(Locale.getDefault(), "Ganho extra: +%.1f dB",
                engine.getBoostMb() / 100f));
        for (int i = 0; i < engine.numBands; i++) {
            int level = engine.getBand(i);
            bandBars[i].setProgress((level - engine.minLevel) / EffectsEngine.STEP_MB);
            bandLabels[i].setText(bandText(i, level));
        }
        bassBar.setProgress(engine.getBass() / 10);
        bassLabel.setText("Reforço de graves: " + (engine.getBass() / 10) + "%"
                + (engine.bassSupported ? "" : " (não suportado)"));
        virtBar.setProgress(engine.getVirt() / 10);
        virtLabel.setText("Surround (virtualizador): " + (engine.getVirt() / 10) + "%"
                + (engine.virtSupported ? "" : " (não suportado)"));
        updating = false;
        refreshStatus();
    }

    private void refreshStatus() {
        if (statusText == null) return;
        statusText.setText("Serviço: " + (EffectsService.isRunning() ? "ligado" : "desligado")
                + "\nEfeito global: " + (engine.isGlobalAttached() ? "ativo" : "indisponível/desligado")
                + "\nSessões: " + engine.describeSessions());
        serviceBtn.setText(EffectsService.isRunning()
                ? "Parar serviço em segundo plano"
                : "Iniciar serviço em segundo plano");
    }

    private String bandText(int band, int levelMb) {
        int hz = engine.centerHz[band];
        String f = hz >= 1000
                ? (hz % 1000 == 0 ? (hz / 1000) + " kHz" : String.format(Locale.getDefault(), "%.1f kHz", hz / 1000f))
                : hz + " Hz";
        return String.format(Locale.getDefault(), "%s   %+.1f dB", f, levelMb / 100f);
    }

    // ------------------------------------------------------------------
    // Serviço
    // ------------------------------------------------------------------
    private void toggleService() {
        if (EffectsService.isRunning()) {
            stopService(new Intent(this, EffectsService.class));
            handler.postDelayed(new Runnable() {
                @Override
                public void run() { refreshStatus(); }
            }, 300);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        startEffectsService();
    }

    private void startEffectsService() {
        Intent i = new Intent(this, EffectsService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() { refreshStatus(); }
        }, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) {
            // Inicia mesmo se a notificação for negada: o serviço funciona, só não aparece o aviso.
            startEffectsService();
        }
    }

    // ------------------------------------------------------------------
    // Player de teste
    // ------------------------------------------------------------------
    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            playUri(data.getData());
        }
    }

    private void playUri(Uri uri) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            playerSession = player.getAudioSessionId();
            engine.attach(playerSession);
            player.setDataSource(this, uri);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    playBtn.setEnabled(true);
                    playBtn.setText("Pausar");
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    playBtn.setText("Tocar de novo");
                }
            });
            player.prepareAsync();
            playerLabel.setText("Tocando: " + String.valueOf(uri.getLastPathSegment()));
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível tocar o arquivo", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private void togglePlay() {
        if (player == null) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                playBtn.setText("Continuar");
            } else {
                player.start();
                playBtn.setText("Pausar");
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void releasePlayer() {
        if (playerSession >= 0) {
            engine.detach(playerSession);
            playerSession = -1;
        }
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (playBtn != null) playBtn.setEnabled(false);
        if (playerLabel != null) playerLabel.setText("Nenhum arquivo tocando");
    }

    // ------------------------------------------------------------------
    // Helpers de UI
    // ------------------------------------------------------------------
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 17, ACCENT, true);
        t.setPadding(0, dp(24), 0, dp(6));
        return t;
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        abstract void changed(int progress, boolean fromUser);

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            changed(progress, fromUser);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
