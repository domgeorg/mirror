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
package com.vaporwarecorp.mirror.feature.main;

import android.content.Intent;
import com.robopupu.api.dependency.D;
import com.robopupu.api.dependency.Provides;
import com.robopupu.api.mvp.View;
import com.robopupu.api.plugin.Plug;
import com.robopupu.api.plugin.Plugin;
import com.robopupu.api.plugin.PluginBus;
import com.robopupu.api.util.Params;
import com.vaporwarecorp.mirror.app.MirrorAppScope;
import com.vaporwarecorp.mirror.component.AppManager;
import com.vaporwarecorp.mirror.component.EventManager;
import com.vaporwarecorp.mirror.component.WebAuthentication;
import com.vaporwarecorp.mirror.event.ForecastEvent;
import com.vaporwarecorp.mirror.event.GreetEvent;
import com.vaporwarecorp.mirror.event.UserInRangeEvent;
import com.vaporwarecorp.mirror.event.UserOutOfRangeEvent;
import com.vaporwarecorp.mirror.feature.MainFeature;
import com.vaporwarecorp.mirror.feature.common.MirrorManager;
import com.vaporwarecorp.mirror.feature.common.presenter.AbstractMirrorFeaturePresenter;
import com.vaporwarecorp.mirror.feature.greet.GreetPresenter;
import com.vaporwarecorp.mirror.service.WebServerService;
import com.vaporwarecorp.mirror.util.PermissionUtil;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import timber.log.Timber;

import java.util.List;

import static com.vaporwarecorp.mirror.app.Constants.ACTION.*;
import static com.vaporwarecorp.mirror.event.GreetEvent.TYPE_GOODBYE;
import static com.vaporwarecorp.mirror.event.GreetEvent.TYPE_WELCOME;
import static com.vaporwarecorp.mirror.feature.greet.GreetPresenter.GREET_TYPE;
import static solid.stream.Stream.stream;

@Plugin
@Provides(MainPresenter.class)
public class MainPresenterImpl extends AbstractMirrorFeaturePresenter<MainView> implements MainPresenter {
// ------------------------------ FIELDS ------------------------------

    @Plug
    AppManager mAppManager;
    @Plug
    EventManager mEventManager;
    @Plug
    MainFeature mFeature;
    @Plug
    MainView mView;

    private boolean mLocationChecked;
    private boolean mManagersInitialized;
    private boolean mManagersRunning;
    private boolean mNetworkConnectionChecked;
    private boolean mPermissionsChecked;
    private List<WebAuthentication> mWebAuthentications;
    private boolean mWebAuthenticationsChecked;

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface MainPresenter ---------------------

    @Override
    public void onViewResult(int requestCode, int resultCode, Intent data) {
        stream(PluginBus.getPlugs(WebAuthentication.class))
                .forEach(v -> v.onAuthenticationResult(requestCode, resultCode, data));
    }

    @Override
    public void tryToStart() {
        if (!mPermissionsChecked) {
            checkPermissions();
            return;
        }
        if (!mNetworkConnectionChecked) {
            checkNetworkConnection();
            return;
        }
        if (!mLocationChecked) {
            checkLocation();
            return;
        }
        if (!mWebAuthenticationsChecked) {
            checkWebAuthentications();
            return;
        }
        if (!mManagersInitialized) {
            initializeManagers();
            return;
        }
        if (!mManagersRunning) {
            managerStart();
        }
    }

// --------------------- Interface ViewObserver ---------------------

    @Override
    public void onViewResume(View view) {
        super.onViewResume(view);
        tryToStart();
    }

    @Override
    public void onViewPause(View view) {
        super.onViewPause(view);
        if (mManagersInitialized) {
            managerStop();
        }
    }

