# android-quuppa-taglib

A simple Android tag emulation library to be included into Android application projects. You can make any Android device trackable by including this library into your Android application.

## Getting Started

See the [android-quuppa-tag demo](https://github.com/quuppalabs/demo-android-quuppa-tag) for an example of how to use this library. Also check out javadoc for the library on javadoc.io.

[![javadoc](https://javadoc.io/badge2/com.quuppa/android-quuppa-taglib/javadoc.svg)](https://javadoc.io/doc/com.quuppa/android-quuppa-taglib) 

## Using the library

The library is ready to be included in your Android project as a compiled dependency and available as a JAR dependency through Maven central.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quuppa/android-quuppa-taglib)

Both *android.permission.BLUETOOTH* and *android.permission.BLUETOOTH_ADMIN* permissions are required to use this library.

The library itself is stateless (hence the main operations in [QuuppaTag](https://github.com/quuppalabs/android-quuppa-taglib/blob/main/src/main/java/com/quuppa/tag/QuuppaTag.java) are static). Android requires you to use the same [AdvertiseCallback](https://developer.android.com/reference/android/bluetooth/le/AdvertiseCallback) to stop the advertisement that you started it with so you want to keep all the state in it. Lifecycle management of the advertisement is left up to the developer. For a long running background task, you may want to build a service for handling the mechanics of starting and stopping advertisement. The default startAdvertising method generates a tag ID (based on [Secure.ANDROID_ID](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID) if available) that is guaranteed to stay constant for the lifetime of the application but you can also supply your own generated ID if you so prefer (see source code for more information).  

## License

Licensed under Apache 2.0 License.
