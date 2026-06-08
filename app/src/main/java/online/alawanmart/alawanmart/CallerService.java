package online.alawanmart.alawanmart;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class CallerService extends Service implements TextToSpeech.OnInitListener {

    static final String CH       = "call_ch";
    static final String ALERT_CH = "call_alert";
    static final int NID         = 1002;
    static final int CALL_NID    = 1003;
    static final long POLL_MS    = 5000;

    Handler handler;
    Runnable poller;
    TextToSpeech tts;
    SharedPreferences prefs;
    boolean ttsReady = false;
    String activeId  = null;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs   = getSharedPreferences("prefs", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        tts     = new TextToSpeech(this, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(
                    CH, "Call Service", NotificationManager.IMPORTANCE_LOW));
                NotificationChannel alertCh = new NotificationChannel(
                    ALERT_CH, "Call Alerts", NotificationManager.IMPORTANCE_HIGH);
                alertCh.setVibrationPattern(new long[]{0, 500, 200, 500});
                nm.createNotificationChannel(alertCh);
            }
        }

        Notification notif;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notif = new Notification.Builder(this, CH)
                .setContentTitle("AlwanMart Caller Active")
                .setContentText("Waiting for orders...")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true).build();
        } else {
            notif = new Notification.Builder(this)
                .setContentTitle("AlwanMart Caller Active")
                .setContentText("Waiting for orders...")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true).build();
        }
        startForeground(NID, notif);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPolling();
        return START_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(new Locale("ur", "PK"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                tts.setLanguage(Locale.ENGLISH);
            tts.setSpeechRate(0.85f);
            ttsReady = true;
        }
    }

    void startPolling() {
        if (poller != null) return;
        poller = new Runnable() {
            @Override public void run() {
                if (activeId == null) poll();
                handler.postDelayed(this, POLL_MS);
            }
        };
        handler.post(poller);
    }

    void poll() {
        final String url = prefs.getString("server_url", "");
        final String tok = prefs.getString("call_token", "");
        if (url.isEmpty() || tok.isEmpty()) return;

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection)
                        new URL(url + "/api/poll_calls.php?token=" + tok).openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    if (conn.getResponseCode() != 200) { conn.disconnect(); return; }

                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    conn.disconnect();

                    JSONObject json = new JSONObject(sb.toString());
                    if (!json.getBoolean("success")) return;
                    JSONArray calls = json.getJSONArray("calls");
                    if (calls.length() == 0) return;

                    JSONObject call = calls.getJSONObject(0);
                    final String id  = call.getString("id");
                    final String oid = call.getString("order_id");
                    final String ph  = call.getString("phone");
                    final int att    = call.optInt("attempt", 1);
                    activeId = id;

                    handler.post(new Runnable() {
                        @Override public void run() {
                            makeCall(ph, id, oid, att, url, tok);
                        }
                    });
                } catch (Exception e) {
                    activeId = null;
                }
            }
        }).start();
    }

    void makeCall(final String ph, final String id, final String oid,
                  final int att, final String url, final String tok) {

        // Launch CallActivity - foreground activity CAN make calls on Android 14
        try {
            Intent actIntent = new Intent(this, CallActivity.class);
            actIntent.putExtra("phone", ph);
            actIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(actIntent);
        } catch (Exception e) {
            // Fallback: direct call attempt
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + ph));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(callIntent);
            } catch (Exception ignored) {}
        }

        // Report back after 45 seconds
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.cancel(CALL_NID);
                report(url, tok, id, oid, att, 0);
                activeId = null;
            }
        }, 45000);
    }

    void report(final String url, final String tok, final String id,
                final String oid, final int att, final int resp) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection)
                        new URL(url + "/api/call_response.php").openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(8000);

                    JSONObject body = new JSONObject();
                    body.put("call_id",  id);
                    body.put("order_id", oid);
                    body.put("response", resp);
                    body.put("attempt",  att);
                    body.put("token",    tok);

                    OutputStreamWriter w = new OutputStreamWriter(conn.getOutputStream());
                    w.write(body.toString());
                    w.flush();
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) { /* silent */ }
            }
        }).start();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && poller != null) handler.removeCallbacks(poller);
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
