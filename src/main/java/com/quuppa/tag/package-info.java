/**
 * Simple Quuppa Tag for emulating Quuppa v1 tag protocol on Android
 * 
 * Meant to be consumed as a library in Android applications. Handles byte-level creation of Bluetooth advertisement messages
 * that are identifiable and trackable by the Quuppa Positioning System.
 * Implements a service that is started on the foreground. Uses accelerometer and exact alarms. The consumer must 
 * provide proper permissions for the service to run correctly.
 */
package com.quuppa.tag;

