// This source is from android-all-8.0.0_r4-roboelectric-r1.jar
// with added NetworkCallback constructors from API level 31 sources just so that library can support
// invoking an anonymous ConnectivityManager.NetworkCallback(int flags) to add 
// ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO. super() needs to be the first call in the constructor
// and anonymous classes cannot be created via reflection. kaosko 2025-03-12

/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.net;

import static com.android.internal.util.Preconditions.checkNotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.util.HashMap;

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.Protocol;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkActivityListener;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

/**
 * Class that answers queries about the state of network connectivity. It also
 * notifies applications when network connectivity changes.
 * <p>
 * The primary responsibilities of this class are to:
 * <ol>
 * <li>Monitor network connections (Wi-Fi, GPRS, UMTS, etc.)</li>
 * <li>Send broadcast intents when network connectivity changes</li>
 * <li>Attempt to "fail over" to another network when connectivity to a network
 * is lost</li>
 * <li>Provide an API that allows applications to query the coarse-grained or fine-grained
 * state of the available networks</li>
 * <li>Provide an API that allows applications to request and select networks for their data
 * traffic</li>
 * </ol>
 */
@SystemService(Context.CONNECTIVITY_SERVICE)
public class ConnectivityManager {
    private static final String TAG = "ConnectivityManager";

    /**
     * A change in network connectivity has occurred. A default connection has either
     * been established or lost. The NetworkInfo for the affected network is
     * sent as an extra; it should be consulted to see what kind of
     * connectivity event occurred.
     * 
     * Apps targeting Android 7.0 (API level 24) and higher do not receive this
     * broadcast if they declare the broadcast receiver in their manifest. Apps
     * will still receive broadcasts if they register their
     * {@link android.content.BroadcastReceiver} with
     * {@link android.content.Context#registerReceiver Context.registerReceiver()}
     * and that context is still valid.
     * 
     * If this is a connection that was the result of failing over from a
     * disconnected network, then the FAILOVER_CONNECTION boolean extra is
     * set to true.
     * 
     * For a loss of connectivity, if the connectivity manager is attempting
     * to connect (or has already connected) to another network, the
     * NetworkInfo for the new network is also passed as an extra. This lets
     * any receivers of the broadcast know that they should not necessarily
     * tell the user that no data traffic will be possible. Instead, the
     * receiver should expect another broadcast soon, indicating either that
     * the failover attempt succeeded (and so there is still overall data
     * connectivity), or that the failover attempt failed, meaning that all
     * connectivity has been lost.
     * 
     * For a disconnect event, the boolean extra EXTRA_NO_CONNECTIVITY
     * is set to {@code true} if there are no connected networks at all.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    /**
     * A temporary hack until SUPL system can get off the legacy APIS.
     * They do too many network requests and the long list of apps listening
     * and waking due to the CONNECTIVITY_ACTION bcast makes it expensive.
     * Use this bcast intent instead for SUPL requests.
     * 
     */
    public static final String CONNECTIVITY_ACTION_SUPL =
            "android.net.conn.CONNECTIVITY_CHANGE_SUPL";

    /**
     * The device has connected to a network that has presented a captive
     * portal, which is blocking Internet connectivity. The user was presented
     * with a notification that network sign in is required,
     * and the user invoked the notification's action indicating they
     * desire to sign in to the network. Apps handling this activity should
     * facilitate signing in to the network. This action includes a
     * {@link Network} typed extra called {@link #EXTRA_NETWORK} that represents
     * the network presenting the captive portal; all communication with the
     * captive portal must be done using this {@code Network} object.
     * 
     * This activity includes a {@link CaptivePortal} extra named
     * {@link #EXTRA_CAPTIVE_PORTAL} that can be used to indicate different
     * outcomes of the captive portal sign in to the system:
     * <ul>
     * <li> When the app handling this action believes the user has signed in to
     * the network and the captive portal has been dismissed, the app should
     * call {@link CaptivePortal#reportCaptivePortalDismissed} so the system can
     * reevaluate the network. If reevaluation finds the network no longer
     * subject to a captive portal, the network may become the default active
     * data network. </li>
     * <li> When the app handling this action believes the user explicitly wants
     * to ignore the captive portal and the network, the app should call
     * {@link CaptivePortal#ignoreNetwork}. </li>
     * </ul>
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_CAPTIVE_PORTAL_SIGN_IN = "android.net.conn.CAPTIVE_PORTAL";

    /**
     * The lookup key for a {@link NetworkInfo} object. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     *
     * @deprecated Since {@link NetworkInfo} can vary based on UID, applications
     *             should always obtain network information through
     *             {@link #getActiveNetworkInfo()}.
     */
    @Deprecated
    public static final String EXTRA_NETWORK_INFO = "networkInfo";

    /**
     * Network type which triggered a {@link #CONNECTIVITY_ACTION} broadcast.
     *
     */
    public static final String EXTRA_NETWORK_TYPE = "networkType";

    /**
     * The lookup key for a boolean that indicates whether a connect event
     * is for a network to which the connectivity manager was failing over
     * following a disconnect on another network.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     */
    public static final String EXTRA_IS_FAILOVER = "isFailover";
    /**
     * The lookup key for a {@link NetworkInfo} object. This is supplied when
     * there is another network that it may be possible to connect to. Retrieve with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_OTHER_NETWORK_INFO = "otherNetwork";
    /**
     * The lookup key for a boolean that indicates whether there is a
     * complete lack of connectivity, i.e., no network is available.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     */
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";
    /**
     * The lookup key for a string that indicates why an attempt to connect
     * to a network failed. The string has no particular structure. It is
     * intended to be used in notifications presented to users. Retrieve
     * it with {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_REASON = "reason";
    /**
     * The lookup key for a string that provides optionally supplied
     * extra information about the network state. The information
     * may be passed up from the lower networking layers, and its
     * meaning may be specific to a particular network type. Retrieve
     * it with {@link android.content.Intent#getStringExtra(String)}.
     */
    public static final String EXTRA_EXTRA_INFO = "extraInfo";
    /**
     * The lookup key for an int that provides information about
     * our connection to the internet at large.  0 indicates no connection,
     * 100 indicates a great connection.  Retrieve it with
     * {@link android.content.Intent#getIntExtra(String, int)}.
     * 
     */
    public static final String EXTRA_INET_CONDITION = "inetCondition";
    /**
     * The lookup key for a {@link CaptivePortal} object included with the
     * {@link #ACTION_CAPTIVE_PORTAL_SIGN_IN} intent.  The {@code CaptivePortal}
     * object can be used to either indicate to the system that the captive
     * portal has been dismissed or that the user does not want to pursue
     * signing in to captive portal.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_CAPTIVE_PORTAL = "android.net.extra.CAPTIVE_PORTAL";

    /**
     * Key for passing a URL to the captive portal login activity.
     */
    public static final String EXTRA_CAPTIVE_PORTAL_URL = "android.net.extra.CAPTIVE_PORTAL_URL";

    /**
     * Key for passing a user agent string to the captive portal login activity.
     * 
     */
    public static final String EXTRA_CAPTIVE_PORTAL_USER_AGENT =
            "android.net.extra.CAPTIVE_PORTAL_USER_AGENT";

    /**
     * Broadcast action to indicate the change of data activity status
     * (idle or active) on a network in a recent period.
     * The network becomes active when data transmission is started, or
     * idle if there is no data transmission for a period of time.
     * 
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_DATA_ACTIVITY_CHANGE = "android.net.conn.DATA_ACTIVITY_CHANGE";
    /**
     * The lookup key for an enum that indicates the network device type on which this data activity
     * change happens.
     * 
     */
    public static final String EXTRA_DEVICE_TYPE = "deviceType";
    /**
     * The lookup key for a boolean that indicates the device is active or not. {@code true} means
     * it is actively sending or receiving data and {@code false} means it is idle.
     * 
     */
    public static final String EXTRA_IS_ACTIVE = "isActive";
    /**
     * The lookup key for a long that contains the timestamp (nanos) of the radio state change.
     * 
     */
    public static final String EXTRA_REALTIME_NS = "tsNanos";

    /**
     * Broadcast Action: The setting for background data usage has changed
     * values. Use {@link #getBackgroundDataSetting()} to get the current value.
     * <p>
     * If an application uses the network in the background, it should listen
     * for this broadcast and stop using the background data if the value is
     * {@code false}.
     * <p>
     *
     * @deprecated As of {@link VERSION_CODES#ICE_CREAM_SANDWICH}, availability
     *             of background data depends on several combined factors, and
     *             this broadcast is no longer sent. Instead, when background
     *             data is unavailable, {@link #getActiveNetworkInfo()} will now
     *             appear disconnected. During first boot after a platform
     *             upgrade, this broadcast will be sent once if
     *             {@link #getBackgroundDataSetting()} was {@code false} before
     *             the upgrade.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    @Deprecated
    public static final String ACTION_BACKGROUND_DATA_SETTING_CHANGED =
            "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED";

    /**
     * Broadcast Action: The network connection may not be good
     * uses {@code ConnectivityManager.EXTRA_INET_CONDITION} and
     * {@code ConnectivityManager.EXTRA_NETWORK_INFO} to specify
     * the network and it's condition.
     * 
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String INET_CONDITION_ACTION =
            "android.net.conn.INET_CONDITION_ACTION";

    /**
     * Broadcast Action: A tetherable connection has come or gone.
     * Uses {@code ConnectivityManager.EXTRA_AVAILABLE_TETHER},
     * {@code ConnectivityManager.EXTRA_ACTIVE_LOCAL_ONLY},
     * {@code ConnectivityManager.EXTRA_ACTIVE_TETHER}, and
     * {@code ConnectivityManager.EXTRA_ERRORED_TETHER} to indicate
     * the current state of tethering.  Each include a list of
     * interface names in that state (may be empty).
     * 
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_TETHER_STATE_CHANGED =
            "android.net.conn.TETHER_STATE_CHANGED";

    /**
     * 
     * gives a String[] listing all the interfaces configured for
     * tethering and currently available for tethering.
     */
    public static final String EXTRA_AVAILABLE_TETHER = "availableArray";

    /**
     * 
     * gives a String[] listing all the interfaces currently in local-only
     * mode (ie, has DHCPv4+IPv6-ULA support and no packet forwarding)
     */
    public static final String EXTRA_ACTIVE_LOCAL_ONLY = "localOnlyArray";

    /**
     * 
     * gives a String[] listing all the interfaces currently tethered
     * (ie, has DHCPv4 support and packets potentially forwarded/NATed)
     */
    public static final String EXTRA_ACTIVE_TETHER = "tetherArray";

    /**
     * 
     * gives a String[] listing all the interfaces we tried to tether and
     * failed.  Use {@link #getLastTetherError} to find the error code
     * for any interfaces listed here.
     */
    public static final String EXTRA_ERRORED_TETHER = "erroredArray";

    /**
     * Broadcast Action: The captive portal tracker has finished its test.
     * Sent only while running Setup Wizard, in lieu of showing a user
     * notification.
     * 
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CAPTIVE_PORTAL_TEST_COMPLETED =
            "android.net.conn.CAPTIVE_PORTAL_TEST_COMPLETED";
    /**
     * The lookup key for a boolean that indicates whether a captive portal was detected.
     * Retrieve it with {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     * 
     */
    public static final String EXTRA_IS_CAPTIVE_PORTAL = "captivePortal";

    /**
     * Action used to display a dialog that asks the user whether to connect to a network that is
     * not validated. This intent is used to start the dialog in settings via startActivity.
     *
     * 
     */
    public static final String ACTION_PROMPT_UNVALIDATED = "android.net.conn.PROMPT_UNVALIDATED";

    /**
     * Action used to display a dialog that asks the user whether to avoid a network that is no
     * longer validated. This intent is used to start the dialog in settings via startActivity.
     *
     * 
     */
    public static final String ACTION_PROMPT_LOST_VALIDATION =
            "android.net.conn.PROMPT_LOST_VALIDATION";

    /**
     * Invalid tethering type.
     * 
     */
    public static final int TETHERING_INVALID   = -1;

    /**
     * Wifi tethering type.
     * 
     */
    @SystemApi
    public static final int TETHERING_WIFI      = 0;

    /**
     * USB tethering type.
     * 
     */
    @SystemApi
    public static final int TETHERING_USB       = 1;

    /**
     * Bluetooth tethering type.
     * 
     */
    @SystemApi
    public static final int TETHERING_BLUETOOTH = 2;

    /**
     * Extra used for communicating with the TetherService. Includes the type of tethering to
     * enable if any.
     * 
     */
    public static final String EXTRA_ADD_TETHER_TYPE = "extraAddTetherType";

    /**
     * Extra used for communicating with the TetherService. Includes the type of tethering for
     * which to cancel provisioning.
     * 
     */
    public static final String EXTRA_REM_TETHER_TYPE = "extraRemTetherType";

    /**
     * Extra used for communicating with the TetherService. True to schedule a recheck of tether
     * provisioning.
     * 
     */
    public static final String EXTRA_SET_ALARM = "extraSetAlarm";

    /**
     * Tells the TetherService to run a provision check now.
     * 
     */
    public static final String EXTRA_RUN_PROVISION = "extraRunProvision";

    /**
     * Extra used for communicating with the TetherService. Contains the ResultReceiver
     * which will receive provisioning results. Can be left empty.
     * 
     */
    public static final String EXTRA_PROVISION_CALLBACK = "extraProvisionCallback";

    /**
     * The absence of a connection type.
     * 
     */
    public static final int TYPE_NONE        = -1;

