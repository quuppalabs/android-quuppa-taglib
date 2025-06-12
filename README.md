# android-quuppa-taglib

An Android Quuppa Tag emulation library to be included into Android application projects. You can make any Android device with BLE and API level 26 (Android version 8.0+) trackable in [Quuppa Intelligent Locating System](https://www.quuppa.com/) by including this library in your Android application.

## Getting Started

See the [android-quuppa-tag demo](https://github.com/quuppalabs/demo-android-quuppa-tag) for an example of how to use this library. Review our full [Android Tag Emulation Specification v2.0](https://github.com/quuppalabs/android-quuppa-taglib/blob/main/Quuppa-Tag-Emulation-for-Android-Devices-v2.0.pdf) to understand the structure of Quuppa specific advertisement data.

Also check out javadoc for the library on javadoc.io.

[![javadoc](https://javadoc.io/badge2/com.quuppa/android-quuppa-taglib/javadoc.svg)](https://javadoc.io/doc/com.quuppa/android-quuppa-taglib)

## Quick Start

 1. Include the latest android-quuppa-taglib in your Android application's build.gradle file by adding:
```
 dependencies {
    implementation 'com.quuppa:android-quuppa-taglib:2.0.14'
}
```
 2. copy all permissions as well as _< service .. >_ and _< receiver >_ configurations from our demo Android Quuppa Tag's [AndroidManifest.xml](https://github.com/quuppalabs/demo-android-quuppa-tag/blob/main/src/main/AndroidManifest.xml) into your own application's AndroidManifest.xml
 3. Make sure you your application asks for necessary runtime permissions (review/copy from the demo app's [QuuppaTagEmulationDemoActivity.java](https://github.com/quuppalabs/demo-android-quuppa-tag/blob/main/src/main/java/com/quuppa/quuppatag/QuuppaTagEmulationDemoActivity.java)). Then call `QuuppaTag.start(this);` from your activity to start the service.

## Using the library

The library is ready to be included in your Android project as a compiled dependency and available as a JAR dependency through Maven central.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib)

The library requires several permissions:

```
    <uses-permission android:name="android.permission.BLUETOOTH"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
```
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a UI permission (again see android-quuppa-tag demo app) but in order to make sure the OS doesn't kill the service when the app is not on the foreground, you should disable battery optimizations. You also need to declare the service and receiver as described in the demo app's [AndroidManifest.xml](https://github.com/quuppalabs/demo-android-quuppa-tag/blob/main/src/main/AndroidManifest.xml).

The 1.x version of the library provided simple operations in a stateless utility class and required user to implement a service around it to make it run for longer periods. The current 2.x version implements a full service that also uses the accelerometer to determine whether the device is moving or not.

The service is very simple to use. There are static convenience operations in [QuuppaTag](https://github.com/quuppalabs/android-quuppa-taglib/blob/main/src/main/java/com/quuppa/tag/QuuppaTag.java) for starting and stopping the service and modifying preferences (e.g. setting the tag ID, accelerometer sensitivity or [AdvertisingSet TX power](https://developer.android.com/reference/android/bluetooth/le/AdvertisingSetParameters)). However, for typical usage, you shouldn't need to change anything. The default tag ID (based on [Secure.ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID) if available) is guaranteed to stay constant for the lifetime of the application but you can also supply your own ID if you so prefer. 

You can also start the service directly with:
```
Intent tagServiceIntent = new Intent(this, QuuppaTagService.class);
startForegroundService(tagServiceIntent);
```
In that case though, you should first call `QuuppaTag.setServiceEnabled(context, true);` to set the service in enabled state. This is so that service can automatically start on system events but only acquires the wake lock and starts emitting BLE advertisement packets when in enabled state.

## Non-RTLS use

This library is really not so useful outside Real-Time Locationing System (RTLS) scope. Even so, it may give you ideas for creating your own never ending service that can ben run on modern Android devices (because Google has been tightening rules around long running service all the time). In short, it's a combination of implementing a frontend service (which this library does), holding a wake lock and disabling battery optimizations - look for examples in the demo app for permission requests. Feel free to explore the source code.

## License

Licensed under Apache 2.0 License. 

Additional Terms:  
Neither the name "Quuppa" nor the names of the contributors to the android-quuppa-taglib repository may be used to endorse or promote products derived from the android-quuppa-taglib repository without the prior written permission of Quuppa Oy.

