package online.alawanmart.alawanmart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SMSReceiver extends BroadcastReceiver {

    static final String[] SENDERS = {
        "JAZZCASH", "EASYPAISA", "MEEZAN", "ALLIED", "FAYSAL", "RAAST", "IBFT", "BANK"
    };

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        final String url = prefs.getString("server_url", "");
        final String tok = prefs.getString("sms_token", "");
        if (url.isEmpty() || tok.isEmpty()) return;

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            if (sms == null) continue;

            final String body   = sms.getMessageBody();
            final String sender = sms.getOriginatingAddress() != null
                ? sms.getOriginatingAddress().toUpperCase() : "";
            final long ts = sms.getTimestampMillis();

            if (body == null) continue;

            boolean isPayment = false;
            for (String s : SENDERS) {
                if (sender.contains(s) || body.toUpperCase().contains(s)) {
                    isPayment = true;
                    break;
                }
            }
            if (!isPayment) continue;

            final String amount = extract(body, new String[]{"PKR ", "Rs.", "Amount: "});
            final String ref    = extract(body, new String[]{"REF#", "REF:", "TXN:", "TRANSACTION ID:"});

            new Thread(new Runnable() {
                @Override public void run() {
                    sendToServer(url, tok, body, sender, amount, ref, ts);
                }
            }).start();
        }
    }

    String extract(String body, String[] keys) {
        for (String key : keys) {
            int idx = body.toUpperCase().indexOf(key.toUpperCase());
            if (idx >= 0) {
                String sub = body.substring(idx + key.length()).trim();
                StringBuilder result = new StringBuilder();
                for (char c : sub.toCharArray()) {
                    if (Character.isLetterOrDigit(c) || c == '.') result.append(c);
                    else if (result.length() > 0) break;
                }
                if (result.length() > 0) return result.toString();
            }
        }
        return "";
    }

    void sendToServer(String url, String tok, String body, String sender,
                      String amount, String ref, long ts) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                new URL(url + "/api/receive_sms.php").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("X-AlawanMart-Token", tok);
            conn.setConnectTimeout(10000);
            conn.setDoOutput(true);

            String data = "token="   + URLEncoder.encode(tok,    "UTF-8")
                        + "&body="   + URLEncoder.encode(body,   "UTF-8")
                        + "&sender=" + URLEncoder.encode(sender, "UTF-8")
                        + "&amount=" + URLEncoder.encode(amount, "UTF-8")
                        + "&ref="    + URLEncoder.encode(ref,    "UTF-8")
                        + "&ts="     + ts;

            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes("UTF-8"));
            os.flush();
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) { /* silent */ }
    }
}