    @Override
    public void onViewDestroy(View view) {
        webServerStop();
        mManagersRunning = false;
        mManagersInitialized = false;
        mWebAuthenticationsChecked = false;
        mLocationChecked = false;
        mNetworkConnectionChecked = false;
        mPermissionsChecked = false;
        super.onViewDestroy(view);
    }

// -------------------------- OTHER METHODS --------------------------

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onEvent(UserInRangeEvent event) {
        mFeature.showPresenter(GreetPresenter.class, new Params(GREET_TYPE, TYPE_WELCOME));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onEvent(ForecastEvent event) {
        mView.setForecast(event.getForecast());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onEvent(UserOutOfRangeEvent event) {
        mFeature.hideView();
        mFeature.showPresenter(GreetPresenter.class, new Params(GREET_TYPE, TYPE_GOODBYE));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    @SuppressWarnings("unused")
    public void onEvent(GreetEvent event) {
        if (TYPE_WELCOME.equals(event.getType())) {
            mFeature.displayView();
            managerResume();
        } else {
            managerPause();
            mFeature.hideCurrentPresenter();
        }
    }

    @Override
    protected MainView getViewPlug() {
        return mView;
    }

    private void checkLocation() {
        delay(l -> {
            mLocationChecked = mAppManager.isLocationAvailable();
            tryToStart();
        }, 5);
    }

    private void checkNetworkConnection() {
        delay(l -> {
            mNetworkConnectionChecked = mAppManager.isNetworkAvailable();
            tryToStart();
        }, 5);
    }

    private void checkPermissions() {
        Timber.d("checking permissions");
        final List<String> neededPermissions = PermissionUtil.checkPermissions(mView.activity());
        if (neededPermissions.isEmpty()) {
            webServerStart();
            stream(D.getAll(MirrorAppScope.class, WebAuthentication.class, null)).forEach(PluginBus::plug);
            mWebAuthentications = PluginBus.getPlugs(WebAuthentication.class);
            mPermissionsChecked = true;
            tryToStart();
        } else {
            PermissionUtil.requestPermissions(mView.activity(), neededPermissions);
        }
    }

    private void checkWebAuthentications() {
        Timber.d("Got %s web authentications to do", mWebAuthentications.size());
        if (!mWebAuthentications.isEmpty()) {
            final WebAuthentication webAuthentication = mWebAuthentications.get(0);
            webAuthentication.isAuthenticated(isAuthenticated -> {
                mWebAuthentications.remove(0);
                if (isAuthenticated) {
                    tryToStart();
                } else {
                    webAuthentication.doAuthentication();
                }
            });
        } else {
            mWebAuthenticationsChecked = true;
            tryToStart();
        }
    }

    /**
     * Start the MirrorManagers
     */
    private void initializeManagers() {
        stream(D.getAll(MirrorAppScope.class, MirrorManager.class, null))
                .filter(manager -> !PluginBus.isPlugged(manager))
                .forEach(PluginBus::plug);
        webServerRefresh();
        mManagersInitialized = true;
        tryToStart();
    }

    private void managerPause() {
        Timber.d("managerPause()");
        stream(PluginBus.getPlugs(MirrorManager.class)).forEach(MirrorManager::onFeaturePause);
    }

    private void managerResume() {
        Timber.d("managerResume()");
        stream(PluginBus.getPlugs(MirrorManager.class)).forEach(MirrorManager::onFeatureResume);
    }

    private void managerStart() {
        Timber.d("managerStart()");
        mManagersRunning = true;
        mEventManager.register(this);
        stream(PluginBus.getPlugs(MirrorManager.class)).forEach(MirrorManager::onFeatureStart);
    }

    private void managerStop() {
        Timber.d("managerStop()");
        stream(PluginBus.getPlugs(MirrorManager.class)).forEach(MirrorManager::onFeatureStop);
        mEventManager.unregister(this);
        mManagersRunning = false;
    }

    /**
     * Refresh the web server
     */
    private void webServerRefresh() {
        mAppManager.startService(WebServerService.class, WEB_SERVER_SERVICE_REFRESH);
    }

    /**
     * Start the web server
     */
    private void webServerStart() {
        mAppManager.startService(WebServerService.class, WEB_SERVER_SERVICE_START);
    }

    /**
     * Stop the web server
     */
    private void webServerStop() {
        mAppManager.startService(WebServerService.class, WEB_SERVER_SERVICE_STOP);
    }
}
