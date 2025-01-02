# android-quuppa-taglib

An Android Quuppa Tag emulation library to be included into Android application projects. You can make any Android device with BLE and API level 26 (Android version 8.0+) trackable in [Quuppa Intelligent Locating System](https://www.quuppa.com/) by including this library in your Android application.

## Getting Started

See the [android-quuppa-tag demo](https://github.com/quuppalabs/demo-android-quuppa-tag) for an example of how to use this library. Also check out javadoc for the library on javadoc.io.

[![javadoc](https://javadoc.io/badge2/com.quuppa/android-quuppa-taglib/javadoc.svg)](https://javadoc.io/doc/com.quuppa/android-quuppa-taglib) 

## Using the library

The library is ready to be included in your Android project as a compiled dependency and available as a JAR dependency through Maven central.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib)

The library requires several permissions:

```
    <uses-permission android:name="android.permission.BLUETOOTH"/>
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
```
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a UI permission (again see android-quuppa-tag demo app) but in order to make sure the OS doesn't kill the service when the app is not on the foreground, you should disable battery optimizations. You also need to declare the service and receiver as described in the demo app's [AndroidManifest.xml](https://github.com/quuppalabs/demo-android-quuppa-tag/blob/main/src/main/AndroidManifest.xml).

The 1.x version of the library provided simple operations in a stateless utility class and required user to implement a service around it to make it run for longer periods. The current 2.x version implements a full service that also uses the accelerometer to determine whether the device is moving or not.

The service is very simple to use. However, it's not enough just to:

```
Intent tagServiceIntent = new Intent(this, QuuppaTagService.class);
startForegroundService(tagServiceIntent);
```

You should first call `QuuppaTag.setServiceEnabled(context, true);` to set the service in enabled state. This is so that service can automatically start on system events but only acquires the wake lock and starts emitting BLE advertisement packets when in enable state. The class [QuuppaTag](https://github.com/quuppalabs/android-quuppa-taglib/blob/main/src/main/java/com/quuppa/tag/QuuppaTag.java) also contains getters and setters for other parameters, such as a the tag ID and [AdvertisingSet TX power](https://developer.android.com/reference/android/bluetooth/le/AdvertisingSetParameters). The default tag ID (based on [Secure.ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID) if available) is guaranteed to stay constant for the lifetime of the application but you can also supply your own ID if you so prefer.

## License

Licensed under Apache 2.0 License.
