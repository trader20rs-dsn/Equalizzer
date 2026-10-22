package br.dsn.equalizador;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Motor central: guarda as configurações e aplica Equalizador, Graves,
 * Surround e Amplificador de volume (LoudnessEnhancer) em cada sessão de áudio.
 */
public final class EffectsEngine {

    public static final int STEP_MB = 50;          // passo do equalizador / amplificador
    public static final int MAX_BOOST_MB = 1500;   // +15 dB no máximo
    private static final int MAX_SESSIONS = 8;

    private static EffectsEngine instance;

    public static synchronized EffectsEngine get(Context ctx) {
        if (instance == null) {
            instance = new EffectsEngine(ctx.getApplicationContext());
        }
        return instance;
    }

    // ---- informações do aparelho (preenchidas em probe()) ----
    public int numBands = 5;
    public int minLevel = -1500;
    public int maxLevel = 1500;
    public int[] centerHz = {60, 230, 910, 3600, 14000};
    public String[] presetNames = new String[0];
    public int[][] presetLevels = new int[0][];
    public boolean eqSupported, bassSupported, virtSupported, loudSupported;

    // ---- estado atual ----
    private boolean enabled;
    private int[] bands;
    private int boostMb;
    private int bassStrength;
    private int virtStrength;
    private boolean globalAttached;

    private final Map<Integer, EffectSet> sessions = new LinkedHashMap<>();
    private final SharedPreferences prefs;

    private EffectsEngine(Context appCtx) {
        prefs = appCtx.getSharedPreferences("efeitos", Context.MODE_PRIVATE);
        probe();
        load();
    }

