package com.avilixradiomod.client.audio;

import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Very small MP3 streaming helper based on JLayer.
 * Runs decoding on a dedicated thread; {@link #stop()} interrupts playback.
 *
 * Volume is applied in software (PCM scaling) so it works on any audio device.
 */
public final class Mp3StreamPlayer {

    private Thread thread;
    private volatile AdvancedPlayer player;
    private volatile InputStream stream;

    private final AtomicBoolean stopping = new AtomicBoolean(false);

    /** True if the last start/play attempt failed (connection/decoder/etc). */
    private volatile boolean failed = false;

    /** Volume is read by the audio thread. */
    private final AtomicInteger volumePercent = new AtomicInteger(100);

    public synchronized void play(final String url, final int initialVolumePercent) {
        stop();

        if (url == null) return;
        final String u = url.trim();
        if (u.isEmpty()) return;
        if (!(u.startsWith("http://") || u.startsWith("https://"))) return;

        failed = false;
        setVolume(initialVolumePercent);

        stopping.set(false);
        thread = new Thread(() -> run(u), "AvilixRadio-MP3");
        thread.setDaemon(true);
        thread.start();
    }

    /** Returns true once when a failure happened, then resets the flag. */
    public boolean consumeFailed() {
        if (!failed) return false;
        failed = false;
        return true;
    }

    public void setVolume(int volume) {
        volumePercent.set(Math.max(0, Math.min(100, volume)));
    }

    public synchronized void stop() {
        stopping.set(true);
        closeQuietly(player);
        player = null;
        closeQuietly(stream);
        stream = null;

        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private void run(final String url) {
        try {
            final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("User-Agent", "AvilixRadioMod");
            conn.connect();

            InputStream raw = conn.getInputStream();
            stream = new BufferedInputStream(raw, 256 * 1024);

            final VolumeAudioDevice device = new VolumeAudioDevice(volumePercent);
            final AdvancedPlayer p = new AdvancedPlayer(stream, device);

            player = p;
            p.play();
        } catch (Throwable t) {
            // If we aren't stopping intentionally, mark as failed so controller can switch track.
            if (!stopping.get()) {
                failed = true;
            }
        } finally {
            closeQuietly(player);
            player = null;
            closeQuietly(stream);
            stream = null;
        }
    }

    private static void closeQuietly(Object o) {
        try {
            if (o == null) return;
            if (o instanceof AdvancedPlayer ap) {
                ap.close();
            } else if (o instanceof Closeable c) {
                c.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Audio device that applies volume by scaling PCM samples. */
    private static final class VolumeAudioDevice extends JavaSoundAudioDevice {
        private final AtomicInteger volumePercent;

        private VolumeAudioDevice(AtomicInteger volumePercent) {
            this.volumePercent = volumePercent;
        }

        @Override
        public void write(short[] samples, int offs, int len) throws javazoom.jl.decoder.JavaLayerException {
            int v = volumePercent.get();

            // 0..100 -> 0..1, square curve (nicer at low volumes)
            float t = v / 100.0f;
            float gain = t * t;

            if (gain <= 0.0001f) {
                short[] zeros = new short[len];
                super.write(zeros, 0, len);
                return;
            }

            short[] out = new short[len];
            for (int i = 0; i < len; i++) {
                int s = samples[offs + i];
                int scaled = Math.round(s * gain);
                if (scaled > 32767) scaled = 32767;
                if (scaled < -32768) scaled = -32768;
                out[i] = (short) scaled;
            }

            super.write(out, 0, len);
        }
    }
}