    /**
     * The Mobile data connection.  When active, all data traffic
     * will use this network type's interface by default
     * (it has a default route)
     */
    public static final int TYPE_MOBILE      = 0;
    /**
     * The WIFI data connection.  When active, all data traffic
     * will use this network type's interface by default
     * (it has a default route).
     */
    public static final int TYPE_WIFI        = 1;
    /**
     * An MMS-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is used by applications needing to talk to the carrier's
     * Multimedia Messaging Service servers.
     *
     * @deprecated Applications should instead use
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         provides the {@link NetworkCapabilities#NET_CAPABILITY_MMS} capability.
     */
    @Deprecated
    public static final int TYPE_MOBILE_MMS  = 2;
    /**
     * A SUPL-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is used by applications needing to talk to the carrier's
     * Secure User Plane Location servers for help locating the device.
     *
     * @deprecated Applications should instead use
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         provides the {@link NetworkCapabilities#NET_CAPABILITY_SUPL} capability.
     */
    @Deprecated
    public static final int TYPE_MOBILE_SUPL = 3;
    /**
     * A DUN-specific Mobile data connection.  This network type may use the
     * same network interface as {@link #TYPE_MOBILE} or it may use a different
     * one.  This is sometimes by the system when setting up an upstream connection
     * for tethering so that the carrier is aware of DUN traffic.
     */
    public static final int TYPE_MOBILE_DUN  = 4;
    /**
     * A High Priority Mobile data connection.  This network type uses the
     * same network interface as {@link #TYPE_MOBILE} but the routing setup
     * is different.
     *
     * @deprecated Applications should instead use
     *         {@link #requestNetwork(NetworkRequest, NetworkCallback)} to request a network that
     *         uses the {@link NetworkCapabilities#TRANSPORT_CELLULAR} transport.
     */
    @Deprecated
    public static final int TYPE_MOBILE_HIPRI = 5;
    /**
     * The WiMAX data connection.  When active, all data traffic
     * will use this network type's interface by default
     * (it has a default route).
     */
    public static final int TYPE_WIMAX       = 6;

    /**
     * The Bluetooth data connection.  When active, all data traffic
     * will use this network type's interface by default
     * (it has a default route).
     */
    public static final int TYPE_BLUETOOTH   = 7;

    /**
     * Dummy data connection.  This should not be used on shipping devices.
     */
    public static final int TYPE_DUMMY       = 8;

    /**
     * The Ethernet data connection.  When active, all data traffic
     * will use this network type's interface by default
     * (it has a default route).
     */
    public static final int TYPE_ETHERNET    = 9;

    /**
     * Over the air Administration.
     * 
     */
    public static final int TYPE_MOBILE_FOTA = 10;

    /**
     * IP Multimedia Subsystem.
     * 
     */
    public static final int TYPE_MOBILE_IMS  = 11;

    /**
     * Carrier Branded Services.
     * 
     */
    public static final int TYPE_MOBILE_CBS  = 12;

    /**
     * A Wi-Fi p2p connection. Only requesting processes will have access to
     * the peers connected.
     * 
     */
    public static final int TYPE_WIFI_P2P    = 13;

    /**
     * The network to use for initially attaching to the network
     * 
     */
    public static final int TYPE_MOBILE_IA = 14;

    /**
     * Emergency PDN connection for emergency services.  This
     * may include IMS and MMS in emergency situations.
     * 
     */
    public static final int TYPE_MOBILE_EMERGENCY = 15;

    /**
     * The network that uses proxy to achieve connectivity.
     * 
     */
    public static final int TYPE_PROXY = 16;

    /**
     * A virtual network using one or more native bearers.
     * It may or may not be providing security services.
     */
    public static final int TYPE_VPN = 17;

    /**  */
    public static final int MAX_RADIO_TYPE   = TYPE_VPN;

    /**  */
    public static final int MAX_NETWORK_TYPE = TYPE_VPN;

    /**
     * If you want to set the default network preference,you can directly
     * change the networkAttributes array in framework's config.xml.
     *
     * @deprecated Since we support so many more networks now, the single
     *             network default network preference can't really express
     *             the hierarchy.  Instead, the default is defined by the
     *             networkAttributes in config.xml.  You can determine
     *             the current value by calling {@link #getNetworkPreference()}
     *             from an App.
     */
    @Deprecated
    public static final int DEFAULT_NETWORK_PREFERENCE = TYPE_WIFI;

    /**
     * 
     */
    public final static int REQUEST_ID_UNSET = 0;

    /**
     * A NetID indicating no Network is selected.
     * Keep in sync with bionic/libc/dns/include/resolv_netid.h
     * 
     */
    public static final int NETID_UNSET = 0;

    private final IConnectivityManager mService;
    /**
     * A kludge to facilitate static access where a Context pointer isn't available, like in the
     * case of the static set/getProcessDefaultNetwork methods and from the Network class.
     * TODO: Remove this after deprecating the static methods in favor of non-static methods or
     * methods that take a Context argument.
     */
    private static ConnectivityManager sInstance;

    private final Context mContext;

    private INetworkManagementService mNMService;
    private INetworkPolicyManager mNPManager;

    /**
     * Tests if a given integer represents a valid network type.
     * @param networkType the type to be tested
     * @return a boolean.  {@code true} if the type is valid, else {@code false}
     * @deprecated All APIs accepting a network type are deprecated. There should be no need to
     *             validate a network type.
     */
    @Deprecated
    public static boolean isNetworkTypeValid(int networkType) {
        return networkType >= 0 && networkType <= MAX_NETWORK_TYPE;
    }

    /**
     * Returns a non-localized string representing a given network type.
     * ONLY used for debugging output.
     * @param type the type needing naming
     * @return a String for the given type, or a string version of the type ("87")
     * if no name is known.
     * 
     */
    public static String getNetworkTypeName(int type) {
        switch (type) {
            case TYPE_MOBILE:
                return "MOBILE";
            case TYPE_WIFI:
                return "WIFI";
            case TYPE_MOBILE_MMS:
                return "MOBILE_MMS";
            case TYPE_MOBILE_SUPL:
                return "MOBILE_SUPL";
            case TYPE_MOBILE_DUN:
                return "MOBILE_DUN";
            case TYPE_MOBILE_HIPRI:
                return "MOBILE_HIPRI";
            case TYPE_WIMAX:
                return "WIMAX";
            case TYPE_BLUETOOTH:
                return "BLUETOOTH";
            case TYPE_DUMMY:
                return "DUMMY";
            case TYPE_ETHERNET:
                return "ETHERNET";
            case TYPE_MOBILE_FOTA:
                return "MOBILE_FOTA";
            case TYPE_MOBILE_IMS:
                return "MOBILE_IMS";
            case TYPE_MOBILE_CBS:
                return "MOBILE_CBS";
            case TYPE_WIFI_P2P:
                return "WIFI_P2P";
            case TYPE_MOBILE_IA:
                return "MOBILE_IA";
            case TYPE_MOBILE_EMERGENCY:
                return "MOBILE_EMERGENCY";
            case TYPE_PROXY:
                return "PROXY";
            case TYPE_VPN:
                return "VPN";
            default:
                return Integer.toString(type);
        }
    }

