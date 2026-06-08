package online.alawanmart.alawanmart;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class CallActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String phone = getIntent().getStringExtra("phone");
        if (phone != null && !phone.isEmpty()) {
            // Auto dial immediately - this works because we are in a FOREGROUND ACTIVITY
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phone));
            startActivity(callIntent);
        }

        // Close this activity after 2 seconds
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                finish();
            }
        }, 2000);
    }
}
