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

import java.util.Arrays;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
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

    /** Constructs a byte array using the Direction Finding Packet Specification.
     * Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for more details.
     * @param tagID Tag ID to be injected into the packet
     * @param mode One of the values of AdvertiseSettings.ADVERTISE_MODE_*. The value is injected in the DF Packet as indication of transmit rate.
     * @param txPower One of the values of AdvertiseSettings.ADVERTISE_TX_*. The value is injected in the DF Packet as indication of transmit power.
     * @return constructed byte array
     * @throws QuuppaTagException 
     */
    private static byte[] createBytesWithQuuppaDFPacket(String tagID, int mode, int txPower) throws QuuppaTagException {
        // Please see the 'Specification of Quuppa Tag Emulation using Bluetooth Wireless Technology' -document for details

        byte[] bytes = new byte[]{
                (byte) 0x01, // Quuppa Packet ID
                (byte) 0x21, // Device Type (0x21 = android smartphone, 0x22 = android tablet)
                (byte) 0x1D, // Payload header (0x1D = moving at walking speed, 0x1C = stationary)
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
    
    private static final String PREF_TAG_ID = "TAG_ID";
    
    public synchronized static String getGeneratedTagId(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(
                "QUUPPA", Context.MODE_PRIVATE);
        String tagId = sharedPrefs.getString(PREF_TAG_ID, null);
        if (tagId == null) {
        	tagId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        	if (tagId == null || tagId.length() < 12) tagId = UUID.randomUUID().toString();
            Editor editor = sharedPrefs.edit();
            tagId = tagId.substring(tagId.length() - 12);
            editor.putString(PREF_TAG_ID, tagId);
            editor.commit();
        }    	
    	return tagId;
    }

    /**
     * Stops Advertising for given callback instance.
     * @param context {@link Context} to look up Bluetooth service
     * @param callback {@link AdvertiseCallback} instance to be stopped.
     */
    public static void stopAdvertising(Context context, AdvertiseCallback callback) {
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        // If not enabled (potentially anymore), return silenty
        if (!btAdapter.isEnabled()) return;
        btAdapter.getBluetoothLeAdvertiser().stopAdvertising(callback);
//        callback.onStopSuccess();
    }

    public static void startAdvertising(Context context, AdvertiseCallback callback) throws QuuppaTagException {
    	startAdvertising(context, callback, getGeneratedTagId(context), AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
    }
    
    public static void startAdvertising(Context context, AdvertiseCallback callback, int mode, int txPower) throws QuuppaTagException {
    	startAdvertising(context, callback, getGeneratedTagId(context), mode, txPower);
    }
    
    public static void startAdvertising(Context context, AdvertiseCallback callback, String tagID) throws QuuppaTagException {
    	startAdvertising(context, callback, tagID, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
    }
    
    /**
     * Starts Advertising using given callback instance.
     * @param mode One of the values in AdvertiseSettings.
     * @param txPower One of the values in AdvertiseSettings.
     * @param callback Callback that gives ID to the advertising instance.
     * @throws QuuppaTagException if Bluetooth service is not enabled or there are errors communicating with it
     */
	public static void startAdvertising(Context context, AdvertiseCallback callback, String tagID, int mode,
			int txPower) throws QuuppaTagException {
		AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder().setAdvertiseMode(mode)
				.setTxPowerLevel(txPower).setConnectable(true).build();

		byte[] bytes = createBytesWithQuuppaDFPacket(tagID, mode, txPower);

		AdvertiseData advertisementData = new AdvertiseData.Builder().setIncludeTxPowerLevel(false)
				.addManufacturerData(0x00C7, bytes).build();

		BluetoothLeAdvertiser bluetoothLeAdvertiser = getBluetoothLeAdvertiser(context);
		bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertisementData, callback);
	}
    
	private static BluetoothLeAdvertiser getBluetoothLeAdvertiser(Context context) throws QuuppaTagException {
		BluetoothManager btManager = (BluetoothManager) context.getSystemService(Activity.BLUETOOTH_SERVICE);
		BluetoothAdapter btAdapter = btManager.getAdapter();
		if (!btAdapter.isEnabled())
			throw new QuuppaTagException("QuuppaTag is not controllable because Bluetooth is not enabled");
		return btAdapter.getBluetoothLeAdvertiser();
	}
}