    /**
     * Checks if a given type uses the cellular data connection.
     * This should be replaced in the future by a network property.
     * @param networkType the type to check
     * @return a boolean - {@code true} if uses cellular network, else {@code false}
     * 
     */
    public static boolean isNetworkTypeMobile(int networkType) {
        switch (networkType) {
            case TYPE_MOBILE:
            case TYPE_MOBILE_MMS:
            case TYPE_MOBILE_SUPL:
            case TYPE_MOBILE_DUN:
            case TYPE_MOBILE_HIPRI:
            case TYPE_MOBILE_FOTA:
            case TYPE_MOBILE_IMS:
            case TYPE_MOBILE_CBS:
            case TYPE_MOBILE_IA:
            case TYPE_MOBILE_EMERGENCY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if the given network type is backed by a Wi-Fi radio.
     *
     * 
     */
    public static boolean isNetworkTypeWifi(int networkType) {
        switch (networkType) {
            case TYPE_WIFI:
            case TYPE_WIFI_P2P:
                return true;
            default:
                return false;
        }
    }

    /**
     * Specifies the preferred network type.  When the device has more
     * than one type available the preferred network type will be used.
     *
     * @param preference the network type to prefer over all others.  It is
     *         unspecified what happens to the old preferred network in the
     *         overall ordering.
     * @deprecated Functionality has been removed as it no longer makes sense,
     *             with many more than two networks - we'd need an array to express
     *             preference.  Instead we use dynamic network properties of
     *             the networks to describe their precedence.
     */
    @Deprecated
    public void setNetworkPreference(int preference) {
    }

    /**
     * Retrieves the current preferred network type.
     *
     * @return an integer representing the preferred network type
     *
     * @deprecated Functionality has been removed as it no longer makes sense,
     *             with many more than two networks - we'd need an array to express
     *             preference.  Instead we use dynamic network properties of
     *             the networks to describe their precedence.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public int getNetworkPreference() {
        return TYPE_NONE;
    }

    /**
     * Returns details about the currently active default data network. When
     * connected, this network is the default route for outgoing connections.
     * You should always check {@link NetworkInfo#isConnected()} before initiating
     * network traffic. This may return {@code null} when there is no default
     * network.
     *
     * @return a {@link NetworkInfo} object for the current default network
     *        or {@code null} if no default network is currently active
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getActiveNetworkInfo() {
        try {
            return mService.getActiveNetworkInfo();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a {@link Network} object corresponding to the currently active
     * default data network.  In the event that the current active default data
     * network disconnects, the returned {@code Network} object will no longer
     * be usable.  This will return {@code null} when there is no default
     * network.
     *
     * @return a {@link Network} object for the current default network or
     *        {@code null} if no default network is currently active
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network getActiveNetwork() {
        try {
            return mService.getActiveNetwork();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns a {@link Network} object corresponding to the currently active
     * default data network for a specific UID.  In the event that the default data
     * network disconnects, the returned {@code Network} object will no longer
     * be usable.  This will return {@code null} when there is no default
     * network for the UID.
     *
     * @return a {@link Network} object for the current default network for the
     *         given UID or {@code null} if no default network is currently active
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public Network getActiveNetworkForUid(int uid) {
        return getActiveNetworkForUid(uid, false);
    }

    /**  */
    public Network getActiveNetworkForUid(int uid, boolean ignoreBlocked) {
        try {
            return mService.getActiveNetworkForUid(uid, ignoreBlocked);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Configures an always-on VPN connection through a specific application.
     * This connection is automatically granted and persisted after a reboot.
     *
     * <p>The designated package should declare a {@link VpnService} in its
     *    manifest guarded by android.Manifest.permission.BIND_VPN_SERVICE,
     *    otherwise the call will fail.
     *
     * @param userId The identifier of the user to set an always-on VPN for.
     * @param vpnPackage The package name for an installed VPN app on the device, or {@code null}
     *                   to remove an existing always-on VPN configuration.
     * @param lockdownEnabled {@code true} to disallow networking when the VPN is not connected or
     *        {@code false} otherwise.
     * @return {@code true} if the package is set as always-on VPN controller;
     *         {@code false} otherwise.
     * 
     */
    public boolean setAlwaysOnVpnPackageForUser(int userId, @Nullable String vpnPackage,
            boolean lockdownEnabled) {
        try {
            return mService.setAlwaysOnVpnPackage(userId, vpnPackage, lockdownEnabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the package name of the currently set always-on VPN application.
     * If there is no always-on VPN set, or the VPN is provided by the system instead
     * of by an app, {@code null} will be returned.
     *
     * @return Package name of VPN controller responsible for always-on VPN,
     *         or {@code null} if none is set.
     * 
     */
    public String getAlwaysOnVpnPackageForUser(int userId) {
        try {
            return mService.getAlwaysOnVpnPackage(userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns details about the currently active default data network
     * for a given uid.  This is for internal use only to avoid spying
     * other apps.
     *
     * @return a {@link NetworkInfo} object for the current default network
     *        for the given uid or {@code null} if no default network is
     *        available for the specified uid.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public NetworkInfo getActiveNetworkInfoForUid(int uid) {
        return getActiveNetworkInfoForUid(uid, false);
    }

    /**  */
    public NetworkInfo getActiveNetworkInfoForUid(int uid, boolean ignoreBlocked) {
        try {
            return mService.getActiveNetworkInfoForUid(uid, ignoreBlocked);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns connection status information about a particular
     * network type.
     *
     * @param networkType integer specifying which networkType in
     *        which you're interested.
     * @return a {@link NetworkInfo} object for the requested
     *        network type or {@code null} if the type is not
     *        supported by the device.
     *
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getNetworkInfo(int networkType) {
        try {
            return mService.getNetworkInfo(networkType);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns connection status information about a particular
     * Network.
     *
     * @param network {@link Network} specifying which network
     *        in which you're interested.
     * @return a {@link NetworkInfo} object for the requested
     *        network or {@code null} if the {@code Network}
     *        is not valid.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo getNetworkInfo(Network network) {
        return getNetworkInfoForUid(network, Process.myUid(), false);
    }

    /**  */
    public NetworkInfo getNetworkInfoForUid(Network network, int uid, boolean ignoreBlocked) {
        try {
            return mService.getNetworkInfoForUid(network, uid, ignoreBlocked);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns connection status information about all network
     * types supported by the device.
     *
     * @return an array of {@link NetworkInfo} objects.  Check each
     * {@link NetworkInfo#getType} for which type each applies.
     *
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkInfo[] getAllNetworkInfo() {
        try {
            return mService.getAllNetworkInfo();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the {@link Network} object currently serving a given type, or
     * null if the given type is not connected.
     *
     * 
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks} and
     *             {@link #getNetworkInfo(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network getNetworkForType(int networkType) {
        try {
            return mService.getNetworkForType(networkType);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns an array of all {@link Network} currently tracked by the
     * framework.
     *
     * @return an array of {@link Network} objects.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public Network[] getAllNetworks() {
        try {
            return mService.getAllNetworks();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns an array of {@link android.net.NetworkCapabilities} objects, representing
     * the Networks that applications run by the given user will use by default.
     * 
     */
    public NetworkCapabilities[] getDefaultNetworkCapabilitiesForUser(int userId) {
        try {
            return mService.getDefaultNetworkCapabilitiesForUser(userId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the IP information for the current default network.
     *
     * @return a {@link LinkProperties} object describing the IP info
     *        for the current default network, or {@code null} if there
     *        is no current default network.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getActiveLinkProperties() {
        try {
            return mService.getActiveLinkProperties();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the IP information for a given network type.
     *
     * @param networkType the network type of interest.
     * @return a {@link LinkProperties} object describing the IP info
     *        for the given networkType, or {@code null} if there is
     *        no current default network.
     *
     * 
     * @deprecated This method does not support multiple connected networks
     *             of the same type. Use {@link #getAllNetworks},
     *             {@link #getNetworkInfo(android.net.Network)}, and
     *             {@link #getLinkProperties(android.net.Network)} instead.
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getLinkProperties(int networkType) {
        try {
            return mService.getLinkPropertiesForType(networkType);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the {@link LinkProperties} for the given {@link Network}.  This
     * will return {@code null} if the network is unknown.
     *
     * @param network The {@link Network} object identifying the network in question.
     * @return The {@link LinkProperties} for the network, or {@code null}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public LinkProperties getLinkProperties(Network network) {
        try {
            return mService.getLinkProperties(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the {@link android.net.NetworkCapabilities} for the given {@link Network}.  This
     * will return {@code null} if the network is unknown.
     *
     * @param network The {@link Network} object identifying the network in question.
     * @return The {@link android.net.NetworkCapabilities} for the network, or {@code null}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkCapabilities getNetworkCapabilities(Network network) {
        try {
            return mService.getNetworkCapabilities(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the URL that should be used for resolving whether a captive portal is present.
     * 1. This URL should respond with a 204 response to a GET request to indicate no captive
     *    portal is present.
     * 2. This URL must be HTTP as redirect responses are used to find captive portal
     *    sign-in pages. Captive portals cannot respond to HTTPS requests with redirects.
     *
     * 
     */
    @SystemApi
    public String getCaptivePortalServerUrl() {
        try {
            return mService.getCaptivePortalServerUrl();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Tells the underlying networking system that the caller wants to
     * begin using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType specifies which network the request pertains to
     * @param feature the name of the feature to be used
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     *
     * @deprecated Deprecated in favor of the cleaner
     *             {@link #requestNetwork(NetworkRequest, NetworkCallback)} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * 
     */
    @Deprecated
    public int startUsingNetworkFeature(int networkType, String feature) {
        checkLegacyRoutingApiAccess();
        NetworkCapabilities netCap = networkCapabilitiesForFeature(networkType, feature);
        if (netCap == null) {
            Log.d(TAG, "Can't satisfy startUsingNetworkFeature for " + networkType + ", " +
                    feature);
            return PhoneConstants.APN_REQUEST_FAILED;
        }

        NetworkRequest request = null;
        synchronized (sLegacyRequests) {
            LegacyRequest l = sLegacyRequests.get(netCap);
            if (l != null) {
                Log.d(TAG, "renewing startUsingNetworkFeature request " + l.networkRequest);
                renewRequestLocked(l);
                if (l.currentNetwork != null) {
                    return PhoneConstants.APN_ALREADY_ACTIVE;
                } else {
                    return PhoneConstants.APN_REQUEST_STARTED;
                }
            }

            request = requestNetworkForFeatureLocked(netCap);
        }
        if (request != null) {
            Log.d(TAG, "starting startUsingNetworkFeature for request " + request);
            return PhoneConstants.APN_REQUEST_STARTED;
        } else {
            Log.d(TAG, " request Failed");
            return PhoneConstants.APN_REQUEST_FAILED;
        }
    }

    /**
     * Tells the underlying networking system that the caller is finished
     * using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType specifies which network the request pertains to
     * @param feature the name of the feature that is no longer needed
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     *
     * @deprecated Deprecated in favor of the cleaner
     *             {@link #unregisterNetworkCallback(NetworkCallback)} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * 
     */
    @Deprecated
    public int stopUsingNetworkFeature(int networkType, String feature) {
        checkLegacyRoutingApiAccess();
        NetworkCapabilities netCap = networkCapabilitiesForFeature(networkType, feature);
        if (netCap == null) {
            Log.d(TAG, "Can't satisfy stopUsingNetworkFeature for " + networkType + ", " +
                    feature);
            return -1;
        }

        if (removeRequestForFeature(netCap)) {
            Log.d(TAG, "stopUsingNetworkFeature for " + networkType + ", " + feature);
        }
        return 1;
    }

    private NetworkCapabilities networkCapabilitiesForFeature(int networkType, String feature) {
        if (networkType == TYPE_MOBILE) {
            switch (feature) {
                case "enableCBS":
                    return networkCapabilitiesForType(TYPE_MOBILE_CBS);
                case "enableDUN":
                case "enableDUNAlways":
                    return networkCapabilitiesForType(TYPE_MOBILE_DUN);
                case "enableFOTA":
                    return networkCapabilitiesForType(TYPE_MOBILE_FOTA);
                case "enableHIPRI":
                    return networkCapabilitiesForType(TYPE_MOBILE_HIPRI);
                case "enableIMS":
                    return networkCapabilitiesForType(TYPE_MOBILE_IMS);
                case "enableMMS":
                    return networkCapabilitiesForType(TYPE_MOBILE_MMS);
                case "enableSUPL":
                    return networkCapabilitiesForType(TYPE_MOBILE_SUPL);
                default:
                    return null;
            }
        } else if (networkType == TYPE_WIFI && "p2p".equals(feature)) {
            return networkCapabilitiesForType(TYPE_WIFI_P2P);
        }
        return null;
    }

    /**
     * Guess what the network request was trying to say so that the resulting
     * network is accessible via the legacy (deprecated) API such as
     * requestRouteToHost.
     *
     * This means we should try to be fairly precise about transport and
     * capability but ignore things such as networkSpecifier.
     * If the request has more than one transport or capability it doesn't
     * match the old legacy requests (they selected only single transport/capability)
     * so this function cannot map the request to a single legacy type and
     * the resulting network will not be available to the legacy APIs.
     *
     * This code is only called from the requestNetwork API (L and above).
     *
     * Setting a legacy type causes CONNECTIVITY_ACTION broadcasts, which are expensive
     * because they wake up lots of apps - see http://b/23350688 . So we currently only
     * do this for SUPL requests, which are the only ones that we know need it. If
     * omitting these broadcasts causes unacceptable app breakage, then for backwards
     * compatibility we can send them:
     *
     * if (targetSdkVersion < Build.VERSION_CODES.M) &&        // legacy API unsupported >= M
     *     targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP))  // requestNetwork not present < L
     *
     * TODO - This should be removed when the legacy APIs are removed.
     */
    private int inferLegacyTypeForNetworkCapabilities(NetworkCapabilities netCap) {
        if (netCap == null) {
            return TYPE_NONE;
        }

        if (!netCap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return TYPE_NONE;
        }

        // Do this only for SUPL, until GnssLocationProvider is fixed. http://b/25876485 .
        if (!netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
            // NOTE: if this causes app breakage, we should not just comment out this early return;
            // instead, we should make this early return conditional on the requesting app's target
            // SDK version, as described in the comment above.
            return TYPE_NONE;
        }

        String type = null;
        int result = TYPE_NONE;

        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)) {
            type = "enableCBS";
            result = TYPE_MOBILE_CBS;
        } else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
            type = "enableIMS";
            result = TYPE_MOBILE_IMS;
        } else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)) {
            type = "enableFOTA";
            result = TYPE_MOBILE_FOTA;
        } else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN)) {
            type = "enableDUN";
            result = TYPE_MOBILE_DUN;
        } else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
            type = "enableSUPL";
            result = TYPE_MOBILE_SUPL;
        // back out this hack for mms as they no longer need this and it's causing
        // device slowdowns - b/23350688 (note, supl still needs this)
        //} else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
        //    type = "enableMMS";
        //    result = TYPE_MOBILE_MMS;
        } else if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            type = "enableHIPRI";
            result = TYPE_MOBILE_HIPRI;
        }
        if (type != null) {
            NetworkCapabilities testCap = networkCapabilitiesForFeature(TYPE_MOBILE, type);
            if (testCap.equalsNetCapabilities(netCap) && testCap.equalsTransportTypes(netCap)) {
                return result;
            }
        }
        return TYPE_NONE;
    }

    private int legacyTypeForNetworkCapabilities(NetworkCapabilities netCap) {
        if (netCap == null) return TYPE_NONE;
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)) {
            return TYPE_MOBILE_CBS;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
            return TYPE_MOBILE_IMS;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)) {
            return TYPE_MOBILE_FOTA;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN)) {
            return TYPE_MOBILE_DUN;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
            return TYPE_MOBILE_SUPL;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
            return TYPE_MOBILE_MMS;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return TYPE_MOBILE_HIPRI;
        }
        if (netCap.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)) {
            return TYPE_WIFI_P2P;
        }
        return TYPE_NONE;
    }

    private static class LegacyRequest {
        NetworkCapabilities networkCapabilities;
        NetworkRequest networkRequest;
        int expireSequenceNumber;
        Network currentNetwork;
        int delay = -1;

        private void clearDnsBinding() {
            if (currentNetwork != null) {
                currentNetwork = null;
                setProcessDefaultNetworkForHostResolution(null);
            }
        }

        NetworkCallback networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                currentNetwork = network;
                Log.d(TAG, "startUsingNetworkFeature got Network:" + network);
                setProcessDefaultNetworkForHostResolution(network);
            }
            @Override
            public void onLost(Network network) {
                if (network.equals(currentNetwork)) clearDnsBinding();
                Log.d(TAG, "startUsingNetworkFeature lost Network:" + network);
            }
        };
    }

    private static HashMap<NetworkCapabilities, LegacyRequest> sLegacyRequests =
            new HashMap<NetworkCapabilities, LegacyRequest>();

    private NetworkRequest findRequestForFeature(NetworkCapabilities netCap) {
        synchronized (sLegacyRequests) {
            LegacyRequest l = sLegacyRequests.get(netCap);
            if (l != null) return l.networkRequest;
        }
        return null;
    }

    private void renewRequestLocked(LegacyRequest l) {
        l.expireSequenceNumber++;
        Log.d(TAG, "renewing request to seqNum " + l.expireSequenceNumber);
        sendExpireMsgForFeature(l.networkCapabilities, l.expireSequenceNumber, l.delay);
    }

    private void expireRequest(NetworkCapabilities netCap, int sequenceNum) {
        int ourSeqNum = -1;
        synchronized (sLegacyRequests) {
            LegacyRequest l = sLegacyRequests.get(netCap);
            if (l == null) return;
            ourSeqNum = l.expireSequenceNumber;
            if (l.expireSequenceNumber == sequenceNum) removeRequestForFeature(netCap);
        }
        Log.d(TAG, "expireRequest with " + ourSeqNum + ", " + sequenceNum);
    }

    private NetworkRequest requestNetworkForFeatureLocked(NetworkCapabilities netCap) {
        int delay = -1;
        int type = legacyTypeForNetworkCapabilities(netCap);
        try {
            delay = mService.getRestoreDefaultNetworkDelay(type);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        LegacyRequest l = new LegacyRequest();
        l.networkCapabilities = netCap;
        l.delay = delay;
        l.expireSequenceNumber = 0;
        l.networkRequest = sendRequestForNetwork(
                netCap, l.networkCallback, 0, REQUEST, type, getDefaultHandler());
        if (l.networkRequest == null) return null;
        sLegacyRequests.put(netCap, l);
        sendExpireMsgForFeature(netCap, l.expireSequenceNumber, delay);
        return l.networkRequest;
    }

    private void sendExpireMsgForFeature(NetworkCapabilities netCap, int seqNum, int delay) {
        if (delay >= 0) {
            Log.d(TAG, "sending expire msg with seqNum " + seqNum + " and delay " + delay);
            CallbackHandler handler = getDefaultHandler();
            Message msg = handler.obtainMessage(EXPIRE_LEGACY_REQUEST, seqNum, 0, netCap);
            handler.sendMessageDelayed(msg, delay);
        }
    }

    private boolean removeRequestForFeature(NetworkCapabilities netCap) {
        final LegacyRequest l;
        synchronized (sLegacyRequests) {
            l = sLegacyRequests.remove(netCap);
        }
        if (l == null) return false;
        unregisterNetworkCallback(l.networkCallback);
        l.clearDnsBinding();
        return true;
    }

    private static final SparseIntArray sLegacyTypeToTransport = new SparseIntArray();
    static {
        sLegacyTypeToTransport.put(TYPE_MOBILE,       NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_CBS,   NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_DUN,   NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_FOTA,  NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_HIPRI, NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_IMS,   NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_MMS,   NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_MOBILE_SUPL,  NetworkCapabilities.TRANSPORT_CELLULAR);
        sLegacyTypeToTransport.put(TYPE_WIFI,         NetworkCapabilities.TRANSPORT_WIFI);
        sLegacyTypeToTransport.put(TYPE_WIFI_P2P,     NetworkCapabilities.TRANSPORT_WIFI);
        sLegacyTypeToTransport.put(TYPE_BLUETOOTH,    NetworkCapabilities.TRANSPORT_BLUETOOTH);
        sLegacyTypeToTransport.put(TYPE_ETHERNET,     NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private static final SparseIntArray sLegacyTypeToCapability = new SparseIntArray();
    static {
        sLegacyTypeToCapability.put(TYPE_MOBILE_CBS,  NetworkCapabilities.NET_CAPABILITY_CBS);
        sLegacyTypeToCapability.put(TYPE_MOBILE_DUN,  NetworkCapabilities.NET_CAPABILITY_DUN);
        sLegacyTypeToCapability.put(TYPE_MOBILE_FOTA, NetworkCapabilities.NET_CAPABILITY_FOTA);
        sLegacyTypeToCapability.put(TYPE_MOBILE_IMS,  NetworkCapabilities.NET_CAPABILITY_IMS);
        sLegacyTypeToCapability.put(TYPE_MOBILE_MMS,  NetworkCapabilities.NET_CAPABILITY_MMS);
        sLegacyTypeToCapability.put(TYPE_MOBILE_SUPL, NetworkCapabilities.NET_CAPABILITY_SUPL);
        sLegacyTypeToCapability.put(TYPE_WIFI_P2P,    NetworkCapabilities.NET_CAPABILITY_WIFI_P2P);
    }

    /**
     * Given a legacy type (TYPE_WIFI, ...) returns a NetworkCapabilities
     * instance suitable for registering a request or callback.  Throws an
     * IllegalArgumentException if no mapping from the legacy type to
     * NetworkCapabilities is known.
     *
     * 
     */
    public static NetworkCapabilities networkCapabilitiesForType(int type) {
        final NetworkCapabilities nc = new NetworkCapabilities();

        // Map from type to transports.
        final int NOT_FOUND = -1;
        final int transport = sLegacyTypeToTransport.get(type, NOT_FOUND);
        if (transport == NOT_FOUND) {
            throw new IllegalArgumentException("unknown legacy type: " + type);
        }
        nc.addTransportType(transport);

        // Map from type to capabilities.
        nc.addCapability(sLegacyTypeToCapability.get(
                type, NetworkCapabilities.NET_CAPABILITY_INTERNET));
        nc.maybeMarkCapabilitiesRestricted();
        return nc;
    }

    /**  */
    public static class PacketKeepaliveCallback {
        /** The requested keepalive was successfully started. */
        public void onStarted() {}
        /** The keepalive was successfully stopped. */
        public void onStopped() {}
        /** An error occurred. */
        public void onError(int error) {}
    }

    /**
     * Allows applications to request that the system periodically send specific packets on their
     * behalf, using hardware offload to save battery power.
     *
     * To request that the system send keepalives, call one of the methods that return a
     * {@link ConnectivityManager.PacketKeepalive} object, such as {@link #startNattKeepalive},
     * passing in a non-null callback. If the callback is successfully started, the callback's
     * {@code onStarted} method will be called. If an error occurs, {@code onError} will be called,
     * specifying one of the {@code ERROR_*} constants in this class.
     *
     * To stop an existing keepalive, call {@link stop}. The system will call {@code onStopped} if
     * the operation was successfull or {@code onError} if an error occurred.
     *
     * 
     */
    public class PacketKeepalive {

        private static final String TAG = "PacketKeepalive";

        /**  */
        public static final int SUCCESS = 0;

        /**  */
        public static final int NO_KEEPALIVE = -1;

        /**  */
        public static final int BINDER_DIED = -10;

        /** The specified {@code Network} is not connected. */
        public static final int ERROR_INVALID_NETWORK = -20;
        /** The specified IP addresses are invalid. For example, the specified source IP address is
          * not configured on the specified {@code Network}. */
        public static final int ERROR_INVALID_IP_ADDRESS = -21;
        /** The requested port is invalid. */
        public static final int ERROR_INVALID_PORT = -22;
        /** The packet length is invalid (e.g., too long). */
        public static final int ERROR_INVALID_LENGTH = -23;
        /** The packet transmission interval is invalid (e.g., too short). */
        public static final int ERROR_INVALID_INTERVAL = -24;

        /** The hardware does not support this request. */
        public static final int ERROR_HARDWARE_UNSUPPORTED = -30;
        /** The hardware returned an error. */
        public static final int ERROR_HARDWARE_ERROR = -31;

        public static final int NATT_PORT = 4500;

        private final Network mNetwork;
        private final PacketKeepaliveCallback mCallback;
        private final Looper mLooper;
        private final Messenger mMessenger;

        private volatile Integer mSlot;

        void stopLooper() {
            mLooper.quit();
        }

        public void stop() {
            try {
                mService.stopKeepalive(mNetwork, mSlot);
            } catch (RemoteException e) {
                Log.e(TAG, "Error stopping packet keepalive: ", e);
                stopLooper();
            }
        }

        private PacketKeepalive(Network network, PacketKeepaliveCallback callback) {
            checkNotNull(network, "network cannot be null");
            checkNotNull(callback, "callback cannot be null");
            mNetwork = network;
            mCallback = callback;
            HandlerThread thread = new HandlerThread(TAG);
            thread.start();
            mLooper = thread.getLooper();
            mMessenger = new Messenger(new Handler(mLooper) {
                @Override
                public void handleMessage(Message message) {
                    switch (message.what) {
                        case NetworkAgent.EVENT_PACKET_KEEPALIVE:
                            int error = message.arg2;
                            try {
                                if (error == SUCCESS) {
                                    if (mSlot == null) {
                                        mSlot = message.arg1;
                                        mCallback.onStarted();
                                    } else {
                                        mSlot = null;
                                        stopLooper();
                                        mCallback.onStopped();
                                    }
                                } else {
                                    stopLooper();
                                    mCallback.onError(error);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Exception in keepalive callback(" + error + ")", e);
                            }
                            break;
                        default:
                            Log.e(TAG, "Unhandled message " + Integer.toHexString(message.what));
                            break;
                    }
                }
            });
        }
    }

    /**
     * Starts an IPsec NAT-T keepalive packet with the specified parameters.
     *
     * 
     */
    public PacketKeepalive startNattKeepalive(
            Network network, int intervalSeconds, PacketKeepaliveCallback callback,
            InetAddress srcAddr, int srcPort, InetAddress dstAddr) {
        final PacketKeepalive k = new PacketKeepalive(network, callback);
        try {
            mService.startNattKeepalive(network, intervalSeconds, k.mMessenger, new Binder(),
                    srcAddr.getHostAddress(), srcPort, dstAddr.getHostAddress());
        } catch (RemoteException e) {
            Log.e(TAG, "Error starting packet keepalive: ", e);
            k.stopLooper();
            return null;
        }
        return k;
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface. An attempt to add a route that
     * already exists is ignored, but treated as successful.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType the type of the network over which traffic to the specified
     * host is to be routed
     * @param hostAddress the IP address of the host to which the route is desired
     * @return {@code true} on success, {@code false} on failure
     *
     * @deprecated Deprecated in favor of the
     *             {@link #requestNetwork(NetworkRequest, NetworkCallback)},
     *             {@link #bindProcessToNetwork} and {@link Network#getSocketFactory} API.
     *             In {@link VERSION_CODES#M}, and above, this method is unsupported and will
     *             throw {@code UnsupportedOperationException} if called.
     * 
     */
    @Deprecated
    public boolean requestRouteToHost(int networkType, int hostAddress) {
        return requestRouteToHostAddress(networkType, NetworkUtils.intToInetAddress(hostAddress));
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface. An attempt to add a route that
     * already exists is ignored, but treated as successful.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param networkType the type of the network over which traffic to the specified
     * host is to be routed
     * @param hostAddress the IP address of the host to which the route is desired
     * @return {@code true} on success, {@code false} on failure
     * 
     * @deprecated Deprecated in favor of the {@link #requestNetwork} and
     *             {@link #bindProcessToNetwork} API.
     */
    @Deprecated
    public boolean requestRouteToHostAddress(int networkType, InetAddress hostAddress) {
        checkLegacyRoutingApiAccess();
        try {
            return mService.requestRouteToHostAddress(networkType, hostAddress.getAddress());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the value of the setting for background data usage. If false,
     * applications should not use the network if the application is not in the
     * foreground. Developers should respect this setting, and check the value
     * of this before performing any background data operations.
     * <p>
     * All applications that have background services that use the network
     * should listen to {@link #ACTION_BACKGROUND_DATA_SETTING_CHANGED}.
     * <p>
     * @deprecated As of {@link VERSION_CODES#ICE_CREAM_SANDWICH}, availability of
     * background data depends on several combined factors, and this method will
     * always return {@code true}. Instead, when background data is unavailable,
     * {@link #getActiveNetworkInfo()} will now appear disconnected.
     *
     * @return Whether background data usage is allowed.
     */
    @Deprecated
    public boolean getBackgroundDataSetting() {
        // assume that background data is allowed; final authority is
        // NetworkInfo which may be blocked.
        return true;
    }

    /**
     * Sets the value of the setting for background data usage.
     *
     * @param allowBackgroundData Whether an application should use data while
     *            it is in the background.
     *
     * ref android.Manifest.permission#CHANGE_BACKGROUND_DATA_SETTING
     * 
     */
    @Deprecated
    public void setBackgroundDataSetting(boolean allowBackgroundData) {
        // ignored
    }

    /**
     * Return quota status for the current active network, or {@code null} if no
     * network is active. Quota status can change rapidly, so these values
     * shouldn't be cached.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkQuotaInfo getActiveNetworkQuotaInfo() {
        try {
            return mService.getActiveNetworkQuotaInfo();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     * @deprecated Talk to TelephonyManager directly
     */
    @Deprecated
    public boolean getMobileDataEnabled() {
        IBinder b = ServiceManager.getService(Context.TELEPHONY_SERVICE);
        if (b != null) {
            try {
                ITelephony it = ITelephony.Stub.asInterface(b);
                int subId = SubscriptionManager.getDefaultDataSubscriptionId();
                Log.d("ConnectivityManager", "getMobileDataEnabled()+ subId=" + subId);
                boolean retVal = it.getDataEnabled(subId);
                Log.d("ConnectivityManager", "getMobileDataEnabled()- subId=" + subId
                        + " retVal=" + retVal);
                return retVal;
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
        Log.d("ConnectivityManager", "getMobileDataEnabled()- remote exception retVal=false");
        return false;
    }

    /**
     * Callback for use with {@link ConnectivityManager#addDefaultNetworkActiveListener}
     * to find out when the system default network has gone in to a high power state.
     */
    public interface OnNetworkActiveListener {
        /**
         * Called on the main thread of the process to report that the current data network
         * has become active, and it is now a good time to perform any pending network
         * operations.  Note that this listener only tells you when the network becomes
         * active; if at any other time you want to know whether it is active (and thus okay
         * to initiate network traffic), you can retrieve its instantaneous state with
         * {@link ConnectivityManager#isDefaultNetworkActive}.
         */
        public void onNetworkActive();
    }

    private INetworkManagementService getNetworkManagementService() {
        synchronized (this) {
            if (mNMService != null) {
                return mNMService;
            }
            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            mNMService = INetworkManagementService.Stub.asInterface(b);
            return mNMService;
        }
    }

    private final ArrayMap<OnNetworkActiveListener, INetworkActivityListener>
            mNetworkActivityListeners
                    = new ArrayMap<OnNetworkActiveListener, INetworkActivityListener>();

    /**
     * Start listening to reports when the system's default data network is active, meaning it is
     * a good time to perform network traffic.  Use {@link #isDefaultNetworkActive()}
     * to determine the current state of the system's default network after registering the
     * listener.
     * <p>
     * If the process default network has been set with
     * {@link ConnectivityManager#bindProcessToNetwork} this function will not
     * reflect the process's default, but the system default.
     *
     * @param l The listener to be told when the network is active.
     */
    public void addDefaultNetworkActiveListener(final OnNetworkActiveListener l) {
        INetworkActivityListener rl = new INetworkActivityListener.Stub() {
            @Override
            public void onNetworkActive() throws RemoteException {
                l.onNetworkActive();
            }
        };

        try {
            getNetworkManagementService().registerNetworkActivityListener(rl);
            mNetworkActivityListeners.put(l, rl);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove network active listener previously registered with
     * {@link #addDefaultNetworkActiveListener}.
     *
     * @param l Previously registered listener.
     */
    public void removeDefaultNetworkActiveListener(OnNetworkActiveListener l) {
        INetworkActivityListener rl = mNetworkActivityListeners.get(l);
        if (rl == null) {
            throw new IllegalArgumentException("Listener not registered: " + l);
        }
        try {
            getNetworkManagementService().unregisterNetworkActivityListener(rl);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Return whether the data network is currently active.  An active network means that
     * it is currently in a high power state for performing data transmission.  On some
     * types of networks, it may be expensive to move and stay in such a state, so it is
     * more power efficient to batch network traffic together when the radio is already in
     * this state.  This method tells you whether right now is currently a good time to
     * initiate network traffic, as the network is already active.
     */
    public boolean isDefaultNetworkActive() {
        try {
            return getNetworkManagementService().isNetworkActive();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     */
    public ConnectivityManager(Context context, IConnectivityManager service) {
        mContext = checkNotNull(context, "missing context");
        mService = checkNotNull(service, "missing IConnectivityManager");
        sInstance = this;
    }

    /**  */
    public static ConnectivityManager from(Context context) {
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /* TODO: These permissions checks don't belong in client-side code. Move them to
     * services.jar, possibly in com.android.server.net. */

    /**  */
    public static final boolean checkChangePermission(Context context) {
        int uid = Binder.getCallingUid();
        return Settings.checkAndNoteChangeNetworkStateOperation(context, uid, Settings
                .getPackageNameForUid(context, uid), false /* throwException */);
    }

    /**  */
    public static final void enforceChangePermission(Context context) {
        int uid = Binder.getCallingUid();
        Settings.checkAndNoteChangeNetworkStateOperation(context, uid, Settings
                .getPackageNameForUid(context, uid), true /* throwException */);
    }

    /** { */
    public static final void enforceTetherChangePermission(Context context) {
        if (context.getResources().getStringArray(
                com.android.internal.R.array.config_mobile_hotspot_provision_app).length == 2) {
            // Have a provisioning app - must only let system apps (which check this app)
            // turn on tethering
            context.enforceCallingOrSelfPermission(
                    android.Manifest.permission.TETHER_PRIVILEGED, "ConnectivityService");
        } else {
            int uid = Binder.getCallingUid();
            Settings.checkAndNoteWriteSettingsOperation(context, uid, Settings
                    .getPackageNameForUid(context, uid), true /* throwException */);
        }
    }

    /**
     * @deprecated - use getSystemService. This is a kludge to support static access in certain
     *               situations where a Context pointer is unavailable.
     * 
     */
    @Deprecated
    static ConnectivityManager getInstanceOrNull() {
        return sInstance;
    }

    /**
     * @deprecated - use getSystemService. This is a kludge to support static access in certain
     *               situations where a Context pointer is unavailable.
     * 
     */
    @Deprecated
    private static ConnectivityManager getInstance() {
        if (getInstanceOrNull() == null) {
            throw new IllegalStateException("No ConnectivityManager yet constructed");
        }
        return getInstanceOrNull();
    }

    /**
     * Get the set of tetherable, available interfaces.  This list is limited by
     * device configuration and current interface existence.
     *
     * @return an array of 0 or more Strings of tetherable interface names.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableIfaces() {
        try {
            return mService.getTetherableIfaces();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the set of tethered interfaces.
     *
     * @return an array of 0 or more String of currently tethered interface names.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetheredIfaces() {
        try {
            return mService.getTetheredIfaces();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the set of interface names which attempted to tether but
     * failed.  Re-attempting to tether may cause them to reset to the Tethered
     * state.  Alternatively, causing the interface to be destroyed and recreated
     * may cause them to reset to the available state.
     * {@link ConnectivityManager#getLastTetherError} can be used to get more
     * information on the cause of the errors.
     *
     * @return an array of 0 or more String indicating the interface names
     *        which failed to tether.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetheringErroredIfaces() {
        try {
            return mService.getTetheringErroredIfaces();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the set of tethered dhcp ranges.
     *
     * @return an array of 0 or more {@code String} of tethered dhcp ranges.
     * 
     */
    public String[] getTetheredDhcpRanges() {
        try {
            return mService.getTetheredDhcpRanges();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Attempt to tether the named interface.  This will setup a dhcp server
     * on the interface, forward and NAT IP packets and forward DNS requests
     * to the best active upstream network interface.  Note that if no upstream
     * IP network interface is available, dhcp will still run and traffic will be
     * allowed between the tethered devices and this device, though upstream net
     * access will of course fail until an upstream network interface becomes
     * active.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * and WifiStateMachine which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to tether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * 
     */
    public int tether(String iface) {
        try {
            return mService.tether(iface);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stop tethering the named interface.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * and WifiStateMachine which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to untether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * 
     */
    public int untether(String iface) {
        try {
            return mService.untether(iface);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Check if the device allows for tethering.  It may be disabled via
     * {@code ro.tether.denied} system property, Settings.TETHER_SUPPORTED or
     * due to device configuration.
     *
     * @return a boolean - {@code true} indicating Tethering is supported.
     *
     * 
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public boolean isTetheringSupported() {
        try {
            return mService.isTetheringSupported();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Callback for use with {@link #startTethering} to find out whether tethering succeeded.
     * 
     */
    @SystemApi
    public static abstract class OnStartTetheringCallback {
        /**
         * Called when tethering has been successfully started.
         */
        public void onTetheringStarted() {};

        /**
         * Called when starting tethering failed.
         */
        public void onTetheringFailed() {};
    }

    /**
     * Convenient overload for
     * {@link #startTethering(int, boolean, OnStartTetheringCallback, Handler)} which passes a null
     * handler to run on the current thread's {@link Looper}.
     * 
     */
    @SystemApi
    public void startTethering(int type, boolean showProvisioningUi,
            final OnStartTetheringCallback callback) {
        startTethering(type, showProvisioningUi, callback, null);
    }

    /**
     * Runs tether provisioning for the given type if needed and then starts tethering if
     * the check succeeds. If no carrier provisioning is required for tethering, tethering is
     * enabled immediately. If provisioning fails, tethering will not be enabled. It also
     * schedules tether provisioning re-checks if appropriate.
     *
     * @param type The type of tethering to start. Must be one of
     *         ConnectivityManager.TETHERING_WIFI,
     *         ConnectivityManager.TETHERING_USB, or
     *         ConnectivityManager.TETHERING_BLUETOOTH.
     * @param showProvisioningUi a boolean indicating to show the provisioning app UI if there
     *         is one. This should be true the first time this function is called and also any time
     *         the user can see this UI. It gives users information from their carrier about the
     *         check failing and how they can sign up for tethering if possible.
     * @param callback an {@link OnStartTetheringCallback} which will be called to notify the caller
     *         of the result of trying to tether.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * 
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.TETHER_PRIVILEGED)
    public void startTethering(int type, boolean showProvisioningUi,
            final OnStartTetheringCallback callback, Handler handler) {
        checkNotNull(callback, "OnStartTetheringCallback cannot be null.");

        ResultReceiver wrappedCallback = new ResultReceiver(handler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == TETHER_ERROR_NO_ERROR) {
                    callback.onTetheringStarted();
                } else {
                    callback.onTetheringFailed();
                }
            }
        };

        try {
            mService.startTethering(type, wrappedCallback, showProvisioningUi);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception trying to start tethering.", e);
            wrappedCallback.send(TETHER_ERROR_SERVICE_UNAVAIL, null);
        }
    }

    /**
     * Stops tethering for the given type. Also cancels any provisioning rechecks for that type if
     * applicable.
     *
     * @param type The type of tethering to stop. Must be one of
     *         ConnectivityManager.TETHERING_WIFI,
     *         ConnectivityManager.TETHERING_USB, or
     *         ConnectivityManager.TETHERING_BLUETOOTH.
     * 
     */
    @SystemApi
    @RequiresPermission(android.Manifest.permission.TETHER_PRIVILEGED)
    public void stopTethering(int type) {
        try {
            mService.stopTethering(type);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * USB network interfaces.  If USB tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable usb interfaces.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableUsbRegexs() {
        try {
            return mService.getTetherableUsbRegexs();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Wifi network interfaces.  If Wifi tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable wifi interfaces.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableWifiRegexs() {
        try {
            return mService.getTetherableWifiRegexs();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Bluetooth network interfaces.  If Bluetooth tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable bluetooth interfaces.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public String[] getTetherableBluetoothRegexs() {
        try {
            return mService.getTetherableBluetoothRegexs();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Attempt to both alter the mode of USB and Tethering of USB.  A
     * utility method to deal with some of the complexity of USB - will
     * attempt to switch to Rndis and subsequently tether the resulting
     * interface on {@code true} or turn off tethering and switch off
     * Rndis on {@code false}.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param enable a boolean - {@code true} to enable tethering
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     *
     * 
     */
    public int setUsbTethering(boolean enable) {
        try {
            return mService.setUsbTethering(enable);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**  */
    public static final int TETHER_ERROR_NO_ERROR           = 0;
    /**  */
    public static final int TETHER_ERROR_UNKNOWN_IFACE      = 1;
    /**  */
    public static final int TETHER_ERROR_SERVICE_UNAVAIL    = 2;
    /**  */
    public static final int TETHER_ERROR_UNSUPPORTED        = 3;
    /**  */
    public static final int TETHER_ERROR_UNAVAIL_IFACE      = 4;
    /**  */
    public static final int TETHER_ERROR_MASTER_ERROR       = 5;
    /**  */
    public static final int TETHER_ERROR_TETHER_IFACE_ERROR = 6;
    /**  */
    public static final int TETHER_ERROR_UNTETHER_IFACE_ERROR = 7;
    /**  */
    public static final int TETHER_ERROR_ENABLE_NAT_ERROR     = 8;
    /**  */
    public static final int TETHER_ERROR_DISABLE_NAT_ERROR    = 9;
    /**  */
    public static final int TETHER_ERROR_IFACE_CFG_ERROR      = 10;
    /**  */
    public static final int TETHER_ERROR_PROVISION_FAILED     = 11;

    /**
     * Get a more detailed error code after a Tethering or Untethering
     * request asynchronously failed.
     *
     * @param iface The name of the interface of interest
     * @return error The error code of the last error tethering or untethering the named
     *               interface
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public int getLastTetherError(String iface) {
        try {
            return mService.getLastTetherError(iface);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Report network connectivity status.  This is currently used only
     * to alter status bar UI.
     * <p>This method requires the caller to hold the permission
     * {@link android.Manifest.permission#STATUS_BAR}.
     *
     * @param networkType The type of network you want to report on
     * @param percentage The quality of the connection 0 is bad, 100 is good
     * 
     */
    public void reportInetCondition(int networkType, int percentage) {
        try {
            mService.reportInetCondition(networkType, percentage);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Report a problem network to the framework.  This provides a hint to the system
     * that there might be connectivity problems on this network and may cause
     * the framework to re-evaluate network connectivity and/or switch to another
     * network.
     *
     * @param network The {@link Network} the application was attempting to use
     *                or {@code null} to indicate the current default network.
     * @deprecated Use {@link #reportNetworkConnectivity} which allows reporting both
     *             working and non-working connectivity.
     */
    @Deprecated
    public void reportBadNetwork(Network network) {
        try {
            // One of these will be ignored because it matches system's current state.
            // The other will trigger the necessary reevaluation.
            mService.reportNetworkConnectivity(network, true);
            mService.reportNetworkConnectivity(network, false);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Report to the framework whether a network has working connectivity.
     * This provides a hint to the system that a particular network is providing
     * working connectivity or not.  In response the framework may re-evaluate
     * the network's connectivity and might take further action thereafter.
     *
     * @param network The {@link Network} the application was attempting to use
     *                or {@code null} to indicate the current default network.
     * @param hasConnectivity {@code true} if the application was able to successfully access the
     *                        Internet using {@code network} or {@code false} if not.
     */
    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
        try {
            mService.reportNetworkConnectivity(network, hasConnectivity);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set a network-independent global http proxy.  This is not normally what you want
     * for typical HTTP proxies - they are general network dependent.  However if you're
     * doing something unusual like general internal filtering this may be useful.  On
     * a private network where the proxy is not accessible, you may break HTTP using this.
     *
     * @param p A {@link ProxyInfo} object defining the new global
     *        HTTP proxy.  A {@code null} value will clear the global HTTP proxy.
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public void setGlobalProxy(ProxyInfo p) {
        try {
            mService.setGlobalProxy(p);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Retrieve any network-independent global HTTP proxy.
     *
     * @return {@link ProxyInfo} for the current global HTTP proxy or {@code null}
     *        if no global HTTP proxy is set.
     * 
     */
    public ProxyInfo getGlobalProxy() {
        try {
            return mService.getGlobalProxy();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Retrieve the global HTTP proxy, or if no global HTTP proxy is set, a
     * network-specific HTTP proxy.  If {@code network} is null, the
     * network-specific proxy returned is the proxy of the default active
     * network.
     *
     * @return {@link ProxyInfo} for the current global HTTP proxy, or if no
     *         global HTTP proxy is set, {@code ProxyInfo} for {@code network},
     *         or when {@code network} is {@code null},
     *         the {@code ProxyInfo} for the default active network.  Returns
     *         {@code null} when no proxy applies or the caller doesn't have
     *         permission to use {@code network}.
     * 
     */
    public ProxyInfo getProxyForNetwork(Network network) {
        try {
            return mService.getProxyForNetwork(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the current default HTTP proxy settings.  If a global proxy is set it will be returned,
     * otherwise if this process is bound to a {@link Network} using
     * {@link #bindProcessToNetwork} then that {@code Network}'s proxy is returned, otherwise
     * the default network's proxy is returned.
     *
     * @return the {@link ProxyInfo} for the current HTTP proxy, or {@code null} if no
     *        HTTP proxy is active.
     */
    public ProxyInfo getDefaultProxy() {
        return getProxyForNetwork(getBoundNetworkForProcess());
    }

    /**
     * Returns true if the hardware supports the given network type
     * else it returns false.  This doesn't indicate we have coverage
     * or are authorized onto a network, just whether or not the
     * hardware supports it.  For example a GSM phone without a SIM
     * should still return {@code true} for mobile data, but a wifi only
     * tablet would return {@code false}.
     *
     * @param networkType The network type we'd like to check
     * @return {@code true} if supported, else {@code false}
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public boolean isNetworkSupported(int networkType) {
        try {
            return mService.isNetworkSupported(networkType);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns if the currently active data network is metered. A network is
     * classified as metered when the user is sensitive to heavy data usage on
     * that connection due to monetary costs, data limitations or
     * battery/performance issues. You should check this before doing large
     * data transfers, and warn the user or delay the operation until another
     * network is available.
     *
     * @return {@code true} if large transfers should be avoided, otherwise
     *        {@code false}.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public boolean isActiveNetworkMetered() {
        try {
            return mService.isActiveNetworkMetered();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * If the LockdownVpn mechanism is enabled, updates the vpn
     * with a reload of its profile.
     *
     * @return a boolean with {@code} indicating success
     *
     * <p>This method can only be called by the system UID
     * 
     */
    public boolean updateLockdownVpn() {
        try {
            return mService.updateLockdownVpn();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Check mobile provisioning.
     *
     * @param suggestedTimeOutMs, timeout in milliseconds
     *
     * @return time out that will be used, maybe less that suggestedTimeOutMs
     * -1 if an error.
     *
     * 
     */
    public int checkMobileProvisioning(int suggestedTimeOutMs) {
        int timeOutMs = -1;
        try {
            timeOutMs = mService.checkMobileProvisioning(suggestedTimeOutMs);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return timeOutMs;
    }

    /**
     * Get the mobile provisioning url.
     * 
     */
    public String getMobileProvisioningUrl() {
        try {
            return mService.getMobileProvisioningUrl();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set sign in error notification to visible or in visible
     *
     * @param visible
     * @param networkType
     *
     * 
     * @deprecated Doesn't properly deal with multiple connected networks of the same type.
     */
    @Deprecated
    public void setProvisioningNotificationVisible(boolean visible, int networkType,
            String action) {
        try {
            mService.setProvisioningNotificationVisible(visible, networkType, action);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set the value for enabling/disabling airplane mode
     *
     * @param enable whether to enable airplane mode or not
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public void setAirplaneMode(boolean enable) {
        try {
            mService.setAirplaneMode(enable);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**  */
    public void registerNetworkFactory(Messenger messenger, String name) {
        try {
            mService.registerNetworkFactory(messenger, name);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**  */
    public void unregisterNetworkFactory(Messenger messenger) {
        try {
            mService.unregisterNetworkFactory(messenger);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * 
     * Register a NetworkAgent with ConnectivityService.
     * @return NetID corresponding to NetworkAgent.
     */
    public int registerNetworkAgent(Messenger messenger, NetworkInfo ni, LinkProperties lp,
            NetworkCapabilities nc, int score, NetworkMisc misc) {
        try {
            return mService.registerNetworkAgent(messenger, ni, lp, nc, score, misc);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Base class for NetworkRequest callbacks.  Used for notifications about network
     * changes.  Should be extended by applications wanting notifications.
     */
    public static class NetworkCallback {
    	// these constructors are copied from AP level 31 sources - kaosko 2025-03-12
        public @interface Flag { }
        public NetworkCallback() {}
        public NetworkCallback(@Flag int flags) {}
    	
        /**
         * Called when the framework connects to a new network to evaluate whether it satisfies this
         * request. If evaluation succeeds, this callback may be followed by an {@link #onAvailable}
         * callback. There is no guarantee that this new network will satisfy any requests, or that
         * the network will stay connected for longer than the time necessary to evaluate it.
         * <p>
         * Most applications <b>should not</b> act on this callback, and should instead use
         * {@link #onAvailable}. This callback is intended for use by applications that can assist
         * the framework in properly evaluating the network &mdash; for example, an application that
         * can automatically log in to a captive portal without user intervention.
         *
         * @param network The {@link Network} of the network that is being evaluated.
         *
         * 
         */
        public void onPreCheck(Network network) {}

        /**
         * Called when the framework connects and has declared a new network ready for use.
         * This callback may be called more than once if the {@link Network} that is
         * satisfying the request changes.
         *
         * @param network The {@link Network} of the satisfying network.
         */
        public void onAvailable(Network network) {}

        /**
         * Called when the network is about to be disconnected.  Often paired with an
         * {@link NetworkCallback#onAvailable} call with the new replacement network
         * for graceful handover.  This may not be called if we have a hard loss
         * (loss without warning).  This may be followed by either a
         * {@link NetworkCallback#onLost} call or a
         * {@link NetworkCallback#onAvailable} call for this network depending
         * on whether we lose or regain it.
         *
         * @param network The {@link Network} that is about to be disconnected.
         * @param maxMsToLive The time in ms the framework will attempt to keep the
         *                     network connected.  Note that the network may suffer a
         *                     hard loss at any time.
         */
        public void onLosing(Network network, int maxMsToLive) {}

        /**
         * Called when the framework has a hard loss of the network or when the
         * graceful failure ends.
         *
         * @param network The {@link Network} lost.
         */
        public void onLost(Network network) {}

        /**
         * Called if no network is found in the timeout time specified in
         * #requestNetwork(NetworkRequest, int, NetworkCallback) call. This callback is not
         * called for the version of {@link #requestNetwork(NetworkRequest, NetworkCallback)}
         * without timeout. When this callback is invoked the associated
         * {@link NetworkRequest} will have already been removed and released, as if
         * {@link #unregisterNetworkCallback(NetworkCallback)} had been called.
         */
        public void onUnavailable() {}

        /**
         * Called when the network the framework connected to for this request
         * changes capabilities but still satisfies the stated need.
         *
         * @param network The {@link Network} whose capabilities have changed.
         * @param networkCapabilities The new {@link android.net.NetworkCapabilities} for this network.
         */
        public void onCapabilitiesChanged(Network network,
                NetworkCapabilities networkCapabilities) {}

        /**
         * Called when the network the framework connected to for this request
         * changes {@link LinkProperties}.
         *
         * @param network The {@link Network} whose link properties have changed.
         * @param linkProperties The new {@link LinkProperties} for this network.
         */
        public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {}

        /**
         * Called when the network the framework connected to for this request
         * goes into NetworkInfo.DetailedState.SUSPENDED.
         * This generally means that while the TCP connections are still live,
         * temporarily network data fails to transfer.  Specifically this is used
         * on cellular networks to mask temporary outages when driving through
         * a tunnel, etc.
         * 
         */
        public void onNetworkSuspended(Network network) {}

        /**
         * Called when the network the framework connected to for this request
         * returns from a NetworkInfo.DetailedState.SUSPENDED state.
         * This should always be preceeded by a matching {@code onNetworkSuspended}
         * call.
         * 
         */
        public void onNetworkResumed(Network network) {}

        private NetworkRequest networkRequest;
    }

    private static final int BASE = Protocol.BASE_CONNECTIVITY_MANAGER;
    /**  */
    public static final int CALLBACK_PRECHECK            = BASE + 1;
    /**  */
    public static final int CALLBACK_AVAILABLE           = BASE + 2;
    /**  arg1 = TTL */
    public static final int CALLBACK_LOSING              = BASE + 3;
    /**  */
    public static final int CALLBACK_LOST                = BASE + 4;
    /**  */
    public static final int CALLBACK_UNAVAIL             = BASE + 5;
    /**  */
    public static final int CALLBACK_CAP_CHANGED         = BASE + 6;
    /**  */
    public static final int CALLBACK_IP_CHANGED          = BASE + 7;
    /**  */
    public static final int CALLBACK_RELEASED            = BASE + 8;
    // TODO: consider deleting CALLBACK_EXIT and shifting following enum codes down by 1.
    /**  */
    public static final int CALLBACK_EXIT                = BASE + 9;
    /**  obj = NetworkCapabilities, arg1 = seq number */
    private static final int EXPIRE_LEGACY_REQUEST       = BASE + 10;
    /**  */
    public static final int CALLBACK_SUSPENDED           = BASE + 11;
    /**  */
    public static final int CALLBACK_RESUMED             = BASE + 12;

    /**  */
    public static String getCallbackName(int whichCallback) {
        switch (whichCallback) {
            case CALLBACK_PRECHECK:     return "CALLBACK_PRECHECK";
            case CALLBACK_AVAILABLE:    return "CALLBACK_AVAILABLE";
            case CALLBACK_LOSING:       return "CALLBACK_LOSING";
            case CALLBACK_LOST:         return "CALLBACK_LOST";
            case CALLBACK_UNAVAIL:      return "CALLBACK_UNAVAIL";
            case CALLBACK_CAP_CHANGED:  return "CALLBACK_CAP_CHANGED";
            case CALLBACK_IP_CHANGED:   return "CALLBACK_IP_CHANGED";
            case CALLBACK_RELEASED:     return "CALLBACK_RELEASED";
            case CALLBACK_EXIT:         return "CALLBACK_EXIT";
            case EXPIRE_LEGACY_REQUEST: return "EXPIRE_LEGACY_REQUEST";
            case CALLBACK_SUSPENDED:    return "CALLBACK_SUSPENDED";
            case CALLBACK_RESUMED:      return "CALLBACK_RESUMED";
            default:
                return Integer.toString(whichCallback);
        }
    }

    private class CallbackHandler extends Handler {
        private static final String TAG = "ConnectivityManager.CallbackHandler";
        private static final boolean DBG = false;

        CallbackHandler(Looper looper) {
            super(looper);
        }

        CallbackHandler(Handler handler) {
            this(handler.getLooper());
        }

        @Override
        public void handleMessage(Message message) {
            NetworkRequest request = getObject(message, NetworkRequest.class);
            Network network = getObject(message, Network.class);
            if (DBG) {
                Log.d(TAG, whatToString(message.what) + " for network " + network);
            }
            switch (message.what) {
                case CALLBACK_PRECHECK: {
                    NetworkCallback callback = getCallback(request, "PRECHECK");
                    if (callback != null) {
                        callback.onPreCheck(network);
                    }
                    break;
                }
                case CALLBACK_AVAILABLE: {
                    NetworkCallback callback = getCallback(request, "AVAILABLE");
                    if (callback != null) {
                        callback.onAvailable(network);
                    }
                    break;
                }
                case CALLBACK_LOSING: {
                    NetworkCallback callback = getCallback(request, "LOSING");
                    if (callback != null) {
                        callback.onLosing(network, message.arg1);
                    }
                    break;
                }
                case CALLBACK_LOST: {
                    NetworkCallback callback = getCallback(request, "LOST");
                    if (callback != null) {
                        callback.onLost(network);
                    }
                    break;
                }
                case CALLBACK_UNAVAIL: {
                    NetworkCallback callback = getCallback(request, "UNAVAIL");
                    if (callback != null) {
                        callback.onUnavailable();
                    }
                    break;
                }
                case CALLBACK_CAP_CHANGED: {
                    NetworkCallback callback = getCallback(request, "CAP_CHANGED");
                    if (callback != null) {
                        NetworkCapabilities cap = getObject(message, NetworkCapabilities.class);
                        callback.onCapabilitiesChanged(network, cap);
                    }
                    break;
                }
                case CALLBACK_IP_CHANGED: {
                    NetworkCallback callback = getCallback(request, "IP_CHANGED");
                    if (callback != null) {
                        LinkProperties lp = getObject(message, LinkProperties.class);
                        callback.onLinkPropertiesChanged(network, lp);
                    }
                    break;
                }
                case CALLBACK_SUSPENDED: {
                    NetworkCallback callback = getCallback(request, "SUSPENDED");
                    if (callback != null) {
                        callback.onNetworkSuspended(network);
                    }
                    break;
                }
                case CALLBACK_RESUMED: {
                    NetworkCallback callback = getCallback(request, "RESUMED");
                    if (callback != null) {
                        callback.onNetworkResumed(network);
                    }
                    break;
                }
                case CALLBACK_RELEASED: {
                    final NetworkCallback callback;
                    synchronized(sCallbacks) {
                        callback = sCallbacks.remove(request);
                    }
                    if (callback == null) {
                        Log.e(TAG, "callback not found for RELEASED message");
                    }
                    break;
                }
                case CALLBACK_EXIT: {
                    break;
                }
                case EXPIRE_LEGACY_REQUEST: {
                    expireRequest((NetworkCapabilities)message.obj, message.arg1);
                    break;
                }
            }
        }

        private <T> T getObject(Message msg, Class<T> c) {
            return (T) msg.getData().getParcelable(c.getSimpleName());
        }

        private NetworkCallback getCallback(NetworkRequest req, String name) {
            NetworkCallback callback;
            synchronized(sCallbacks) {
                callback = sCallbacks.get(req);
            }
            if (callback == null) {
                Log.e(TAG, "callback not found for " + name + " message");
            }
            return callback;
        }
    }

    private CallbackHandler getDefaultHandler() {
        synchronized (sCallbacks) {
            if (sCallbackHandler == null) {
                sCallbackHandler = new CallbackHandler(ConnectivityThread.getInstanceLooper());
            }
            return sCallbackHandler;
        }
    }

    private static final HashMap<NetworkRequest, NetworkCallback> sCallbacks = new HashMap<>();
    private static CallbackHandler sCallbackHandler;

    private static final int LISTEN  = 1;
    private static final int REQUEST = 2;

    private NetworkRequest sendRequestForNetwork(NetworkCapabilities need, NetworkCallback callback,
            int timeoutMs, int action, int legacyType, CallbackHandler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("null NetworkCallback");
        }
        if (need == null && action != REQUEST) {
            throw new IllegalArgumentException("null NetworkCapabilities");
        }
        // TODO: throw an exception if callback.networkRequest is not null.
        // http://b/20701525
        final NetworkRequest request;
        try {
            synchronized(sCallbacks) {
                Messenger messenger = new Messenger(handler);
                Binder binder = new Binder();
                if (action == LISTEN) {
                    request = mService.listenForNetwork(need, messenger, binder);
                } else {
                    request = mService.requestNetwork(
                            need, messenger, timeoutMs, binder, legacyType);
                }
                if (request != null) {
                    sCallbacks.put(request, callback);
                }
                callback.networkRequest = request;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return request;
    }

    /**
     * Helper function to request a network with a particular legacy type.
     *
     * This is temporarily public  so it can be called by system code that uses the
     * NetworkRequest API to request networks but relies on CONNECTIVITY_ACTION broadcasts for
     * instead network notifications.
     *
     * TODO: update said system code to rely on NetworkCallbacks and make this method private.
     *
     * 
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback,
            int timeoutMs, int legacyType, Handler handler) {
        CallbackHandler cbHandler = new CallbackHandler(handler);
        NetworkCapabilities nc = request.networkCapabilities;
        sendRequestForNetwork(nc, networkCallback, timeoutMs, REQUEST, legacyType, cbHandler);
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This {@link NetworkRequest} will live until released via
     * {@link #unregisterNetworkCallback(NetworkCallback)} or the calling application exits. A
     * version of the method which takes a timeout is
     * #requestNetwork(NetworkRequest, int, NetworkCallback).
     * Status of the request can be followed by listening to the various
     * callbacks described in {@link NetworkCallback}.  The {@link Network}
     * can be used to direct traffic to the network.
     * <p>It is presently unsupported to request a network with mutable
     * {@link NetworkCapabilities} such as
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfing a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     *                        The callback is invoked on the default internal Handler.
     * @throws IllegalArgumentException if {@code request} specifies any mutable
     *         {@code NetworkCapabilities}.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback) {
        requestNetwork(request, networkCallback, getDefaultHandler());
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This {@link NetworkRequest} will live until released via
     * {@link #unregisterNetworkCallback(NetworkCallback)} or the calling application exits. A
     * version of the method which takes a timeout is
     * #requestNetwork(NetworkRequest, int, NetworkCallback).
     * Status of the request can be followed by listening to the various
     * callbacks described in {@link NetworkCallback}.  The {@link Network}
     * can be used to direct traffic to the network.
     * <p>It is presently unsupported to request a network with mutable
     * {@link NetworkCapabilities} such as
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfing a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @throws IllegalArgumentException if {@code request} specifies any mutable
     *         {@code NetworkCapabilities}.
     */
    public void requestNetwork(
            NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        int legacyType = inferLegacyTypeForNetworkCapabilities(request.networkCapabilities);
        CallbackHandler cbHandler = new CallbackHandler(handler);
        requestNetwork(request, networkCallback, 0, legacyType, cbHandler);
    }

    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}, limited
     * by a timeout.
     *
     * This function behaves identically to the non-timed-out version
     * {@link #requestNetwork(NetworkRequest, NetworkCallback)}, but if a suitable network
     * is not found within the given time (in milliseconds) the
     * {@link NetworkCallback#onUnavailable()} callback is called. The request can still be
     * released normally by calling {@link #unregisterNetworkCallback(NetworkCallback)} but does
     * not have to be released if timed-out (it is automatically released). Unregistering a
     * request that timed out is not an error.
     *
     * <p>Do not use this method to poll for the existence of specific networks (e.g. with a small
     * timeout) - {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} is provided
     * for that purpose. Calling this method will attempt to bring up the requested network.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param timeoutMs The time in milliseconds to attempt looking for a suitable network
     *                  before {@link NetworkCallback#onUnavailable()} is called. The timeout must
     *                  be a positive value.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback,
            int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Non-positive timeoutMs: " + timeoutMs);
        }
        int legacyType = inferLegacyTypeForNetworkCapabilities(request.networkCapabilities);
        requestNetwork(request, networkCallback, timeoutMs, legacyType, getDefaultHandler());
    }


    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}, limited
     * by a timeout.
     *
     * This function behaves identically to the non-timedout version, but if a suitable
     * network is not found within the given time (in milliseconds) the
     * {@link NetworkCallback#onUnavailable} callback is called. The request can still be
     * released normally by calling {@link #unregisterNetworkCallback(NetworkCallback)} but does
     * not have to be released if timed-out (it is automatically released). Unregistering a
     * request that timed out is not an error.
     *
     * <p>Do not use this method to poll for the existence of specific networks (e.g. with a small
     * timeout) - {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} is provided
     * for that purpose. Calling this method will attempt to bring up the requested network.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} to be utilized for this request. Note
     *                        the callback must not be shared - it uniquely specifies this request.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     * @param timeoutMs The time in milliseconds to attempt looking for a suitable network
     *                  before {@link NetworkCallback#onUnavailable} is called.
     */
    public void requestNetwork(NetworkRequest request, NetworkCallback networkCallback,
            Handler handler, int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Non-positive timeoutMs");
        }
        int legacyType = inferLegacyTypeForNetworkCapabilities(request.networkCapabilities);
        CallbackHandler cbHandler = new CallbackHandler(handler);
        requestNetwork(request, networkCallback, timeoutMs, legacyType, cbHandler);
    }

    /**
     * The lookup key for a {@link Network} object included with the intent after
     * successfully finding a network for the applications request.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     * <p>
     * Note that if you intend to invoke {@link Network#openConnection(java.net.URL)}
     * then you must get a ConnectivityManager instance before doing so.
     */
    public static final String EXTRA_NETWORK = "android.net.extra.NETWORK";

    /**
     * The lookup key for a {@link NetworkRequest} object included with the intent after
     * successfully finding a network for the applications request.  Retrieve it with
     * {@link android.content.Intent#getParcelableExtra(String)}.
     */
    public static final String EXTRA_NETWORK_REQUEST = "android.net.extra.NETWORK_REQUEST";


    /**
     * Request a network to satisfy a set of {@link android.net.NetworkCapabilities}.
     *
     * This function behaves identically to the version that takes a NetworkCallback, but instead
     * of {@link NetworkCallback} a {@link PendingIntent} is used.  This means
     * the request may outlive the calling application and get called back when a suitable
     * network is found.
     * <p>
     * The operation is an Intent broadcast that goes to a broadcast receiver that
     * you registered with {@link Context#registerReceiver} or through the
     * &lt;receiver&gt; tag in an AndroidManifest.xml file
     * <p>
     * The operation Intent is delivered with two extras, a {@link Network} typed
     * extra called {@link #EXTRA_NETWORK} and a {@link NetworkRequest}
     * typed extra called {@link #EXTRA_NETWORK_REQUEST} containing
     * the original requests parameters.  It is important to create a new,
     * {@link NetworkCallback} based request before completing the processing of the
     * Intent to reserve the network or it will be released shortly after the Intent
     * is processed.
     * <p>
     * If there is already a request for this Intent registered (with the equality of
     * two Intents defined by {@link Intent#filterEquals}), then it will be removed and
     * replaced by this one, effectively releasing the previous {@link NetworkRequest}.
     * <p>
     * The request may be released normally by calling
     * {@link #releaseNetworkRequest(android.app.PendingIntent)}.
     * <p>It is presently unsupported to request a network with either
     * {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     * {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}
     * as these {@code NetworkCapabilities} represent states that a particular
     * network may never attain, and whether a network will attain these states
     * is unknown prior to bringing up the network so the framework does not
     * know how to go about satisfing a request with these capabilities.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param operation Action to perform when the network is available (corresponds
     *                  to the {@link NetworkCallback#onAvailable} call.  Typically
     *                  comes from {@link PendingIntent#getBroadcast}. Cannot be null.
     * @throws IllegalArgumentException if {@code request} contains either
     *         {@link NetworkCapabilities#NET_CAPABILITY_VALIDATED} or
     *         {@link NetworkCapabilities#NET_CAPABILITY_CAPTIVE_PORTAL}.
     */
    public void requestNetwork(NetworkRequest request, PendingIntent operation) {
        checkPendingIntent(operation);
        try {
            mService.pendingRequestForNetwork(request.networkCapabilities, operation);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes a request made via {@link #requestNetwork(NetworkRequest, android.app.PendingIntent)}
     * <p>
     * This method has the same behavior as
     * {@link #unregisterNetworkCallback(android.app.PendingIntent)} with respect to
     * releasing network resources and disconnecting.
     *
     * @param operation A PendingIntent equal (as defined by {@link Intent#filterEquals}) to the
     *                  PendingIntent passed to
     *                  {@link #requestNetwork(NetworkRequest, android.app.PendingIntent)} with the
     *                  corresponding NetworkRequest you'd like to remove. Cannot be null.
     */
    public void releaseNetworkRequest(PendingIntent operation) {
        checkPendingIntent(operation);
        try {
            mService.releasePendingNetworkRequest(operation);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void checkPendingIntent(PendingIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("PendingIntent cannot be null.");
        }
    }

    /**
     * Registers to receive notifications about all networks which satisfy the given
     * {@link NetworkRequest}.  The callbacks will continue to be called until
     * either the application exits or link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} that the system will call as suitable
     *                        networks change state.
     *                        The callback is invoked on the default internal Handler.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback networkCallback) {
        registerNetworkCallback(request, networkCallback, getDefaultHandler());
    }

    /**
     * Registers to receive notifications about all networks which satisfy the given
     * {@link NetworkRequest}.  The callbacks will continue to be called until
     * either the application exits or link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param request {@link NetworkRequest} describing this request.
     * @param networkCallback The {@link NetworkCallback} that the system will call as suitable
     *                        networks change state.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(
            NetworkRequest request, NetworkCallback networkCallback, Handler handler) {
        CallbackHandler cbHandler = new CallbackHandler(handler);
        NetworkCapabilities nc = request.networkCapabilities;
        sendRequestForNetwork(nc, networkCallback, 0, LISTEN, TYPE_NONE, cbHandler);
    }

    /**
     * Registers a PendingIntent to be sent when a network is available which satisfies the given
     * {@link NetworkRequest}.
     *
     * This function behaves identically to the version that takes a NetworkCallback, but instead
     * of {@link NetworkCallback} a {@link PendingIntent} is used.  This means
     * the request may outlive the calling application and get called back when a suitable
     * network is found.
     * <p>
     * The operation is an Intent broadcast that goes to a broadcast receiver that
     * you registered with {@link Context#registerReceiver} or through the
     * &lt;receiver&gt; tag in an AndroidManifest.xml file
     * <p>
     * The operation Intent is delivered with two extras, a {@link Network} typed
     * extra called {@link #EXTRA_NETWORK} and a {@link NetworkRequest}
     * typed extra called {@link #EXTRA_NETWORK_REQUEST} containing
     * the original requests parameters.
     * <p>
     * If there is already a request for this Intent registered (with the equality of
     * two Intents defined by {@link Intent#filterEquals}), then it will be removed and
     * replaced by this one, effectively releasing the previous {@link NetworkRequest}.
     * <p>
     * The request may be released normally by calling
     * {@link #unregisterNetworkCallback(android.app.PendingIntent)}.
     * @param request {@link NetworkRequest} describing this request.
     * @param operation Action to perform when the network is available (corresponds
     *                  to the {@link NetworkCallback#onAvailable} call.  Typically
     *                  comes from {@link PendingIntent#getBroadcast}. Cannot be null.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerNetworkCallback(NetworkRequest request, PendingIntent operation) {
        checkPendingIntent(operation);
        try {
            mService.pendingListenForNetwork(request.networkCapabilities, operation);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Registers to receive notifications about changes in the system default network. The callbacks
     * will continue to be called until either the application exits or
     * {@link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param networkCallback The {@link NetworkCallback} that the system will call as the
     *                        system default network changes.
     *                        The callback is invoked on the default internal Handler.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerDefaultNetworkCallback(NetworkCallback networkCallback) {
        registerDefaultNetworkCallback(networkCallback, getDefaultHandler());
    }

    /**
     * Registers to receive notifications about changes in the system default network. The callbacks
     * will continue to be called until either the application exits or
     * {@link #unregisterNetworkCallback(NetworkCallback)} is called.
     *
     * @param networkCallback The {@link NetworkCallback} that the system will call as the
     *                        system default network changes.
     * @param handler {@link Handler} to specify the thread upon which the callback will be invoked.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public void registerDefaultNetworkCallback(NetworkCallback networkCallback, Handler handler) {
        // This works because if the NetworkCapabilities are null,
        // ConnectivityService takes them from the default request.
        //
        // Since the capabilities are exactly the same as the default request's
        // capabilities, this request is guaranteed, at all times, to be
        // satisfied by the same network, if any, that satisfies the default
        // request, i.e., the system default network.
        CallbackHandler cbHandler = new CallbackHandler(handler);
        sendRequestForNetwork(null, networkCallback, 0, REQUEST, TYPE_NONE, cbHandler);
    }

    /**
     * Requests bandwidth update for a given {@link Network} and returns whether the update request
     * is accepted by ConnectivityService. Once accepted, ConnectivityService will poll underlying
     * network connection for updated bandwidth information. The caller will be notified via
     * {@link ConnectivityManager.NetworkCallback} if there is an update. Notice that this
     * method assumes that the caller has previously called
     * {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} to listen for network
     * changes.
     *
     * @param network {@link Network} specifying which network you're interested.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     */
    public boolean requestBandwidthUpdate(Network network) {
        try {
            return mService.requestBandwidthUpdate(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters callbacks about and possibly releases networks originating from
     * {@link #requestNetwork(NetworkRequest, NetworkCallback)} and
     * {@link #registerNetworkCallback(NetworkRequest, NetworkCallback)} calls.
     * If the given {@code NetworkCallback} had previously been used with
     * {@code #requestNetwork}, any networks that had been connected to only to satisfy that request
     * will be disconnected.
     *
     * @param networkCallback The {@link NetworkCallback} used when making the request.
     */
    public void unregisterNetworkCallback(NetworkCallback networkCallback) {
        if (networkCallback == null || networkCallback.networkRequest == null ||
                networkCallback.networkRequest.requestId == REQUEST_ID_UNSET) {
            throw new IllegalArgumentException("Invalid NetworkCallback");
        }
        try {
            // CallbackHandler will release callback when receiving CALLBACK_RELEASED.
            mService.releaseNetworkRequest(networkCallback.networkRequest);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters a callback previously registered via
     * {@link #registerNetworkCallback(NetworkRequest, android.app.PendingIntent)}.
     *
     * @param operation A PendingIntent equal (as defined by {@link Intent#filterEquals}) to the
     *                  PendingIntent passed to
     *                  {@link #registerNetworkCallback(NetworkRequest, android.app.PendingIntent)}.
     *                  Cannot be null.
     */
    public void unregisterNetworkCallback(PendingIntent operation) {
        releaseNetworkRequest(operation);
    }

    /**
     * Informs the system whether it should switch to {@code network} regardless of whether it is
     * validated or not. If {@code accept} is true, and the network was explicitly selected by the
     * user (e.g., by selecting a Wi-Fi network in the Settings app), then the network will become
     * the system default network regardless of any other network that's currently connected. If
     * {@code always} is true, then the choice is remembered, so that the next time the user
     * connects to this network, the system will switch to it.
     *
     * @param network The network to accept.
     * @param accept Whether to accept the network even if unvalidated.
     * @param always Whether to remember this choice in the future.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public void setAcceptUnvalidated(Network network, boolean accept, boolean always) {
        try {
            mService.setAcceptUnvalidated(network, accept, always);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Informs the system to penalize {@code network}'s score when it becomes unvalidated. This is
     * only meaningful if the system is configured not to penalize such networks, e.g., if the
     * {@code config_networkAvoidBadWifi} configuration variable is set to 0 and the {@code
     * NETWORK_AVOID_BAD_WIFI setting is unset}.
     *
     * @param network The network to accept.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public void setAvoidUnvalidated(Network network) {
        try {
            mService.setAvoidUnvalidated(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests that the system open the captive portal app on the specified network.
     *
     * @param network The network to log into.
     *
     * 
     */
    @RequiresPermission(android.Manifest.permission.CONNECTIVITY_INTERNAL)
    public void startCaptivePortalApp(Network network) {
        try {
            mService.startCaptivePortalApp(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * It is acceptable to briefly use multipath data to provide seamless connectivity for
     * time-sensitive user-facing operations when the system default network is temporarily
     * unresponsive. The amount of data should be limited (less than one megabyte for every call to
     * this method), and the operation should be infrequent to ensure that data usage is limited.
     *
     * An example of such an operation might be a time-sensitive foreground activity, such as a
     * voice command, that the user is performing while walking out of range of a Wi-Fi network.
     */
    public static final int MULTIPATH_PREFERENCE_HANDOVER = 1 << 0;

    /**
     * It is acceptable to use small amounts of multipath data on an ongoing basis to provide
     * a backup channel for traffic that is primarily going over another network.
     *
     * An example might be maintaining backup connections to peers or servers for the purpose of
     * fast fallback if the default network is temporarily unresponsive or disconnects. The traffic
     * on backup paths should be negligible compared to the traffic on the main path.
     */
    public static final int MULTIPATH_PREFERENCE_RELIABILITY = 1 << 1;

    /**
     * It is acceptable to use metered data to improve network latency and performance.
     */
    public static final int MULTIPATH_PREFERENCE_PERFORMANCE = 1 << 2;

    /**
     * Return value to use for unmetered networks. On such networks we currently set all the flags
     * to true.
     * 
     */
    public static final int MULTIPATH_PREFERENCE_UNMETERED =
            MULTIPATH_PREFERENCE_HANDOVER |
            MULTIPATH_PREFERENCE_RELIABILITY |
            MULTIPATH_PREFERENCE_PERFORMANCE;

    /**  */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            MULTIPATH_PREFERENCE_HANDOVER,
            MULTIPATH_PREFERENCE_RELIABILITY,
            MULTIPATH_PREFERENCE_PERFORMANCE,
    })
    public @interface MultipathPreference {
    }

    /**
     * Provides a hint to the calling application on whether it is desirable to use the
     * multinetwork APIs (e.g., {@link Network#openConnection}, {@link Network#bindSocket}, etc.)
     * for multipath data transfer on this network when it is not the system default network.
     * Applications desiring to use multipath network protocols should call this method before
     * each such operation.
     *
     * @param network The network on which the application desires to use multipath data.
     *                If {@code null}, this method will return the a preference that will generally
     *                apply to metered networks.
     * @return a bitwise OR of zero or more of the  {@code MULTIPATH_PREFERENCE_*} constants.
     */
    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    public @MultipathPreference int getMultipathPreference(Network network) {
        try {
            return mService.getMultipathPreference(network);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Resets all connectivity manager settings back to factory defaults.
     * 
     */
    public void factoryReset() {
        try {
            mService.factoryReset();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Binds the current process to {@code network}.  All Sockets created in the future
     * (and not explicitly bound via a bound SocketFactory from
     * {@link Network#getSocketFactory() Network.getSocketFactory()}) will be bound to
     * {@code network}.  All host name resolutions will be limited to {@code network} as well.
     * Note that if {@code network} ever disconnects, all Sockets created in this way will cease to
     * work and all host name resolutions will fail.  This is by design so an application doesn't
     * accidentally use Sockets it thinks are still bound to a particular {@link Network}.
     * To clear binding pass {@code null} for {@code network}.  Using individually bound
     * Sockets created by Network.getSocketFactory().createSocket() and
     * performing network-specific host name resolutions via
     * {@link Network#getAllByName Network.getAllByName} is preferred to calling
     * {@code bindProcessToNetwork}.
     *
     * @param network The {@link Network} to bind the current process to, or {@code null} to clear
     *                the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     */
    public boolean bindProcessToNetwork(Network network) {
        // Forcing callers to call thru non-static function ensures ConnectivityManager
        // instantiated.
        return setProcessDefaultNetwork(network);
    }

    /**
     * Binds the current process to {@code network}.  All Sockets created in the future
     * (and not explicitly bound via a bound SocketFactory from
     * {@link Network#getSocketFactory() Network.getSocketFactory()}) will be bound to
     * {@code network}.  All host name resolutions will be limited to {@code network} as well.
     * Note that if {@code network} ever disconnects, all Sockets created in this way will cease to
     * work and all host name resolutions will fail.  This is by design so an application doesn't
     * accidentally use Sockets it thinks are still bound to a particular {@link Network}.
     * To clear binding pass {@code null} for {@code network}.  Using individually bound
     * Sockets created by Network.getSocketFactory().createSocket() and
     * performing network-specific host name resolutions via
     * {@link Network#getAllByName Network.getAllByName} is preferred to calling
     * {@code setProcessDefaultNetwork}.
     *
     * @param network The {@link Network} to bind the current process to, or {@code null} to clear
     *                the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     * @deprecated This function can throw {@link IllegalStateException}.  Use
     *             {@link #bindProcessToNetwork} instead.  {@code bindProcessToNetwork}
     *             is a direct replacement.
     */
    @Deprecated
    public static boolean setProcessDefaultNetwork(Network network) {
        return false;
    }

    /**
     * Returns the {@link Network} currently bound to this process via
     * {@link #bindProcessToNetwork}, or {@code null} if no {@link Network} is explicitly bound.
     *
     * @return {@code Network} to which this process is bound, or {@code null}.
     */
    public Network getBoundNetworkForProcess() {
        // Forcing callers to call thru non-static function ensures ConnectivityManager
        // instantiated.
        return getProcessDefaultNetwork();
    }

    /**
     * Returns the {@link Network} currently bound to this process via
     * {@link #bindProcessToNetwork}, or {@code null} if no {@link Network} is explicitly bound.
     *
     * @return {@code Network} to which this process is bound, or {@code null}.
     * @deprecated Using this function can lead to other functions throwing
     *             {@link IllegalStateException}.  Use {@link #getBoundNetworkForProcess} instead.
     *             {@code getBoundNetworkForProcess} is a direct replacement.
     */
    @Deprecated
    public static Network getProcessDefaultNetwork() {
        int netId = NetworkUtils.getBoundNetworkForProcess();
        if (netId == NETID_UNSET) return null;
        return new Network(netId);
    }

    private void unsupportedStartingFrom(int version) {
        if (Process.myUid() == Process.SYSTEM_UID) {
            // The getApplicationInfo() call we make below is not supported in system context, and
            // we want to allow the system to use these APIs anyway.
            return;
        }

        if (mContext.getApplicationInfo().targetSdkVersion >= version) {
            throw new UnsupportedOperationException(
                    "This method is not supported in target SDK version " + version + " and above");
        }
    }

    // Checks whether the calling app can use the legacy routing API (startUsingNetworkFeature,
    // stopUsingNetworkFeature, requestRouteToHost), and if not throw UnsupportedOperationException.
    // TODO: convert the existing system users (Tethering, GnssLocationProvider) to the new APIs and
    // remove these exemptions. Note that this check is not secure, and apps can still access these
    // functions by accessing ConnectivityService directly. However, it should be clear that doing
    // so is unsupported and may break in the future. http://b/22728205
    private void checkLegacyRoutingApiAccess() {
        if (mContext.checkCallingOrSelfPermission("com.android.permission.INJECT_OMADM_SETTINGS")
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        unsupportedStartingFrom(VERSION_CODES.M);
    }

    /**
     * Binds host resolutions performed by this process to {@code network}.
     * {@link #bindProcessToNetwork} takes precedence over this setting.
     *
     * @param network The {@link Network} to bind host resolutions from the current process to, or
     *                {@code null} to clear the current binding.
     * @return {@code true} on success, {@code false} if the {@link Network} is no longer valid.
     * 
     * @deprecated This is strictly for legacy usage to support {@link #startUsingNetworkFeature}.
     */
    @Deprecated
    public static boolean setProcessDefaultNetworkForHostResolution(Network network) {
        return NetworkUtils.bindProcessToNetworkForHostResolution(
                network == null ? NETID_UNSET : network.netId);
    }

    /**
     * Device is not restricting metered network activity while application is running on
     * background.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;

    /**
     * Device is restricting metered network activity while application is running on background,
     * but application is allowed to bypass it.
     * <p>
     * In this state, application should take action to mitigate metered network access.
     * For example, a music streaming application should switch to a low-bandwidth bitrate.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2;

    /**
     * Device is restricting metered network activity while application is running on background.
     * <p>
     * In this state, application should not try to use the network while running on background,
     * because it would be denied.
     */
    public static final int RESTRICT_BACKGROUND_STATUS_ENABLED = 3;

    /**
     * A change in the background metered network activity restriction has occurred.
     * <p>
     * Applications should call {@link #getRestrictBackgroundStatus()} to check if the restriction
     * applies to them.
     * <p>
     * This is only sent to registered receivers, not manifest receivers.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_RESTRICT_BACKGROUND_CHANGED =
            "android.net.conn.RESTRICT_BACKGROUND_CHANGED";

    /**  */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = false, value = {
            RESTRICT_BACKGROUND_STATUS_DISABLED,
            RESTRICT_BACKGROUND_STATUS_WHITELISTED,
            RESTRICT_BACKGROUND_STATUS_ENABLED,
    })
    public @interface RestrictBackgroundStatus {
    }

    private INetworkPolicyManager getNetworkPolicyManager() {
        synchronized (this) {
            if (mNPManager != null) {
                return mNPManager;
            }
            mNPManager = INetworkPolicyManager.Stub.asInterface(ServiceManager
                    .getService(Context.NETWORK_POLICY_SERVICE));
            return mNPManager;
        }
    }

    /**
     * Determines if the calling application is subject to metered network restrictions while
     * running on background.
     *
     * @return {@link #RESTRICT_BACKGROUND_STATUS_DISABLED},
     * {@link #RESTRICT_BACKGROUND_STATUS_ENABLED},
     * or {@link #RESTRICT_BACKGROUND_STATUS_WHITELISTED}
     */
    public @RestrictBackgroundStatus int getRestrictBackgroundStatus() {
        try {
            return getNetworkPolicyManager().getRestrictBackgroundByCaller();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A holder class for debug info (mapping CALLBACK values to field names). This is stored
     * in a holder for two reasons:
     * 1) The reflection necessary to establish the map can't be run at compile-time. Thus, this
     *    code will make the enclosing class not compile-time initializeable, deferring its
     *    initialization to zygote startup. This leads to dirty (but shared) memory.
     *    As this is debug info, use a holder that isn't initialized by default. This way the map
     *    will be created on demand, while ConnectivityManager can be compile-time initialized.
     * 2) Static initialization is still preferred for its strong thread safety guarantees without
     *    requiring a lock.
     */
    private static class NoPreloadHolder {
        public static final SparseArray<String> sMagicDecoderRing = MessageUtils.findMessageNames(
                new Class[]{ConnectivityManager.class}, new String[]{"CALLBACK_"});
    }

    static {
        // When debug is enabled, aggressively initialize the holder by touching the field (which
        // will guarantee static initialization).
        if (CallbackHandler.DBG) {
            Object dummy = NoPreloadHolder.sMagicDecoderRing;
        }
    }

    private static final String whatToString(int what) {
        return NoPreloadHolder.sMagicDecoderRing.get(what, Integer.toString(what));
    }
}
