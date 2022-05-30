package com.quuppa.tag;

import com.quuppa.tag.QuuppaTagService.IntentAction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SystemEventBroadcastReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, QuuppaTagService.class);
        serviceIntent.setAction(IntentAction.QT_SYSTEM_EVENT.name());
        context.startService(serviceIntent);
    }
}	
