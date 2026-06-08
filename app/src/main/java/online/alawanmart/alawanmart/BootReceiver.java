package online.alawanmart.alawanmart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent smsIntent = new Intent(ctx, SMSService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(smsIntent);
        else
            ctx.startService(smsIntent);

        SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        if (!prefs.getString("call_token", "").isEmpty()) {
            Intent callIntent = new Intent(ctx, CallerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(callIntent);
            else
                ctx.startService(callIntent);
        }
    }
}
