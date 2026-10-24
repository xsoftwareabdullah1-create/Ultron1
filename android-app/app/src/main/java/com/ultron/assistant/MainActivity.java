package com.ultron.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 101;
    private static final int REQUEST_AUDIO = 102;
    private static final String PREFS = "ultron_settings";
    private static final String API_KEY = "gemini_api_key";
    private EditText messageInput;
    private TextView chatText;
    private TextView statusText;
    private ScrollView chatScroll;
    private SharedPreferences preferences;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        messageInput = findViewById(R.id.messageInput);
        chatText = findViewById(R.id.chatText);
        statusText = findViewById(R.id.statusText);
        chatScroll = findViewById(R.id.chatScroll);

        tts = new TextToSpeech(this, result -> {
            ttsReady = result == TextToSpeech.SUCCESS;
            if (ttsReady) tts.setLanguage(Locale.getDefault());
        });

        Button send = findViewById(R.id.sendButton);
        Button voice = findViewById(R.id.voiceButton);
        Button settings = findViewById(R.id.settingsButton);
        send.setOnClickListener(v -> sendMessage());
        voice.setOnClickListener(v -> startVoiceInput());
        settings.setOnClickListener(v -> showSettings());
        statusText.setText(preferences.getString(API_KEY, "").isEmpty()
                ? "MOBILE SYSTEM • API KEY REQUIRED FOR ONLINE AI"
                : "MOBILE SYSTEM • READY");
    }

    private void showSettings() {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("Paste Gemini API key");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(preferences.getString(API_KEY, ""));
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        keyInput.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("ULTRON Settings")
                .setMessage("Gemini API key stays in this app's private storage. Never post it in GitHub.")
                .setView(keyInput)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear key", (dialog, which) -> {
                    preferences.edit().remove(API_KEY).apply();
                    statusText.setText("MOBILE SYSTEM • API KEY REQUIRED");
                    append("SYSTEM", "Saved API key cleared.");
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    String key = keyInput.getText().toString().trim();
                    if (key.isEmpty()) {
                        Toast.makeText(this, "API key is empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    preferences.edit().putString(API_KEY, key).apply();
                    statusText.setText("MOBILE SYSTEM • READY");
                    append("SYSTEM", "API key saved on this device.");
                }).show();
    }

    private void sendMessage() {
        String prompt = messageInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        messageInput.setText("");
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        append("YOU", prompt);
        String key = preferences.getString(API_KEY, "");
        if (key.isEmpty()) {
            append("ULTRON", "Online AI is not configured yet. Open Settings and add your Gemini API key. Voice input and speech output are available on-device.");
            return;
        }
        statusText.setText("MOBILE SYSTEM • THINKING");
        executor.execute(() -> {
            try {
                String answer = askGemini(key, prompt);
                runOnUiThread(() -> {
                    statusText.setText("MOBILE SYSTEM • READY");
                    append("ULTRON", answer);
                    speak(answer);
                });
            } catch (Exception e) {
                String message = friendlyError(e);
                runOnUiThread(() -> {
                    statusText.setText("MOBILE SYSTEM • CONNECTION ERROR");
                    append("ULTRON", message);
                });
            }
        });
    }

    private String askGemini(String key, String prompt) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                + java.net.URLEncoder.encode(key, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        JSONObject textPart = new JSONObject().put("text", prompt);
        JSONArray parts = new JSONArray().put(textPart);
        JSONObject content = new JSONObject().put("parts", parts);
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }
        JSONObject json = new JSONObject(response.toString());
        if (code < 200 || code >= 300) {
            String detail = json.optJSONObject("error") != null
                    ? json.optJSONObject("error").optString("message", "HTTP " + code)
                    : "HTTP " + code;
            throw new Exception(detail);
        }
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new Exception("The AI returned no answer. Try again.");
        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject answerContent = candidate.optJSONObject("content");
        JSONArray answerParts = answerContent == null ? null : answerContent.optJSONArray("parts");
        if (answerParts == null || answerParts.length() == 0) throw new Exception("The AI returned an empty answer.");
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < answerParts.length(); i++) {
            String piece = answerParts.optJSONObject(i) == null ? "" : answerParts.optJSONObject(i).optString("text", "");
            if (!piece.isEmpty()) {
                if (answer.length() > 0) answer.append("\n");
                answer.append(piece);
            }
        }
        if (answer.length() == 0) throw new Exception("The AI returned an empty answer.");
        return answer.toString();
    }

    private String friendlyError(Exception e) {
        String raw = e.getMessage() == null ? "Unknown network error" : e.getMessage();
        if (raw.contains("API_KEY_INVALID") || raw.toLowerCase(Locale.ROOT).contains("api key not valid"))
            return "The Gemini API key appears invalid. Check it in Settings.";
        if (raw.contains("429") || raw.toLowerCase(Locale.ROOT).contains("quota"))
            return "Gemini rate limit or quota reached. Check your API quota and try later.";
        if (raw.toLowerCase(Locale.ROOT).contains("unable to resolve") || raw.toLowerCase(Locale.ROOT).contains("failed to connect"))
            return "Could not connect to Gemini. Check your internet connection and try again.";
        return "Request failed: " + raw;
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        launchSpeechRecognizer();
    }

    private void launchSpeechRecognizer() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to ULTRON");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognizer();
        } else if (requestCode == REQUEST_AUDIO) {
            Toast.makeText(this, "Microphone permission is needed for voice input.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                messageInput.setText(results.get(0));
                messageInput.setSelection(messageInput.length());
            }
        }
    }

    private void append(String speaker, String message) {
        chatText.append("\n\n" + speaker + ": " + message);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ultron-answer");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
