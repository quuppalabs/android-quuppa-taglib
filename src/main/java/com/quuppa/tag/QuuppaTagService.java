// Copyright 2022 Quuppa Oy
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//    http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.quuppa.tag;

import java.lang.reflect.Method;

import com.quuppa.tag.QuuppaTag.DeviceType;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class QuuppaTagService extends Service implements SensorEventListener {
	public static long STATIONARY_TRESHOLD_MS = 60000L;
	
	public static Icon ICON;
	public static String NOTIFICATION_CHANNEL_ID = "QuuppaTagNotification";
	public static String NOTIFICATION_CHANNEL_NAME = "Quuppa Tag Notifications";

	public static String NOTIFICATION_DEFAULT_TEXT = "Quuppa Tag active";

	public enum IntentAction {
		QT_SYSTEM_ERROR, QT_SYSTEM_EVENT, QT_BLE_NOT_ENABLED, QT_MOVING, QT_STATIONARY, QT_STARTED, QT_STOPPED, QT_STATIONARY_CHECK
	};

	private SensorManager sensorManager;
	private Sensor accelerometer;
	private PowerManager.WakeLock wakeLock;

	private String notificationChannelId;

	private long lastMoved;
	private boolean moving = true;

	// needed just to stop starting a new scan when service is going down
	private volatile boolean running = false;

	private static long STATIONARY_CHECK_INTERVAL = 1000L * 60;
	
	private boolean advertisingStarted;

	private String tagId;
	private DeviceType deviceType;
	private Class<? extends Activity> notifiedActivityClass;

	private AdvertisingSetCallback advertisingSetCallback = new AdvertisingSetCallback() {

		@Override
		public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
			Log.v(QuuppaTagService.class.getSimpleName(), "onAdvertisingSetStarted() status " + status + ", moving " + moving);
		}

		@Override
		public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
			Log.v(QuuppaTagService.class.getSimpleName(), "onAdvertisingEnabled(), enable " + enable);
		}

		@Override
		public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
			Log.v(QuuppaTagService.class.getSimpleName(), "onAdvertisingDataSet(), status " + status);
		}

		@Override
		public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
			Log.v(QuuppaTagService.class.getSimpleName(), "onPeriodicAdvertisingEnabled(), enable " + enable);
		}

		@Override
		public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
			Log.v(QuuppaTagService.class.getSimpleName(), "onAdvertisingSetStopped()");
			advertisingStarted = false;
		}
	};

	@Override
	public void onCreate() {
		super.onCreate();
		ICON = Icon.createWithResource(this, android.R.drawable.ic_menu_mylocation); //  only mylocation available in 8, otherwise could also use perm_group_location as default

		notificationChannelId = createNotificationChannel(this);
		PendingIntent pendingIntent = null;
		
		notifiedActivityClass = QuuppaTag.getNotifiedActivityClass(this);
		if (notifiedActivityClass != null) {
			Intent notificationIntent = new Intent(this, notifiedActivityClass);
			pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,  PendingIntent.FLAG_IMMUTABLE);
		}

		// content intent *can be* null, in that case user clicking notification just doesn't lead anywhere
		Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setContentTitle("Quuppa Tag Service").setContentText(NOTIFICATION_DEFAULT_TEXT).setSmallIcon(ICON)
				.setVisibility(Notification.VISIBILITY_PRIVATE)
				.setContentIntent(pendingIntent).build();
		if (Build.VERSION.SDK_INT >= 34) // Build.VERSION_CODES.UPSIDE_DOWN_CAKE 
		{
            // startForeground(1, notification, 8) 
			try {
				// Note: Beginning with SDK Version Build.VERSION_CODES.UPSIDE_DOWN_CAKE, apps targeting SDK Version 
				// Build.VERSION_CODES.UPSIDE_DOWN_CAKE or higher are not allowed to start foreground services without 
				// specifying a valid foreground service type in the manifest attribute R.attr.foregroundServiceType, 
				// and the parameter foregroundServiceType here must not be the ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE. 
				// See Behavior changes: Apps targeting Android 14 for more details.
				// https://developer.android.com/reference/android/app/Service#startForeground(int,%20android.app.Notification,%20int)
				Method method = getClass().getMethod("startForeground", new Class[] {int.class, Notification.class, int.class});
				// We don't need location updates - yes RTLS produces location, but the app doesn't need it
				// method.invoke(this, 1, notification, 8); // ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION 
				method.invoke(this, 1, notification, 16); // ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE 
			} catch (Exception e) {
				// shouldn't fail
				Log.v(QuuppaTagService.class.getSimpleName(), "startForeground failed because: " + e.getCause());
			}
		}
        else startForeground(1, notification);
	}
	
	private void init() {
		tagId = QuuppaTag.getOrInitTagId(this);
		deviceType = QuuppaTag.getOrInitDeviceType(this);

		lastMoved = System.currentTimeMillis();
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		
		startAdvertisingSet();
		startStationaryCheckAlarm();

		// Acquire wake lock
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuuppaTagService::WakeLock");
		wakeLock.acquire();
		running = true;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(getClass().getSimpleName(), "Start service called with intent action " + (intent == null ? null : intent.getAction()));

		if (!isEnabled()) return START_NOT_STICKY;
		
		if (!running) {
			init();
		}
		// Especially QT_STATIONARY_CHECK
		else if (intent.getAction() != null) adjustAdvertisingSchedule();
		return START_STICKY;
	}
	
	private boolean isEnabled() {
        SharedPreferences sharedPrefs = getSharedPreferences(
                QuuppaTag.PREFS, Context.MODE_PRIVATE);
        return sharedPrefs.getBoolean(QuuppaTag.PREFS_ENABLED, false);
	}

	protected void adjustAdvertisingSchedule() {
		if (!running) return;
		
		System.out.println("adjustAdvertisingSchedule(), moving " + moving + ", periodicAdvertisingStarted "
				+ advertisingStarted);
		
		boolean wasMoving = moving;
		moving = (System.currentTimeMillis() - lastMoved < STATIONARY_TRESHOLD_MS);
		
		if (!advertisingStarted)
			startAdvertisingSet();
		else if (moving != wasMoving) {
			stopAdvertisingSet();
			startAdvertisingSet();
		}
	}

	private PendingIntent getStationaryAlarmIntent() {
		Intent intent = new Intent(this, QuuppaTagService.class);
		intent.setAction(IntentAction.QT_STATIONARY_CHECK.name());
		return PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	private void startStationaryCheckAlarm() {
		getSystemService(AlarmManager.class).setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
				STATIONARY_CHECK_INTERVAL, getStationaryAlarmIntent());
	}

	private void stopStationaryCheckAlarm() {
		getSystemService(AlarmManager.class).cancel(getStationaryAlarmIntent());
	}

	protected void stopAdvertisingSet() {
		try {
			BluetoothLeAdvertiser bluetoothLeAdvertiser = QuuppaTag.getBluetoothLeAdvertiser(this);
			Log.d(getClass().getSimpleName(), "stopPeriodicAdvertisingSet");
			bluetoothLeAdvertiser.stopAdvertisingSet(advertisingSetCallback);
			advertisingStarted = false;
		} catch (QuuppaTagException e) {
			sendBroadcast(new Intent(IntentAction.QT_BLE_NOT_ENABLED.name()));
			return;
		}

	}

	// never throw exception but send error broadcasts that can be listened to
	protected void startAdvertisingSet() {
		byte[] bytes = null;
		try {
			bytes = QuuppaTag.createQuuppaDFPacketAdvertiseData(tagId, deviceType, moving);
		} catch (QuuppaTagException e) {
			// this should only fail in case of an IOException
			e.printStackTrace();
			sendBroadcast(new Intent(IntentAction.QT_SYSTEM_ERROR.name()));
			return;
		}

		// primary channel interval is 0.625ms per unit,
		// https://developer.android.com/reference/android/bluetooth/le/AdvertisingSetParameters.Builder#setInterval(int)
		// ~3Hz / 0.1 Hz
		int interval = moving ? 533 : 16000;
		
		int advertisingSetTxPower = QuuppaTag.getAdvertisingSetTxPower(this);

		AdvertisingSetParameters primaryChannelAdvertisingSetParameters = new AdvertisingSetParameters.Builder()
				.setLegacyMode(true)
				.setConnectable(true)
				.setScannable(true)
				.setInterval(interval)
				.setTxPowerLevel(advertisingSetTxPower)
				.build();

		// Neither txpower level nor device name doesn't fit in legacy mode with our manufacturer data
		AdvertiseData advertiseData = new AdvertiseData.Builder()
				.setIncludeTxPowerLevel(false)
				.setIncludeDeviceName(false)
				.addManufacturerData(0x00C7, bytes).build();

		AdvertiseData scanResponse = null;
		int maxExtendedAdvertisingEvents = 0;
		int duration = 0; 

		try {
			BluetoothLeAdvertiser bluetoothLeAdvertiser = QuuppaTag.getBluetoothLeAdvertiser(this);
			Log.d(getClass().getSimpleName(), "startAdvertisingSet");
			
			bluetoothLeAdvertiser.startAdvertisingSet(primaryChannelAdvertisingSetParameters, advertiseData,
					scanResponse, null, null, duration, maxExtendedAdvertisingEvents, advertisingSetCallback);

			if (!BluetoothAdapter.getDefaultAdapter().isLeExtendedAdvertisingSupported()) {
			    Log.e(getClass().getSimpleName(), "Extended advertising is not supported on this device.");
			}
			else Log.v(getClass().getSimpleName(), "Extended advertising is supported on this device.");
			
			advertisingStarted = true;
		} catch (IllegalArgumentException iae) {
			Log.e(getClass().getSimpleName(),
					"Couldn't start advertising because: " + iae.getMessage());
		} catch (QuuppaTagException e) {
			sendBroadcast(new Intent(IntentAction.QT_BLE_NOT_ENABLED.name()));
			return;
		}

	}

	@Override
	public void onDestroy() {
		Log.d(getClass().getSimpleName(), "service onDestroy()");
		running = false;
		sensorManager.unregisterListener(this);

		if (advertisingStarted) stopAdvertisingSet();
		stopStationaryCheckAlarm();

		NotificationManager manager = getSystemService(NotificationManager.class);
		if (notificationChannelId != null)
			manager.deleteNotificationChannel(notificationChannelId);
		notificationChannelId = null;

		// Release wake lock
		if (wakeLock != null && wakeLock.isHeld()) {
			wakeLock.release();
		}

		super.onDestroy();
	}

	private static String createNotificationChannel(Context context) {
		NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
				NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
		NotificationManager manager = context.getSystemService(NotificationManager.class);
		manager.createNotificationChannel(notificationChannel);
		return notificationChannel.getId();
	}

	public double SHAKE_THRESHOLD = 1.3;

	private float[] gravity;
	private double accel;
	private double accelCurrent;
	private double accelLast;

	@Override
	public void onSensorChanged(SensorEvent event) {
		Sensor sensor = event.sensor;

		if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			gravity = event.values.clone();
			// Shake detection
			double x = gravity[0];
			double y = gravity[1];
			double z = gravity[2];
			accelLast = accelCurrent;
			accelCurrent = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
			double delta = accelCurrent - accelLast;
			accel = accel * 0.9f + delta;

			if (accel > SHAKE_THRESHOLD) {
				Log.v(getClass().getSimpleName(), "Moved, was moving " + moving);
				lastMoved = System.currentTimeMillis();
				if (!moving) adjustAdvertisingSchedule();
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		System.out.println("Accl accuracy changed " + accuracy);
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(getClass().getSimpleName(), "Service onBind() called with intent action " + intent.getAction());
		return null;
	}
}
