// Copyright 2025 Quuppa Oy
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.provider.Settings.Secure;

/**
 * Simple Quuppa Tag emulation for Android. Note that the public operation in this abstract class are static and the class is not meant 
 * to be instantiated. The state should be held in the {@link AdvertiseCallback} because Android dictates you must use the same callback 
 * instance to stop the advertisement that you started that specific advertisement. Another thing to consider is the Quuppa Tag ID that 
 * this device (user) will advertise as. By default it's created from Secure.ANDROID_ID but you can also supply your own ID.
 *
 */
public abstract class QuuppaTag {
    public static String PREFS = "QUUPPATAG";
    public static final String PREFS_TAG_ID = "TAG_ID";
	public static final String PREFS_DEVICETYPE = "DEVICETYPE";
	public static final String PREFS_ENABLED = "ENABLED";
	public static final String PREFS_NOTIFIED_ACTIVITY_CLASSNAME = "NOTIFIED_ACTIVITY_CLASSNAME";
	public static final String PREFS_ADVERTISINGSET_TX_POWER = "ADV_TX_POWER";
	
    /** Creates a byte array with the given tag ID */
    private static byte[] createQuuppaAddress(String tagID) {
        byte[] bytes = new byte[6];
        bytes[0] = (byte) Integer.parseInt(tagID.substring(0, 2), 16);
        bytes[1] = (byte) Integer.parseInt(tagID.substring(2, 4), 16);
        bytes[2] = (byte) Integer.parseInt(tagID.substring(4, 6), 16);
        bytes[3] = (byte) Integer.parseInt(tagID.substring(6, 8), 16);
        bytes[4] = (byte) Integer.parseInt(tagID.substring(8, 10), 16);
        bytes[5] = (byte) Integer.parseInt(tagID.substring(10, 12), 16);
        return bytes;
    }
    
	public enum DeviceType {
		SMARTPHONE((byte) 0x21), TABLET((byte) 0x22);

		public byte type;

		DeviceType(byte type) {
			this.type = type;
		}
	}
	
    /** Constructs a byte array using the Direction Finding Packet Specification.
     * Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for more details.
     * @param tagID Tag ID to be injected into the packet
     * @return constructed byte array
     * @throws QuuppaTagException 
     */
    protected static byte[] createQuuppaDFPacketAdvertiseData(String tagID, DeviceType deviceType, boolean moving) throws QuuppaTagException {
        // Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for details

        byte[] bytes = new byte[]{
                (byte) 0x01, // Quuppa Packet ID
                deviceType.type, // Device Type (0x21 = android smartphone, 0x22 = android tablet)
                moving ? (byte) 0x1D : (byte) 0x1C, // Payload header (0x1D = moving at walking speed, 0x1C = stationary)
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, // Quuppa Address payload, will be replaced shortly...
                (byte) 0xb4, // checksum, calculated later
                (byte) 0x67, (byte) 0xF7, (byte) 0xDB, (byte) 0x34, (byte) 0xC4, (byte) 0x03, (byte) 0x8E, (byte) 0x5C, (byte) 0x0B, (byte) 0xAA, (byte) 0x97, (byte) 0x30, (byte) 0x56, (byte) 0xE6 // DF field, 14 octets
        };

        // inject Quuppa Address into byte array
        byte[] qAddress = createQuuppaAddress(tagID);
        System.arraycopy(qAddress, 0, bytes, 3, 6);

        // calculate CRC and inject
        try {
            bytes[9] = CRC8.simpleCRC(Arrays.copyOfRange(bytes, 1, 9));
        } catch (Exception e) {
            throw new QuuppaTagException("CRC failed", e);
        }
        return bytes;
    }
        
    public synchronized static String getOrInitTagId(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        String tagId = sharedPrefs.getString(PREFS_TAG_ID, null);
        if (tagId == null) {
        	tagId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        	if (tagId == null || tagId.length() < 12) tagId = UUID.randomUUID().toString();
            Editor editor = sharedPrefs.edit();
            tagId = tagId.substring(tagId.length() - 12);
            editor.putString(PREFS_TAG_ID, tagId);
            editor.commit();
        }    	
    	return tagId;
    }
    
	public static void setTagId(Context context, String tagId) {
		SharedPreferences sharedPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		Editor editor = sharedPrefs.edit();
		editor.putString(PREFS_TAG_ID, tagId);
		editor.commit();
	}
	
	public static int getAdvertisingSetTxPower(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sharedPrefs.getInt(PREFS_ADVERTISINGSET_TX_POWER, AdvertisingSetParameters.TX_POWER_HIGH );
	}
	
	private static final Set<Integer> availableTxPowers = new HashSet<>(Arrays.asList(AdvertisingSetParameters.TX_POWER_HIGH, AdvertisingSetParameters.TX_POWER_MEDIUM, AdvertisingSetParameters.TX_POWER_LOW, AdvertisingSetParameters.TX_POWER_ULTRA_LOW));
	
