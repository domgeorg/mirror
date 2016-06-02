/*
 * Copyright 2016 Johann Reyes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vaporwarecorp.mirror.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.NetworkInfo.State.CONNECTED;
import static android.net.wifi.WifiManager.WIFI_STATE_DISABLED;

public class NetworkUtil {
// -------------------------- STATIC METHODS --------------------------

    @SuppressWarnings("ResourceType")
    public static boolean isNetworkAvailable(final Context context) {
        final WifiManager manager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        if (manager != null) {
            if (manager.isWifiEnabled()) {
                return isNetworkConnected(context);
            } else {
                if (manager.getWifiState() == WIFI_STATE_DISABLED) {
                    manager.setWifiEnabled(true);
                }
            }
        }
        return false;
    }

    public static boolean isNetworkConnected(final Context context) {
        final ConnectivityManager manager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (manager != null) {
            final Network[] networks = manager.getAllNetworks();
            if (networks != null) {
                for (final Network network : networks) {
                    final NetworkInfo info = manager.getNetworkInfo(network);
                    if (info.getState() == CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