    // ------------------------------------------------------------------
    // Descoberta das capacidades do aparelho
    // ------------------------------------------------------------------
    private void probe() {
        MediaPlayer mp = null;
        Equalizer eq = null;
        try {
            mp = new MediaPlayer();
            int sid = mp.getAudioSessionId();
            try {
                eq = new Equalizer(0, sid);
            } catch (Exception e) {
                eq = new Equalizer(0, 0);
            }
            numBands = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            minLevel = range[0];
            maxLevel = range[1];
            centerHz = new int[numBands];
            for (int b = 0; b < numBands; b++) {
                centerHz[b] = eq.getCenterFreq((short) b) / 1000;
            }
            int np = eq.getNumberOfPresets();
            presetNames = new String[np];
            presetLevels = new int[np][numBands];
            for (int p = 0; p < np; p++) {
                presetNames[p] = eq.getPresetName((short) p);
                eq.usePreset((short) p);
                for (int b = 0; b < numBands; b++) {
                    presetLevels[p][b] = eq.getBandLevel((short) b);
                }
            }
            eqSupported = true;

            try {
                BassBoost bb = new BassBoost(0, sid);
                bassSupported = bb.getStrengthSupported();
                bb.release();
            } catch (Exception ignored) {
            }
            try {
                Virtualizer v = new Virtualizer(0, sid);
                virtSupported = v.getStrengthSupported();
                v.release();
            } catch (Exception ignored) {
            }
            try {
                LoudnessEnhancer le = new LoudnessEnhancer(sid);
                loudSupported = true;
                le.release();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            eqSupported = false;
        } finally {
            try {
                if (eq != null) eq.release();
            } catch (Exception ignored) {
            }
            try {
                if (mp != null) mp.release();
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // Persistência
    // ------------------------------------------------------------------
    private void load() {
        enabled = prefs.getBoolean("enabled", true);
        boostMb = clamp(prefs.getInt("boost", 0), 0, MAX_BOOST_MB);
        bassStrength = clamp(prefs.getInt("bass", 0), 0, 1000);
        virtStrength = clamp(prefs.getInt("virt", 0), 0, 1000);
        bands = new int[numBands];
        String s = prefs.getString("bands", "");
        if (!s.isEmpty()) {
            String[] parts = s.split(",");
            if (parts.length == numBands) {
                try {
                    for (int i = 0; i < numBands; i++) {
                        bands[i] = clamp(Integer.parseInt(parts[i].trim()), minLevel, maxLevel);
                    }
                } catch (NumberFormatException e) {
                    bands = new int[numBands];
                }
            }
        }
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bands.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(bands[i]);
        }
        prefs.edit()
                .putBoolean("enabled", enabled)
                .putInt("boost", boostMb)
                .putInt("bass", bassStrength)
                .putInt("virt", virtStrength)
                .putString("bands", sb.toString())
                .apply();
    }

    // ------------------------------------------------------------------
    // Leitura / alteração das configurações
    // ------------------------------------------------------------------
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized int getBand(int i) { return bands[i]; }
    public synchronized int getBoostMb() { return boostMb; }
    public synchronized int getBass() { return bassStrength; }
    public synchronized int getVirt() { return virtStrength; }

    public synchronized void setEnabled(boolean on) {
        enabled = on;
        save();
        applyAll();
    }

    public synchronized void setBand(int band, int levelMb) {
        if (band < 0 || band >= numBands) return;
        bands[band] = clamp(levelMb, minLevel, maxLevel);
        save();
        applyAll();
    }

    public synchronized void applyPreset(int preset) {
        if (preset < 0 || preset >= presetLevels.length) return;
        for (int b = 0; b < numBands; b++) {
            bands[b] = clamp(presetLevels[preset][b], minLevel, maxLevel);
        }
        save();
        applyAll();
    }

    public synchronized void setBoostMb(int mb) {
        boostMb = clamp(mb, 0, MAX_BOOST_MB);
        save();
        applyAll();
    }

    public synchronized void setBass(int strength) {
        bassStrength = clamp(strength, 0, 1000);
        save();
        applyAll();
    }

    public synchronized void setVirt(int strength) {
        virtStrength = clamp(strength, 0, 1000);
        save();
        applyAll();
    }

    public synchronized void reset() {
        bands = new int[numBands];
        boostMb = 0;
        bassStrength = 0;
        virtStrength = 0;
        save();
        applyAll();
    }

    // ------------------------------------------------------------------
    // Sessões de áudio
    // ------------------------------------------------------------------
    public synchronized boolean attach(int session) {
        if (sessions.containsKey(session)) return true;
        EffectSet set = new EffectSet(session);
        if (set.isEmpty()) {
            set.release();
            return false;
        }
        // evita acumular sessões mortas
        while (sessions.size() >= MAX_SESSIONS) {
            Iterator<Map.Entry<Integer, EffectSet>> it = sessions.entrySet().iterator();
            Map.Entry<Integer, EffectSet> oldest = it.next();
            if (oldest.getKey() == 0) {
                if (!it.hasNext()) break;
                oldest = it.next();
            }
            oldest.getValue().release();
            it.remove();
        }
        sessions.put(session, set);
        if (session == 0) globalAttached = true;
        set.apply(this);
        return true;
    }

    public synchronized void detach(int session) {
        EffectSet set = sessions.remove(session);
        if (set != null) {
            set.release();
            if (session == 0) globalAttached = false;
        }
    }

    public synchronized void releaseAll() {
        for (EffectSet s : sessions.values()) s.release();
        sessions.clear();
        globalAttached = false;
    }

    private void applyAll() {
        for (EffectSet s : sessions.values()) s.apply(this);
    }

    public synchronized String describeSessions() {
        if (sessions.isEmpty()) return "nenhuma sessão de áudio ativa";
        StringBuilder sb = new StringBuilder();
        for (Integer id : sessions.keySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(id == 0 ? "global" : String.valueOf(id));
        }
        return sessions.size() + " sessão(ões): " + sb;
    }

    public synchronized boolean isGlobalAttached() { return globalAttached; }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------
    // Conjunto de efeitos de UMA sessão
    // ------------------------------------------------------------------
    private static final class EffectSet {
        private Equalizer eq;
        private BassBoost bass;
        private Virtualizer virt;
        private LoudnessEnhancer loud;

        EffectSet(int session) {
            try { eq = new Equalizer(0, session); } catch (Exception e) { eq = null; }
            try { bass = new BassBoost(0, session); } catch (Exception e) { bass = null; }
            try { virt = new Virtualizer(0, session); } catch (Exception e) { virt = null; }
            try { loud = new LoudnessEnhancer(session); } catch (Exception e) { loud = null; }
        }

        boolean isEmpty() {
            return eq == null && bass == null && virt == null && loud == null;
        }

        void apply(EffectsEngine e) {
            boolean on = e.enabled;
            try {
                if (eq != null) {
                    int n = Math.min(e.numBands, eq.getNumberOfBands());
                    for (int b = 0; b < n; b++) {
                        eq.setBandLevel((short) b, (short) clamp(e.bands[b], e.minLevel, e.maxLevel));
                    }
                    eq.setEnabled(on);
                }
            } catch (Exception ignored) {
            }
            try {
                if (bass != null) {
                    if (bass.getStrengthSupported()) bass.setStrength((short) e.bassStrength);
                    bass.setEnabled(on && e.bassStrength > 0);
                }
            } catch (Exception ignored) {
            }
            try {
                if (virt != null) {
                    if (virt.getStrengthSupported()) virt.setStrength((short) e.virtStrength);
                    virt.setEnabled(on && e.virtStrength > 0);
                }
            } catch (Exception ignored) {
            }
            try {
                if (loud != null) {
                    loud.setTargetGain(e.boostMb);
                    loud.setEnabled(on && e.boostMb > 0);
                }
            } catch (Exception ignored) {
            }
        }

        void release() {
            try { if (eq != null) eq.release(); } catch (Exception ignored) { }
            try { if (bass != null) bass.release(); } catch (Exception ignored) { }
            try { if (virt != null) virt.release(); } catch (Exception ignored) { }
            try { if (loud != null) loud.release(); } catch (Exception ignored) { }
            eq = null; bass = null; virt = null; loud = null;
        }
    }
}
