package com.transistorsoft.cordova.bggeo;

import com.transistorsoft.locationmanager.adapter.BackgroundGeolocation;
import com.transistorsoft.locationmanager.adapter.TSConfig;
import com.transistorsoft.locationmanager.adapter.callback.*;
import com.transistorsoft.locationmanager.config.TransistorAuthorizationToken;
import com.transistorsoft.locationmanager.config.TSAuthorization;
import com.transistorsoft.locationmanager.data.LocationModel;
import com.transistorsoft.locationmanager.data.SQLQuery;
import com.transistorsoft.locationmanager.device.DeviceSettingsRequest;
import com.transistorsoft.locationmanager.device.DeviceInfo;
import com.transistorsoft.locationmanager.event.ActivityChangeEvent;
import com.transistorsoft.locationmanager.event.AuthorizationEvent;
import com.transistorsoft.locationmanager.event.ConnectivityChangeEvent;
import com.transistorsoft.locationmanager.event.GeofenceEvent;
import com.transistorsoft.locationmanager.event.GeofencesChangeEvent;
import com.transistorsoft.locationmanager.event.HeartbeatEvent;
import com.transistorsoft.locationmanager.event.LocationProviderChangeEvent;
import com.transistorsoft.locationmanager.event.TerminateEvent;
import com.transistorsoft.locationmanager.geofence.TSGeofence;
import com.transistorsoft.locationmanager.http.HttpResponse;
import com.transistorsoft.locationmanager.http.HttpService;
import com.transistorsoft.locationmanager.location.TSCurrentPositionRequest;
import com.transistorsoft.locationmanager.location.TSLocation;
import com.transistorsoft.locationmanager.location.TSWatchPositionRequest;
import com.transistorsoft.locationmanager.logger.TSLog;
import com.transistorsoft.locationmanager.scheduler.ScheduleEvent;
import com.transistorsoft.locationmanager.scheduler.TSScheduleManager;
import com.transistorsoft.locationmanager.util.Sensors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.transistorsoft.xms.g.common.ExtensionApiAvailability;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class CDVBackgroundGeolocation extends CordovaPlugin {
    private static final String TAG = "TSLocationManager";
    private static final String HEADLESS_JOB_SERVICE_CLASS = "BackgroundGeolocationHeadlessTask";

    public static final int REQUEST_ACTION_START = 1;
    public static final int REQUEST_ACTION_GET_CURRENT_POSITION = 2;
    public static final int REQUEST_ACTION_START_GEOFENCES = 3;
    public static final int REQUEST_ACTION_WATCH_POSITION = 4;
    public static final int REQUEST_ACTION_CONFIGURE = 5;

    /**
     * Timeout in millis for a getCurrentPosition request to give up.
     * TODO make configurable.
     */
    public static final String ACTION_RESET             = "reset";
    public static final String ACTION_FINISH            = "finish";
    public static final String ACTION_START_BACKGROUND_TASK = "startBackgroundTask";
    public static final String ACTION_ERROR             = "error";
    public static final String ACTION_CONFIGURE         = "configure";
    public static final String ACTION_READY             = "ready";
    public static final String ACTION_ADD_MOTION_CHANGE_LISTENER    = "addMotionChangeListener";
    public static final String ACTION_ADD_LOCATION_LISTENER = "addLocationListener";
    public static final String ACTION_ADD_HEARTBEAT_LISTENER = "addHeartbeatListener";
    public static final String ACTION_ADD_ACTIVITY_CHANGE_LISTENER = "addActivityChangeListener";
    public static final String ACTION_ADD_PROVIDER_CHANGE_LISTENER = "addProviderChangeListener";
    public static final String ACTION_ADD_SCHEDULE_LISTENER = "addScheduleListener";
    public static final String ACTION_REMOVE_LISTENERS  = "removeListeners";
    public static final String ACTION_ADD_GEOFENCE_LISTENER = "addGeofenceListener";
    public static final String ACTION_ADD_GEOFENCESCHANGE_LISTENER = "addGeofencesChangeListener";
    public static final String ACTION_ADD_CONNECTIVITYCHANGE_LISTENER = "addConnectivityChangeListener";
    public static final String ACTION_ADD_ENABLEDCHANGE_LISTENER = "addEnabledChangeListener";
    public static final String ACTION_ADD_POWERSAVECHANGE_LISTENER = "addPowerSaveChangeListener";
    public static final String ACTION_ADD_NOTIFICATIONACTION_LISTENER = "addNotificationActionListener";
    public static final String ACTION_ADD_AUTHORIZATION_LISTENER = "addAuthorizationListener";
    public static final String ACTION_REQUEST_TEMPORARY_FULL_ACCURACY = "requestTemporaryFullAccuracy";

    public static final String ACTION_PLAY_SOUND        = "playSound";
    public static final String ACTION_GET_STATE         = "getState";
    public static final String ACTION_ADD_HTTP_LISTENER = "addHttpListener";
    public static final String ACTION_GET_LOG           = "getLog";
    public static final String ACTION_EMAIL_LOG         = "emailLog";
    public static final String ACTION_START_SCHEDULE    = "startSchedule";
    public static final String ACTION_STOP_SCHEDULE     = "stopSchedule";
    public static final String ACTION_LOG               = "log";

    private static final String ACTION_REQUEST_SETTINGS  = "requestSettings";
    private static final String ACTION_SHOW_SETTINGS     = "showSettings";

    private boolean mReady;
    private List<TSCallback> locationAuthorizationCallbacks = new ArrayList<TSCallback>();
    private List<CallbackContext> watchPositionCallbacks = new ArrayList<CallbackContext>();
    private List<CordovaCallback> cordovaCallbacks = new ArrayList<CordovaCallback>();

    @Override
    protected void pluginInitialize() {
        mReady = false;
        initializeLocationManager();
    }

    private void initializeLocationManager() {
        Activity activity   = cordova.getActivity();

        TSConfig config = TSConfig.getInstance(activity.getApplicationContext());
        config.useCLLocationAccuracy(true);
        // Ensure HeadlessJobService is set.
        config.updateWithBuilder()
            .setHeadlessJobService(getClass().getPackage().getName() + "." + HEADLESS_JOB_SERVICE_CLASS)
            .commit();

        BackgroundGeolocation adapter = getAdapter();
        adapter.setActivity(activity);

        adapter.onPlayServicesConnectError((new TSPlayServicesConnectErrorCallback() {
            @Override
            public void onPlayServicesConnectError(int errorCode) {
                handlePlayServicesConnectError(errorCode);
            }
        }));
    }

    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "$ " + action + "()");

        Boolean result      = false;

        if (BackgroundGeolocation.ACTION_START.equalsIgnoreCase(action)) {
            result      = true;
            start(callbackContext);
        } else if (ACTION_START_SCHEDULE.equalsIgnoreCase(action)) {
            result = true;
            startSchedule(callbackContext);
        } else if (ACTION_STOP_SCHEDULE.equalsIgnoreCase(action)) {
            result = true;
            stopSchedule(callbackContext);
        } else if (BackgroundGeolocation.ACTION_START_GEOFENCES.equalsIgnoreCase(action)) {
            result = true;
            startGeofences(callbackContext);
        } else if (BackgroundGeolocation.ACTION_STOP.equalsIgnoreCase(action)) {
            // No implementation to stop background-tasks with Android.  Just say "success"
            result      = true;
            stop(callbackContext);
        } else if (ACTION_START_BACKGROUND_TASK.equalsIgnoreCase(action)) {
            result = true;
            startBackgroundTask(callbackContext);
        } else if (ACTION_FINISH.equalsIgnoreCase(action)) {
            result = true;
            stopBackgroundTask(data.getInt(0), callbackContext);
        } else if (ACTION_ERROR.equalsIgnoreCase(action)) {
            result = true;
            this.onError(data.getString(1));
            callbackContext.success();
        } else if (ACTION_RESET.equalsIgnoreCase(action)) {
            result = true;
            reset(data.getJSONObject(0), callbackContext);
        } else if (ACTION_READY.equalsIgnoreCase(action)) {
            result = true;
            ready(data.getJSONObject(0), callbackContext);
        } else if (ACTION_CONFIGURE.equalsIgnoreCase(action)) {
            result = true;
            configure(data.getJSONObject(0), callbackContext);
        } else if (ACTION_REMOVE_LISTENERS.equalsIgnoreCase(action)) {
            result = true;
            removeListeners(callbackContext);
        } else if (BackgroundGeolocation.ACTION_REMOVE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            removeListener(data.getString(0), data.getString(1), callbackContext);
        } else if (ACTION_ADD_LOCATION_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addLocationListener(callbackContext);
        } else if (BackgroundGeolocation.ACTION_CHANGE_PACE.equalsIgnoreCase(action)) {
            result = true;
            TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
            if (!config.getEnabled()) {
                Log.w(TAG, "- Cannot change pace while disabled");
                callbackContext.error("Cannot #changePace while disabled");
            } else {
                changePace(callbackContext, data);
            }
        } else if (BackgroundGeolocation.ACTION_SET_CONFIG.equalsIgnoreCase(action)) {
            result = true;
            setConfig( data.getJSONObject(0), callbackContext);
        } else if (ACTION_GET_STATE.equalsIgnoreCase(action)) {
            result = true;
            callbackContext.success(getState());
        } else if (ACTION_ADD_MOTION_CHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            this.addMotionChangeListener(callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_LOCATIONS.equalsIgnoreCase(action)) {
            result = true;
            getLocations(callbackContext);
        } else if (BackgroundGeolocation.ACTION_SYNC.equalsIgnoreCase(action)) {
            result = true;
            sync(callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_ODOMETER.equalsIgnoreCase(action)) {
            result = true;
            getOdometer(callbackContext);
        } else if (BackgroundGeolocation.ACTION_SET_ODOMETER.equalsIgnoreCase(action)) {
            result = true;
            setOdometer((float) data.getDouble(0), callbackContext);
        } else if (BackgroundGeolocation.ACTION_ADD_GEOFENCE.equalsIgnoreCase(action)) {
            result = true;
            addGeofence(callbackContext, data.getJSONObject(0));
        } else if (BackgroundGeolocation.ACTION_ADD_GEOFENCES.equalsIgnoreCase(action)) {
            result = true;
            addGeofences(callbackContext, data.getJSONArray(0));
        } else if (BackgroundGeolocation.ACTION_REMOVE_GEOFENCE.equalsIgnoreCase(action)) {
            result = true;
            removeGeofence(data.getString(0), callbackContext);
        } else if (BackgroundGeolocation.ACTION_REMOVE_GEOFENCES.equalsIgnoreCase(action)) {
            result = true;
            removeGeofences(data.getJSONArray(0), callbackContext);
        } else if (ACTION_ADD_GEOFENCE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addGeofenceListener(callbackContext);
        } else if (ACTION_ADD_GEOFENCESCHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addGeofencesChangeListener(callbackContext);
        } else if (ACTION_ADD_POWERSAVECHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addPowerSaveChangeListener(callbackContext);
        } else if (ACTION_ADD_CONNECTIVITYCHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addConnectivityChangeListener(callbackContext);
        } else if (ACTION_ADD_ENABLEDCHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addEnabledChangeListener(callbackContext);
        } else if (ACTION_ADD_NOTIFICATIONACTION_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addNotificationActionListener(callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_GEOFENCES.equalsIgnoreCase(action)) {
            result = true;
            getGeofences(callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_GEOFENCE.equalsIgnoreCase(action)) {
            result = true;
            getGeofence(data.getString(0), callbackContext);
        } else if (BackgroundGeolocation.ACTION_GEOFENCE_EXISTS.equalsIgnoreCase(action)) {
            result = true;
            geofenceExists(data.getString(0), callbackContext);
        } else if (ACTION_PLAY_SOUND.equalsIgnoreCase(action)) {
            result = true;
            getAdapter().startTone(data.getString(0));
            callbackContext.success();
        } else if (BackgroundGeolocation.ACTION_GET_CURRENT_POSITION.equalsIgnoreCase(action)) {
            result = true;
            getCurrentPosition(callbackContext, data.getJSONObject(0));
        } else if (BackgroundGeolocation.ACTION_WATCH_POSITION.equalsIgnoreCase(action)) {
            result = true;
            watchPosition(callbackContext, data.getJSONObject(0));
        } else if (BackgroundGeolocation.ACTION_STOP_WATCH_POSITION.equalsIgnoreCase(action)) {
            result = true;
            stopWatchPosition(callbackContext);
        } else if (BackgroundGeolocation.ACTION_START_BACKGROUND_TASK.equalsIgnoreCase(action)) {
            // Android doesn't do background-tasks.  This is an iOS thing.  Just return a number.
            result = true;
            callbackContext.success(1);
        } else if (BackgroundGeolocation.ACTION_DESTROY_LOCATIONS.equalsIgnoreCase(action)) {
            result = true;
            destroyLocations(callbackContext);
        } else if (BackgroundGeolocation.ACTION_DESTROY_LOCATION.equalsIgnoreCase(action)) {
            result = true;
            destroyLocation(data.getString(0), callbackContext);
        } else if (ACTION_ADD_HTTP_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addHttpListener(callbackContext);
        } else if (ACTION_ADD_HEARTBEAT_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addHeartbeatListener(callbackContext);
        } else if (ACTION_ADD_ACTIVITY_CHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addActivityChangeListener(callbackContext);
        } else if (ACTION_ADD_PROVIDER_CHANGE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addProviderChangeListener(callbackContext);
        } else if (ACTION_ADD_SCHEDULE_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addScheduleListener(callbackContext);
        } else if (ACTION_ADD_AUTHORIZATION_LISTENER.equalsIgnoreCase(action)) {
            result = true;
            addAuthorizationListener(callbackContext);
        } else if (ACTION_GET_LOG.equalsIgnoreCase(action)) {
            result = true;
            getLog(data.getJSONObject(0), callbackContext);
        } else if (ACTION_EMAIL_LOG.equalsIgnoreCase(action)) {
            result = true;
            emailLog(data.getString(0), data.getJSONObject(1), callbackContext);
        } else if (TSLog.ACTION_UPLOAD_LOG.equalsIgnoreCase(action)) {
            result = true;
            uploadLog(data.getString(0), data.getJSONObject(1), callbackContext);
        } else if (BackgroundGeolocation.ACTION_INSERT_LOCATION.equalsIgnoreCase(action)) {
            result = true;
            insertLocation(data.getJSONObject(0), callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_COUNT.equalsIgnoreCase(action)) {
            result = true;
            getCount(callbackContext);
        } else if (BackgroundGeolocation.ACTION_DESTROY_LOG.equalsIgnoreCase(action)) {
            result = true;
            destroyLog(callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_SENSORS.equalsIgnoreCase(action)) {
            result = true;
            getSensors(callbackContext);
        } else if (DeviceInfo.ACTION_GET_DEVICE_INFO.equalsIgnoreCase(action)) {
            result = true;
            getDeviceInfo(callbackContext);
        } else if (BackgroundGeolocation.ACTION_IS_POWER_SAVE_MODE.equalsIgnoreCase(action)) {
            result = true;
            isPowerSaveMode(callbackContext);
        } else if (BackgroundGeolocation.ACTION_IS_IGNORING_BATTERY_OPTIMIZATIONS.equalsIgnoreCase(action)) {
            result = true;
            isIgnoringBatteryOptimizations(callbackContext);
        } else if (ACTION_REQUEST_SETTINGS.equalsIgnoreCase(action)) {
            result = true;
            requestSettings(data.getJSONObject(0), callbackContext);
        } else if (ACTION_SHOW_SETTINGS.equalsIgnoreCase(action)) {
            result = true;
            showSettings(data.getJSONObject(0), callbackContext);
        } else if (ACTION_LOG.equalsIgnoreCase(action)) {
            result = true;
            log(data, callbackContext);
        } else if (BackgroundGeolocation.ACTION_GET_PROVIDER_STATE.equalsIgnoreCase(action)) {
            result = true;
            getProviderState(callbackContext);
        } else if (BackgroundGeolocation.ACTION_REQUEST_PERMISSION.equalsIgnoreCase(action)) {
            result = true;
            requestPermission(callbackContext);
        } else if (ACTION_REQUEST_TEMPORARY_FULL_ACCURACY.equalsIgnoreCase(action)) {
            result = true;
            requestTemporaryFullAccuracy(data.getString(0), callbackContext);
        } else if (TransistorAuthorizationToken.ACTION_GET.equalsIgnoreCase(action)) {
            result = true;
            getTransistorToken(data.getString(0), data.getString(1), data.getString(2), callbackContext);
        } else if (TransistorAuthorizationToken.ACTION_DESTROY.equalsIgnoreCase(action)) {
            result = true;
            destroyTransistorToken(data.getString(0), callbackContext);
        }
        return result;
    }

    private void reset(JSONObject params, CallbackContext callbackContext) throws JSONException {
        TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
        config.reset();
        config.updateWithJSONObject(setHeadlessJobService(params));
        callbackContext.success(config.toJson());
    }
    private void ready(final JSONObject params, final CallbackContext callbackContext) throws JSONException {
        final TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());

        boolean reset = true;
        if (params.has("reset")) {
            reset = params.getBoolean("reset");
        }
        if (mReady) {
            if (reset) {
                TSLog.logger.warn(TSLog.warn("#ready already called.  Redirecting to #setConfig"));
                setConfig(params, callbackContext);
            } else {
                TSLog.logger.warn(TSLog.warn("#ready already called.  Ignored"));
                callbackContext.success(config.toJson());
            }
            return;
        }
        mReady = true;
        BackgroundGeolocation adapter = getAdapter();


        if (config.isFirstBoot()) {
            config.updateWithJSONObject(setHeadlessJobService(params));
        } else {
            if (reset) {
                config.reset();
                config.updateWithJSONObject(setHeadlessJobService(params));
            } else if (params.has(TSAuthorization.NAME)) {
                JSONObject options = params.getJSONObject(TSAuthorization.NAME);
                config.updateWithBuilder()
                        .setAuthorization(new TSAuthorization(options, false))
                        .commit();
            }
        }
        adapter.ready(new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success(config.toJson());
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }
    private void configure(final JSONObject params, final CallbackContext callbackContext) throws JSONException {
        final TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
        config.reset();
        config.updateWithJSONObject(setHeadlessJobService(params));

        getAdapter().ready(new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success(config.toJson());
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void start(final CallbackContext callbackContext) {
        final TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
        getAdapter().start(new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success(config.toJson());
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void startSchedule(CallbackContext callbackContext) {
        if (getAdapter().startSchedule()) {
            callbackContext.success();
        } else {
            callbackContext.error("Failed to start schedule.  Did you configure a #schedule?");
        }
    }

    private void stopSchedule(CallbackContext callback) {
        getAdapter().stopSchedule();
        callback.success();
    }

    private class StartGeofencesCallback implements TSCallback {
        private CallbackContext mCallbackContext;
        public StartGeofencesCallback(CallbackContext callbackContext) {
            mCallbackContext = callbackContext;
        }
        @Override public void onSuccess() { mCallbackContext.success(TSConfig.getInstance(cordova.getActivity().getApplicationContext()).toJson()); }
        @Override public void onFailure(String error) {
            mCallbackContext.error(error);
        }
    }

    private void startGeofences(final CallbackContext callback) {
        getAdapter().startGeofences(new StartGeofencesCallback(callback));
    }

    private void stop(CallbackContext callbackContext) {
        locationAuthorizationCallbacks.clear();
        getAdapter().stop(new StopCallback(callbackContext));
    }

    private class StopCallback implements TSCallback {
        private CallbackContext mCallbackContext;
        public StopCallback(CallbackContext callback) {
            mCallbackContext = callback;
        }
        @Override public void onSuccess() { mCallbackContext.success(TSConfig.getInstance(cordova.getActivity().getApplicationContext()).toJson()); }
        @Override public void onFailure(String error) {
            mCallbackContext.error(error);
        }
    }

    private void changePace(final CallbackContext callbackContext, JSONArray data) throws JSONException {
        getAdapter().changePace(data.getBoolean(0), new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success();
            }
            @Override public void onFailure(String error) { callbackContext.error(error); }
        });
    }

    private void getLocations(final CallbackContext callbackContext) {
        getAdapter().getLocations(new TSGetLocationsCallback() {
            @Override public void onSuccess(List<LocationModel> locations) {
                try {
                    JSONArray data = new JSONArray();
                    for (LocationModel location : locations) {
                        data.put(location.json);
                    }
                    JSONObject params = new JSONObject();
                    params.put("locations", data);
                    callbackContext.success(params);
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                    e.printStackTrace();
                }
            }
            @Override public void onFailure(Integer error) {
                callbackContext.error(error);
            }
        });
    }

    private void getCount(final CallbackContext callbackContext) {
        getAdapter().getCount(new TSGetCountCallback() {
            @Override public void onSuccess(Integer count) {
                callbackContext.success(count);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void sync(final CallbackContext callbackContext) {
        getAdapter().sync(new TSSyncCallback() {
            @Override public void onSuccess(List<LocationModel> records) {
                try {
                    JSONArray data = new JSONArray();
                    for (LocationModel location : records) {
                        data.put(location.json);
                    }
                    JSONObject params = new JSONObject();
                    params.put("locations", data);
                    callbackContext.success(params);
                } catch (JSONException e) {
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void getCurrentPosition(final CallbackContext callbackContext, JSONObject options) throws JSONException {
        TSCurrentPositionRequest.Builder builder = new TSCurrentPositionRequest.Builder(cordova.getActivity().getApplicationContext());

        builder.setCallback(new TSLocationCallback() {
            @Override public void onLocation(TSLocation location) {
                try {
                    callbackContext.success(location.toJson());
                } catch (JSONException e) {
                    TSLog.logger.error(e.getMessage(), e);
                }
            }
            @Override public void onError(Integer error) { callbackContext.error(error); }
        });

        if (options.has("samples"))         { builder.setSamples(options.getInt("samples")); }
        if (options.has("extras"))          { builder.setExtras(options.getJSONObject("extras")); }
        if (options.has("persist"))         { builder.setPersist(options.getBoolean("persist")); }
        if (options.has("timeout"))         { builder.setTimeout(options.getInt("timeout")); }
        if (options.has("maximumAge"))      { builder.setMaximumAge(options.getLong("maximumAge")); }
        if (options.has("desiredAccuracy")) { builder.setDesiredAccuracy(options.getInt("desiredAccuracy")); }

        getAdapter().getCurrentPosition(builder.build());
    }

    private void watchPosition(final CallbackContext callbackContext, final JSONObject options) throws JSONException {
        Context context = cordova.getActivity().getApplicationContext();

        TSWatchPositionRequest.Builder builder = new TSWatchPositionRequest.Builder(context);

        builder.setCallback(new TSLocationCallback() {
            @Override public void onLocation(TSLocation location) {
                try {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, location.toJson());
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                } catch (JSONException e) {
                    TSLog.logger.debug(e.getMessage(), e);
                }
            }
            @Override public void onError(Integer error) { callbackContext.error(error); }
        });

        if (options.has("interval"))        { builder.setInterval(options.getLong("interval")); }
        if (options.has("extras"))          { builder.setExtras(options.getJSONObject("extras")); }
        if (options.has("persist"))         { builder.setPersist(options.getBoolean("persist")); }
        if (options.has("desiredAccuracy")) { builder.setDesiredAccuracy(options.getInt("desiredAccuracy")); }

        watchPositionCallbacks.add(callbackContext);
        getAdapter().watchPosition(builder.build());
    }

    private void stopWatchPosition(final CallbackContext callbackContext) {
        getAdapter().stopWatchPosition(new TSCallback() {
            @Override public void onSuccess() {
                JSONArray callbackIds = new JSONArray();
                Iterator<CallbackContext> iterator = watchPositionCallbacks.iterator();
                while (iterator.hasNext()) {
                    CallbackContext cb = iterator.next();
                    callbackIds.put(cb.getCallbackId());
                    iterator.remove();
                }
                callbackContext.success(callbackIds);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void addGeofence(final CallbackContext callbackContext, JSONObject config) {
        try {
            getAdapter().addGeofence(buildGeofence(config), new TSCallback() {
                @Override public void onSuccess() { callbackContext.success(); }
                @Override public void onFailure(String error) { callbackContext.error(error); }
            });
        } catch(JSONException | TSGeofence.Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void addGeofences(final CallbackContext callbackContext, JSONArray data) {
        List<TSGeofence> geofences = new ArrayList<TSGeofence>();
        for (int i = 0; i < data.length(); i++) {
            try {
                geofences.add(buildGeofence(data.getJSONObject(i)));
            } catch (JSONException | TSGeofence.Exception e) {
                callbackContext.error(e.getMessage());
                return;
            }
        }
        getAdapter().addGeofences(geofences, new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success();
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private TSGeofence buildGeofence(JSONObject config) throws JSONException, TSGeofence.Exception {
        TSGeofence.Builder builder = new TSGeofence.Builder();
        if (config.has("identifier"))       { builder.setIdentifier(config.getString("identifier")); }
        if (config.has("latitude"))         { builder.setLatitude(config.getDouble("latitude")); }
        if (config.has("longitude"))        { builder.setLongitude(config.getDouble("longitude")); }
        if (config.has("radius"))           { builder.setRadius((float) config.getDouble("radius")); }
        if (config.has("notifyOnEntry"))    { builder.setNotifyOnEntry(config.getBoolean("notifyOnEntry")); }
        if (config.has("notifyOnExit"))     { builder.setNotifyOnExit(config.getBoolean("notifyOnExit")); }
        if (config.has("notifyOnDwell"))    { builder.setNotifyOnDwell(config.getBoolean("notifyOnDwell")); }
        if (config.has("loiteringDelay"))   { builder.setLoiteringDelay(config.getInt("loiteringDelay")); }
        if (config.has("extras"))           { builder.setExtras(config.getJSONObject("extras")); }
        return builder.build();
    }

    private void getGeofences(final CallbackContext callbackContext) {
        getAdapter().getGeofences(new TSGetGeofencesCallback() {
            @Override public void onSuccess(List<TSGeofence> geofences) {
                JSONArray data = new JSONArray();
                for (TSGeofence geofence : geofences) {
                    data.put(geofence.toJson());
                }
                callbackContext.success(data);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void getGeofence(String identifier, final CallbackContext callbackContext) {
        getAdapter().getGeofence(identifier, new TSGetGeofenceCallback() {
            @Override public void onSuccess(TSGeofence geofence) {
                callbackContext.success(geofence.toJson());
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void geofenceExists(String identifier, final CallbackContext callbackContext) {
        getAdapter().geofenceExists(identifier, new TSGeofenceExistsCallback() {
            @Override public void onResult(boolean exists) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, exists));
            }
        });
    }

    private void getOdometer(CallbackContext callbackContext) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, getAdapter().getOdometer());
        callbackContext.sendPluginResult(result);
    }

    private void setOdometer(Float value, final CallbackContext callbackContext) {
        getAdapter().setOdometer(value, new TSLocationCallback() {
            @Override public void onLocation(TSLocation location) {
                try {
                    callbackContext.success(location.toJson());
                } catch (JSONException e) {
                    TSLog.logger.error(e.getMessage(), e);
                }
            }
            @Override public void onError(Integer error) {
                callbackContext.error(error);
            }
        });
    }

    private void startBackgroundTask(final CallbackContext callbackContext) {
        getAdapter().startBackgroundTask(new TSBackgroundTaskCallback() {
            @Override public void onStart(int taskId) {
                callbackContext.success(taskId);
            }
        });
    }

    private void stopBackgroundTask(int taskId, CallbackContext callbackContext) {
        getAdapter().stopBackgroundTask(taskId);
        callbackContext.success(taskId);
    }

    private void removeListener(String event, String callbackId, CallbackContext callbackContext) {
        Iterator<CordovaCallback> iterator = cordovaCallbacks.iterator();
        CordovaCallback found = null;
        while (iterator.hasNext() && (found == null)) {
            CordovaCallback cordovaCallback = iterator.next();
            if (cordovaCallback.callbackId.equalsIgnoreCase(callbackId)) {
                found = cordovaCallback;
            }
        }
        if (found != null) {
            cordovaCallbacks.remove(found);
            getAdapter().removeListener(event, found.callback);
            callbackContext.success();
        } else {
            TSLog.logger.warn(TSLog.warn("Failed to find listener for event: " + event));
            callbackContext.error(404);
        }
    }

    private void removeListeners(CallbackContext callbackContext) {
        getAdapter().removeListeners();
        cordovaCallbacks.clear();
        callbackContext.success();
    }

    private void addGeofenceListener(final CallbackContext callbackContext) {
        TSGeofenceCallback callback = new TSGeofenceCallback() {
            @Override public void onGeofence(GeofenceEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onGeofence(callback);
    }

    private void addGeofencesChangeListener(final CallbackContext callbackContext) {
        TSGeofencesChangeCallback callback = new TSGeofencesChangeCallback() {
            @Override
            public void onGeofencesChange(GeofencesChangeEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onGeofencesChange(callback);
    }

    private void addPowerSaveChangeListener(final CallbackContext callbackContext) {
        TSPowerSaveChangeCallback callback = new TSPowerSaveChangeCallback() {
            @Override
            public void onPowerSaveChange(Boolean isPowerSaveMode) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, isPowerSaveMode);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onPowerSaveChange(callback);
    }

    private void addConnectivityChangeListener(final CallbackContext callbackContext) {
        TSConnectivityChangeCallback callback = new TSConnectivityChangeCallback() {
            @Override public void onConnectivityChange(ConnectivityChangeEvent event) {
                JSONObject params = new JSONObject();
                try {
                    params.put("connected", event.hasConnection());
                    PluginResult result = new PluginResult(PluginResult.Status.OK, params);
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
                catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                }
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onConnectivityChange(callback);
    }

    private void addEnabledChangeListener(final CallbackContext callbackContext) {
        TSEnabledChangeCallback callback = new TSEnabledChangeCallback() {
            @Override public void onEnabledChange(boolean enabled) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, enabled);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onEnabledChange(callback);
    }

    private void addNotificationActionListener(final CallbackContext callbackContext) {
        TSNotificationActionCallback callback = new TSNotificationActionCallback() {
            @Override public void onClick(String buttonId) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, buttonId);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onNotificationAction(callback);
    }

    private void addHeartbeatListener(final CallbackContext callbackContext) {
        TSHeartbeatCallback callback = new TSHeartbeatCallback() {
            @Override
            public void onHeartbeat(HeartbeatEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onHeartbeat(callback);
    }

    private void addActivityChangeListener(final CallbackContext callbackContext) {
        TSActivityChangeCallback callback = new TSActivityChangeCallback() {
            @Override
            public void onActivityChange(ActivityChangeEvent event) {

                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onActivityChange(callback);
    }

    private void addProviderChangeListener(final CallbackContext callbackContext) {
        TSLocationProviderChangeCallback callback = new TSLocationProviderChangeCallback() {
            @Override public void onLocationProviderChange(LocationProviderChangeEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onLocationProviderChange(callback);
    }
    private void addScheduleListener(final CallbackContext callbackContext) {
        TSScheduleCallback callback = new TSScheduleCallback() {
            @Override public void onSchedule(ScheduleEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.getState());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onSchedule(callback);

    }

    private void addLocationListener(final CallbackContext callbackContext) {
        TSLocationCallback callback = new TSLocationCallback() {
            @Override public void onLocation(TSLocation location) {
                try {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, location.toJson());
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                } catch (JSONException e) {
                    TSLog.logger.error(e.getMessage(), e);
                }
            }
            @Override public void onError(Integer errorCode) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, errorCode);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onLocation(callback);
    }

    private void registerCallback(CallbackContext cordovaCallback, Object tsCallback) {
        cordovaCallbacks.add(new CordovaCallback(cordovaCallback.getCallbackId(), tsCallback));
    }

    private void addMotionChangeListener(final CallbackContext callbackContext) {
        TSLocationCallback callback = new TSLocationCallback() {
            @Override public void onLocation(TSLocation location) {
                JSONObject params = new JSONObject();
                try {
                    params.put("isMoving", location.getIsMoving());
                    try {
                        params.put("location", location.toJson());
                        PluginResult result = new PluginResult(PluginResult.Status.OK, params);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    } catch (JSONException e) {
                        TSLog.logger.error(e.getMessage(), e);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override public void onError(Integer error) {
                callbackContext.error(error);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onMotionChange(callback);
    }

    private void addHttpListener(final CallbackContext callbackContext) {
        TSHttpResponseCallback callback = new TSHttpResponseCallback() {
            @Override public void onHttpResponse(HttpResponse response) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, response.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };
        registerCallback(callbackContext, callback);
        getAdapter().onHttp(callback);

    }

    private void addAuthorizationListener(final CallbackContext callbackContext) {
        Context context = cordova.getActivity().getApplicationContext();
        TSAuthorizationCallback callback = new TSAuthorizationCallback() {
            @Override public void onResponse(AuthorizationEvent event) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, event.toJson());
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            }
        };

        registerCallback(callbackContext, callback);
        HttpService.getInstance(context).onAuthorization(callback);
    }

    private void removeGeofence(String identifier, final CallbackContext callbackContext) {
        getAdapter().removeGeofence(identifier, new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success();
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void removeGeofences(final JSONArray identifiers, final CallbackContext callbackContext) {
        List<String> rs = new ArrayList<String>();
        try {
            for (int i = 0; i < identifiers.length(); i++) {
                rs.add(identifiers.getString(i));
            }
            getAdapter().removeGeofences(rs, new TSCallback() {
                @Override public void onSuccess() {
                    callbackContext.success();
                }
                @Override public void onFailure(String error) {
                    callbackContext.error(error);
                }
            });
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void insertLocation(JSONObject params, final CallbackContext callbackContext) {
        getAdapter().insertLocation(params, new TSInsertLocationCallback() {
            @Override public void onSuccess(String uuid) {
                callbackContext.success(uuid);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void setConfig(final JSONObject params, final CallbackContext callbackContext) throws JSONException {
        TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
        config.updateWithJSONObject(params);
        callbackContext.success(config.toJson());
    }

    private void destroyLocations(final CallbackContext callbackContext) {
        getAdapter().destroyLocations(new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success();
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void destroyLocation(String uuid, final CallbackContext callbackContext) {
        getAdapter().destroyLocation(uuid, new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.success();
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void getLog(JSONObject params, final CallbackContext callbackContext) throws JSONException {
        TSLog.getLog(parseSQLQuery(params), new TSGetLogCallback() {
            @Override public void onSuccess(String log) {
                callbackContext.success(log);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void destroyLog(final CallbackContext callbackContext) {
        TSLog.destroyLog(new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void emailLog(String email, JSONObject params, final CallbackContext callbackContext) throws JSONException {
        TSLog.emailLog(cordova.getActivity(), email, parseSQLQuery(params), new TSEmailLogCallback() {
            @Override public void onSuccess() {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void uploadLog(String url, JSONObject params, final CallbackContext callbackContext) throws JSONException {
        TSLog.uploadLog(cordova.getActivity().getApplicationContext(), url, parseSQLQuery(params), new TSCallback() {
            @Override public void onSuccess() {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, true));
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private SQLQuery parseSQLQuery(JSONObject params) throws JSONException {
        SQLQuery query = SQLQuery.create();
        if (params.has(SQLQuery.FIELD_START)) {
            query.setStart(params.getLong(SQLQuery.FIELD_START));
        }
        if (params.has(SQLQuery.FIELD_END)) {
            query.setEnd(params.getLong(SQLQuery.FIELD_END));
        }
        if (params.has(SQLQuery.FIELD_ORDER)) {
            query.setOrder(params.getInt(SQLQuery.FIELD_ORDER));
        }
        if (params.has(SQLQuery.FIELD_LIMIT )) {
            query.setLimit(params.getInt(SQLQuery.FIELD_LIMIT));
        }
        return query;
    }

    private void log(JSONArray arguments, CallbackContext callbackContext) throws JSONException {
        String level = arguments.getString(0);
        String message = arguments.getString(1);
        TSLog.log(level, message);
        callbackContext.success();
    }

    private JSONObject getState() {
        return TSConfig.getInstance(cordova.getActivity().getApplicationContext()).toJson();
    }

    private void getSensors(CallbackContext callbackContext) {
        JSONObject result = new JSONObject();
        Sensors sensors = Sensors.getInstance(cordova.getActivity().getApplicationContext());
        try {
            result.put("platform", "android");
            result.put("accelerometer", sensors.hasAccelerometer());
            result.put("magnetometer", sensors.hasMagnetometer());
            result.put("gyroscope", sensors.hasGyroscope());
            result.put("significant_motion", sensors.hasSignificantMotion());
            callbackContext.success(result);
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void getDeviceInfo(CallbackContext callbackContext) {
        JSONObject deviceInfo = DeviceInfo.getInstance(cordova.getActivity().getApplicationContext()).toJson();
        callbackContext.success(deviceInfo);
    }

    private void isPowerSaveMode(CallbackContext callbackContext) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, getAdapter().isPowerSaveMode());
        callbackContext.sendPluginResult(result);
    }

    private void isIgnoringBatteryOptimizations(CallbackContext callbackContext) {
        PluginResult result = new PluginResult(PluginResult.Status.OK, getAdapter().isIgnoringBatteryOptimizations());
        callbackContext.sendPluginResult(result);
    }

    private void requestSettings(JSONObject args, CallbackContext callbackContext) throws JSONException {
        String action = args.getString("action");
        DeviceSettingsRequest request = getAdapter().requestSettings(action);
        if (request != null) {
            callbackContext.success(request.toJson());
        } else {
            callbackContext.error("Failed to find " + action + " screen for device " + Build.MANUFACTURER + " " + Build.MODEL + "@" + Build.VERSION.RELEASE);
        }
    }

    private void showSettings(JSONObject args, CallbackContext callbackContext) throws JSONException {
        String action = args.getString("action");
        boolean didShow = getAdapter().showSettings(action);
        if (didShow) {
            callbackContext.success();
        } else {
            callbackContext.error("Failed to find " + action + " screen for device " + Build.MANUFACTURER + " " + Build.MODEL + "@" + Build.VERSION.RELEASE);
        }
    }

    private void requestPermission(final CallbackContext callbackContext) {
        getAdapter().requestPermission(new TSRequestPermissionCallback() {
            @Override public void onSuccess(int status) {
                callbackContext.success(status);
            }
            @Override public void onFailure(int status) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, status);
                callbackContext.sendPluginResult(result);
            }
        });
    }

    // [iOS 14+ only] -- No Android implementation.  Just return CLAccuracyAuthorizationFull (0)
    private void requestTemporaryFullAccuracy(String purpose, final CallbackContext callbackContext) {
        getAdapter().requestTemporaryFullAccuracy(purpose, new TSRequestPermissionCallback() {
            @Override public void onSuccess(int accuracyAuthorization) {
                callbackContext.success(accuracyAuthorization);
            }
            @Override public void onFailure(int accuracyAuthorization) {
                callbackContext.success(accuracyAuthorization);
            }
        });
    }


    private void getTransistorToken(String orgname, String username, String url, final CallbackContext callbackContext) {
        Context context = cordova.getActivity().getApplicationContext();
        TransistorAuthorizationToken.findOrCreate(context, orgname, username, url, new TransistorAuthorizationToken.Callback() {
            @Override public void onSuccess(TransistorAuthorizationToken token) {
                callbackContext.success(token.toJson());
            }
            @Override public void onFailure(String error) {
                JSONObject response = new JSONObject();
                try {
                    response.put("status", error);
                    response.put("message", error);
                    callbackContext.error(response);
                } catch (JSONException e) {}
            }
        });
    }

    private void destroyTransistorToken(String url, CallbackContext callbackContext) {
        Context context = cordova.getActivity().getApplicationContext();
        TransistorAuthorizationToken.destroyTokenForUrl(context, url, new TSCallback() {
            @Override public void onSuccess() {
                PluginResult result = new PluginResult(PluginResult.Status.OK, true);
                callbackContext.sendPluginResult(result);
            }
            @Override public void onFailure(String error) {
                callbackContext.error(error);
            }
        });
    }

    private void getProviderState(CallbackContext callbackContext) {
        callbackContext.success(getAdapter().getProviderState().toJson());
    }

    private void onError(String error) {
        String message = "BG Geolocation caught a Javascript exception while running in background-thread:\n".concat(error);
        Log.e(TAG,message);
        TSConfig config = TSConfig.getInstance(cordova.getActivity().getApplicationContext());
        // Show alert popup with js error
        if (config.getDebug()) {
            getAdapter().startTone("ERROR");
            AlertDialog.Builder builder = new AlertDialog.Builder(this.cordova.getActivity());
            builder.setMessage(message)
                    .setCancelable(false)
                    .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //do things
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private JSONObject setHeadlessJobService(JSONObject params) throws JSONException {
        params.put("headlessJobService", getClass().getPackage().getName() + "." + HEADLESS_JOB_SERVICE_CLASS);
        return params;
    }

    private void handlePlayServicesConnectError(Integer errorCode) {
        Activity activity = cordova.getActivity();
        ExtensionApiAvailability.getInstance().getErrorDialog(activity, errorCode, 1001).show();
    }

    private BackgroundGeolocation getAdapter() {
        Activity activity = cordova.getActivity();
        return BackgroundGeolocation.getInstance(activity.getApplicationContext(), activity.getIntent());
    }
    /**
     * Override method in CordovaPlugin.
     * Checks to see if it should turn off
     */
    public void onPause(boolean multitasking) {

    }

    public void onStop() {
        Context context = cordova.getActivity().getApplicationContext();
        TSConfig config = TSConfig.getInstance(context);
        if (config.getEnabled()) {
            TSScheduleManager.getInstance(context).oneShot(TerminateEvent.ACTION, 10000);
        }
    }

    public void onResume(boolean multitasking) {

    }

    public void onDestroy() {
        Log.i(TAG, "CDVBackgroundGeolocation#onDestoy");
        getAdapter().onActivityDestroy();
        super.onDestroy();
    }

    private class CordovaCallback {
        public String callbackId;
        public Object callback;

        public CordovaCallback(String _callbackId, Object _callback) {
            callbackId  = _callbackId;
            callback    = _callback;
        }
    }
}
