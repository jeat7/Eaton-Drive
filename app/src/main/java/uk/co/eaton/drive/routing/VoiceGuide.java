package uk.co.eaton.drive.routing;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;

/** Speaks only using an installed, offline-capable English voice. */
public final class VoiceGuide implements TextToSpeech.OnInitListener {
    private TextToSpeech engine;
    private boolean ready;
    private boolean enabled = true;
    private String state = "Preparing offline voice";
    public VoiceGuide(Context context) { engine = new TextToSpeech(context.getApplicationContext(), this); }
    @Override public void onInit(int status) {
        if (engine == null || status != TextToSpeech.SUCCESS) {
            state = "Speech engine unavailable";
            return;
        }
        int language = engine.setLanguage(Locale.UK);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            state = "Install an English offline speech voice in Android settings";
            return;
        }
        Voice offline = null;
        try {
            for (Voice v : engine.getVoices()) {
                if (!v.isNetworkConnectionRequired() && v.getLocale().getLanguage().equals("en")) {
                    if (v.getLocale().getCountry().equals("GB")) { offline = v; break; }
                    if (offline == null) offline = v;
                }
            }
        } catch (Exception ignored) { }
        if (offline == null) { state = "No offline English voice installed"; return; }
        engine.setVoice(offline);
        engine.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        ready = true; state = "Offline voice ready";
    }
    public String status() { return enabled ? state : "Voice muted"; }
    public void setEnabled(boolean value) {
        enabled=value;
        if (!enabled && engine != null) engine.stop();
    }
    public boolean toggle() {
        enabled = !enabled;
        if (!enabled && engine != null) engine.stop();
        return enabled;
    }
    public void say(String message) {
        if (enabled && ready && engine != null && message != null && !message.isEmpty())
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "eaton-" + System.nanoTime());
    }
    public void shutdown() {
        if (engine != null) { engine.stop(); engine.shutdown(); engine = null; }
    }
}