	public static void setAdvertisingSetTxPower(Context context, int advertisingSetTxPower) {
		SharedPreferences sharedPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		if (!availableTxPowers.contains(advertisingSetTxPower)) advertisingSetTxPower = AdvertisingSetParameters.TX_POWER_HIGH;
		
		Editor editor = sharedPrefs.edit();
		editor.putInt(PREFS_ADVERTISINGSET_TX_POWER, advertisingSetTxPower);
		editor.commit();
	}
    
	public static DeviceType getOrInitDeviceType(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        String typeString = sharedPrefs.getString(PREFS_DEVICETYPE, null);
        DeviceType type = null;
        try {
        	if (typeString != null) type = DeviceType.valueOf(typeString);
        } catch (IllegalArgumentException e) {}
        
        if (type == null) {
        	type = DeviceType.SMARTPHONE;
        	typeString = type.name();
            Editor editor = sharedPrefs.edit();
            editor.putString(PREFS_DEVICETYPE, typeString);
            editor.commit();
        }    	
    	return type;
	}
	
    public static boolean isServiceEnabled(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(QuuppaTag.PREFS, Context.MODE_PRIVATE);
        return sharedPrefs.getBoolean(QuuppaTag.PREFS_ENABLED, false);
    }
	
    
	public static void setServiceEnabled(Context context, boolean enabled) {
		SharedPreferences sharedPrefs = context.getSharedPreferences(QuuppaTag.PREFS, Context.MODE_PRIVATE);
		Editor editor = sharedPrefs.edit();
		editor.putBoolean(QuuppaTag.PREFS_ENABLED, enabled);
		editor.commit();
	}
	
	@SuppressWarnings("unchecked")
	public static Class<? extends Activity> getNotifiedActivityClass(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        String className = sharedPrefs.getString(PREFS_NOTIFIED_ACTIVITY_CLASSNAME, null);
        if (className == null) return null;
		try {
			return (Class<? extends Activity>)Class.forName(className);
		} catch (ClassNotFoundException e) {
		}
		return null;
	}	
        
	public static void setNotifiedActivityClass(Context context, Class<? extends Activity> activityClass) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                PREFS, Context.MODE_PRIVATE);
        Editor editor = sharedPrefs.edit();
        editor.putString(PREFS_NOTIFIED_ACTIVITY_CLASSNAME, activityClass.getCanonicalName());
        editor.commit();
	}
    
    /**
     * Stops Advertising for given callback instance.
     * @param context {@link Context} to look up Bluetooth service
     * @param callback {@link AdvertiseCallback} instance to be stopped.
     */
    @Deprecated
    public static void stopAdvertising(Context context, AdvertiseCallback callback) {
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        // If not enabled (potentially anymore), return silently
        if (!btAdapter.isEnabled()) return;
        btAdapter.getBluetoothLeAdvertiser().stopAdvertising(callback);
    }

    @Deprecated
    public static void startAdvertising(Context context, AdvertiseCallback callback) throws QuuppaTagException {
    	startAdvertising(context, callback, true, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
    }

    @Deprecated
    public static void startAdvertising(Context context, AdvertiseCallback callback, boolean moving) throws QuuppaTagException {
    	startAdvertising(context, callback, moving, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
    }
    
    /**
     * Starts Advertising using given callback instance.
     * @param mode One of the values in AdvertiseSettings.
     * @param txPower One of the values in AdvertiseSettings.
     * @param callback Callback that gives ID to the advertising instance.
     * @throws QuuppaTagException if Bluetooth service is not enabled or there are errors communicating with it
     */
    @Deprecated
	public static void startAdvertising(Context context, AdvertiseCallback callback, boolean moving, int mode,
			int txPower) throws QuuppaTagException {
		String tagID = getOrInitTagId(context);
		
		AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder().setAdvertiseMode(mode)
				.setTxPowerLevel(txPower).setConnectable(true).build();
		
		byte[] bytes = createQuuppaDFPacketAdvertiseData(tagID, getOrInitDeviceType(context), moving);

		AdvertiseData advertisementData = new AdvertiseData.Builder().setIncludeTxPowerLevel(false)
				.addManufacturerData(0x00C7, bytes).build();

		BluetoothLeAdvertiser bluetoothLeAdvertiser = getBluetoothLeAdvertiser(context);
		bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertisementData, callback);
	}
	
	protected static BluetoothLeAdvertiser getBluetoothLeAdvertiser(Context context) throws QuuppaTagException {
		BluetoothManager btManager = (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
		BluetoothAdapter btAdapter = btManager.getAdapter();
		if (!btAdapter.isEnabled())
			throw new QuuppaTagException("QuuppaTag is not controllable because Bluetooth is not enabled");
		return btAdapter.getBluetoothLeAdvertiser();
	}
	
	public static void restart(Context context) {
        setServiceEnabled(context, true);
        Intent intent = new Intent(context, QuuppaTagService.class);
        intent.setAction(IntentAction.QT_RESTART.fullyQualifiedName());
        context.startForegroundService(intent);
	}
	
	public static void start(Context context) {
        setServiceEnabled(context, true);
        context.startForegroundService(new Intent(context, QuuppaTagService.class));
	}
	
	public static void stop(Context context) {
        setServiceEnabled(context, false);
        context.stopService(new Intent(context, QuuppaTagService.class));
	}
	
}

