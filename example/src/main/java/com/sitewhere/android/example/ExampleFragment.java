/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
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
package com.sitewhere.android.example;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.sitewhere.androidsdk.SiteWhereMessageClient;
import com.sitewhere.androidsdk.messaging.SiteWhereMessagingException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fragment that implements example application.
 *
 * @author Derek
 */
public class ExampleFragment extends MapFragment implements LocationListener, SensorEventListener,
        OnMapReadyCallback {

    /**
     * Tag used for logging
     */
    private static final String TAG = "ExampleFragment";

    /**
     * Arbitrary permissions request code.
     */
    public static final int LOCATION_REQUEST_CODE = 1001;

    /**
     * Interval at which locations are sent
     */
    private static final int SEND_INTERVAL_IN_SECONDS = 5;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Text views for lat, long, altitude
     */
    private TextView txtLat;
    private TextView txtLon;
    private TextView txtAlt;

    /**
     * Text views for rotation vector
     */
    private TextView txtRotX;
    private TextView txtRotY;
    private TextView txtRotZ;

    /**
     * Manages location updates
     */
    protected LocationManager locationManager;

    /**
     * Manages sensors
     */
    protected SensorManager sensorManager;

    /**
     * Sensor for rotation vector
     */
    protected Sensor rotationVector;

    /**
     * Last reported location
     */
    private Location lastLocation;

    /**
     * Last reported rotation vector
     */
    private float[] lastRotation;

    /**
     * Used to schedule a recurring report to SiteWhere for location
     */
    private ScheduledExecutorService scheduler;

    /**
     * Google Map
     */
    private GoogleMap googleMap;

    /*
     * (non-Javadoc)
     *
     * @see android.app.Fragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup,
     * android.os.Bundle)
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup example = (ViewGroup) inflater.inflate(R.layout.example, container, false);

        View map = super.onCreateView(inflater, example, savedInstanceState);
        FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        map.setLayoutParams(mapParams);
        example.addView(map);

        View overlay = inflater.inflate(R.layout.sensor_overlay, example, false);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        overlay.setLayoutParams(overlayParams);
        example.addView(overlay);

        startDeviceMonitoring();

        return example;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.app.Activity#onPostCreate(android.os.Bundle)
     */
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();

        // Getting Google Play availability status
        int status = apiAvailability.isGooglePlayServicesAvailable(getActivity().getBaseContext());

        // If Google Play not available, show dialog for dealing with it.
        if (status != ConnectionResult.SUCCESS) {
            Dialog dialog = apiAvailability.getErrorDialog(getActivity(), status, PLAY_SERVICES_RESOLUTION_REQUEST);
            dialog.show();
        } else {
            hookViews();
            getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        this.googleMap = googleMap;
        googleMap.setMyLocationEnabled(true);
    }

    /**
     * When connected to SiteWhere, start monitoring device.
     */
    public void onSiteWhereConnected() {
        startDeviceMonitoring();
    }

    /**
     * Only schedule SiteWhere reporting thread once we have a connection to the server.
     */
    public void startDeviceMonitoring() {
        Log.d(TAG, "Starting device monitoring.");

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Start location updates.
                boolean locationStarted = false;
                locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
                if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No permissions for location. Requesting permissions from user.");
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST_CODE);
                    return;
                }
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ExampleFragment.this);
                    locationStarted = true;
                    Log.d(TAG, "Started monitoring locations via GPS provider.");
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ExampleFragment.this);
                    locationStarted = true;
                    Log.d(TAG, "Started monitoring locations via network provider.");
                } else {
                    locationStarted = false;
                    Log.d(TAG, "No location provider available. Will not monitor location.");
                }

                // Start accelerometer updates.
                boolean accelerometerStarted = false;
                sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
                if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                    rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                    sensorManager.registerListener(ExampleFragment.this, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);
                    accelerometerStarted = true;
                    Log.d(TAG, "Started monitoring accelerometer.");
                } else {
                    Toast.makeText(getActivity().getApplicationContext(), "Unable to start accelerometer updates. No accelerometer provided", Toast.LENGTH_LONG);
                    accelerometerStarted = false;
                    Log.d(TAG, "Unable to monitor accelerometer.");
                }

                // Send alerts to SiteWhere.
                SiteWhereMessageClient messageClient = SiteWhereMessageClient.getInstance();
                try {
                    if (locationStarted)
                        messageClient.sendDeviceAlert(messageClient.getUniqueDeviceId(), "location.started", "Started to read location data.", null);
                } catch (SiteWhereMessagingException ex) {
                    Log.e(TAG, "Unable to send location.started alert to SiteWhere.");
                }
                try {
                    if (accelerometerStarted)
                        messageClient.sendDeviceAlert(messageClient.getUniqueDeviceId(), "accelerometer.started", "Started to read accelerometer data.", null);
                } catch (SiteWhereMessagingException e) {
                    Log.e(TAG, "Unable to send accelerometer.started alert to SiteWhere.");
                }


                if (scheduler != null) {
                    scheduler.shutdownNow();
                }
                scheduler = Executors.newSingleThreadScheduledExecutor();
                scheduler.scheduleAtFixedRate(new SiteWhereDataReporter(), SEND_INTERVAL_IN_SECONDS,
                        SEND_INTERVAL_IN_SECONDS, TimeUnit.SECONDS);
                Log.d(TAG, "Set up scheduler for monitoring.");
            }
        });
    }

    /**
     * Handles result of requesting permissions on-the-fly.
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                    startDeviceMonitoring();
                }
            }
        }
    }

    /**
     * Turn off scheduling when SiteWhere is disconnected.
     */
    public void onSiteWhereDisconnected() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        Log.d(TAG, "Example fragment no longer sending data to SiteWhere.");
    }

    /**
     * Get reference to views.
     */
    protected void hookViews() {
        txtLat = (TextView) getActivity().findViewById(R.id.overlay_lat_value);
        txtLon = (TextView) getActivity().findViewById(R.id.overlay_lon_value);
        txtAlt = (TextView) getActivity().findViewById(R.id.overlay_alt_value);

        txtRotX = (TextView) getActivity().findViewById(R.id.overlay_rotx_value);
        txtRotY = (TextView) getActivity().findViewById(R.id.overlay_roty_value);
        txtRotZ = (TextView) getActivity().findViewById(R.id.overlay_rotz_value);
    }

    /**
     * Sends most recent location and measurements to SiteWhere.
     */
    protected void onSendDataToSiteWhere() {
        SiteWhereMessageClient messageClient = SiteWhereMessageClient.getInstance();
        try {
            if (lastLocation != null) {
                messageClient.sendDeviceLocation(messageClient.getUniqueDeviceId(), lastLocation.getLatitude(),
                        lastLocation.getLongitude(), lastLocation.getAltitude(), null);

                Activity activity = getActivity();

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
//                            Toast.makeText(getActivity(), "Sent Location to SiteWhere", Toast.LENGTH_SHORT)
//                                    .show();
                        }
                    });
                }
                Map<String, Double> measurements = new HashMap<>();
                measurements.put("x.rotation", new Double(lastRotation[0]));
                measurements.put("y.rotation", new Double(lastRotation[1]));
                measurements.put("z.rotation", new Double(lastRotation[2]));
                messageClient.sendDeviceMeasurements(messageClient.getUniqueDeviceId(), measurements, new Date());
            }
        } catch (Throwable e) {
            Log.e(TAG, "Unable to send location to SiteWhere.", e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.location.LocationListener#onLocationChanged(android.location.Location)
     */
    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "Received location update from device.");

        if (googleMap == null) {
            return;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        LatLng latLng = new LatLng(latitude, longitude);

        // For first location reading, center and zoom to location.
        if (lastLocation == null) {
            CameraPosition position = googleMap.getCameraPosition();
            CameraPosition.Builder builder = CameraPosition.builder(position).target(latLng).zoom(15)
                    .tilt(45);
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()));
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
        }

        txtLat.setText(String.format("%1.6f", latitude));
        txtLon.setText(String.format("%1.6f", longitude));
        txtAlt.setText(String.format("%1.6f", location.getAltitude()));

        this.lastLocation = location;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
     */
    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Location provider (" + provider + ") disabled.");
    }

    /*
     * (non-Javadoc)
     *
     * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
     */
    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Location provider (" + provider + ") enabled.");
    }

    /*
     * (non-Javadoc)
     *
     * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see android.hardware.SensorEventListener#onAccuracyChanged(android.hardware.Sensor, int)
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Sensor accuracy changed for " + sensor.getName() + ".");
    }

    /*
     * (non-Javadoc)
     *
     * @see android.hardware.SensorEventListener#onSensorChanged(android.hardware.SensorEvent)
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        lastRotation = event.values;
        if (lastRotation != null) {
            txtRotX.setText(String.format("%1.6f", lastRotation[0]));
            txtRotY.setText(String.format("%1.6f", lastRotation[1]));
            txtRotZ.setText(String.format("%1.6f", lastRotation[2]));
        }
    }

    /**
     * Runs data reporting in a separate thread.
     *
     * @author Derek
     */
    private class SiteWhereDataReporter implements Runnable {

        @Override
        public void run() {
            onSendDataToSiteWhere();
        }
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}