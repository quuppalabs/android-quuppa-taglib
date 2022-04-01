/**
 * Simple Quuppa Tag for emulating Quuppa v1 tag protocol on Android
 * 
 * Meant to be consumed as a library in Android applications. Handles byte-level creation of Bluetooth advertisement messages
 * that are identifiable and trackable by the Quuppa Positioning System.
 * The consumer must consider the lifetime of the advertisement. A long running Android application should hold a reference
 * to a AdvertiseCallback instance in a service. Foreground demos and other short running apps should handle starting and stopping
 * of BLE advertisement as part of the Activity's lifecyce.
 */
package com.quuppa.tag;

