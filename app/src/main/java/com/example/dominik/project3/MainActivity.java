package com.example.dominik.project3;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.vecmath.Tuple3d;
import javax.vecmath.Vector3d;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.vision.text.Text;

public class MainActivity extends AppCompatActivity

        implements

        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        SensorEventListener,
        LocationListener,
        OnMapReadyCallback,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnMarkerClickListener,
        ResultCallback<Status>
{

    //======================== Step counting below ================================
    private final static Double THRESHOLD = 0.2;
    private static String walk = "WALK";
    private static int MAX_SIZE = 1000;
    public static GoogleApiClient mApiClient;
    TextView textView;
    TextView countTextView;
    TextView fullerCountTextView;
    TextView libraryCountTextView;
    PowerManager.WakeLock wakeLock = null;
    double[] gravity = new double[]{0.0, 0.0, 0.0};
    int count = 0;
    boolean startCount = true;
    int index = 0;
    ArrayList<Tuple3d> accelerations = new ArrayList<>(Collections.nCopies(MAX_SIZE, new Vector3d()));
    ArrayList<Tuple3d> averageAcceleration = new ArrayList<>(Collections.nCopies(MAX_SIZE, new Vector3d()));
    private SensorManager mSensorManager;
    private Sensor mSensor;
    private CompositeDisposable task = new CompositeDisposable();
    private Disposable updateStep = null;

    //================Helper functions===================
    static int clamp(int min, int max, int value) {
        return Math.max(Math.min(max, value), min);
    }

    static int rotate(int max, int value) {
        if (value < 0) return max - (0 - value);
        return value % max;

    }

    static List<Tuple3d> getLast(int howmany, int currentIndex, List<Tuple3d> array) {
        if (array.size() < howmany || howmany <= 0 || currentIndex >= array.size() || currentIndex < 0)
            throw new IllegalArgumentException("zwrong arguments");
        int start = currentIndex - howmany;
        List<Tuple3d> result = array.subList(clamp(0, currentIndex, start), currentIndex + 1);
        List<Tuple3d> spare = new ArrayList<>();
        if (start < 0)
            spare = Observable.just(array).flatMapIterable(v -> v).takeLast(start * -1 - 1)
                    .collectInto(new ArrayList<Tuple3d>(), ArrayList::add).blockingGet();
        result.addAll(spare);
        return result;
    }

    static Tuple3d average3(Tuple3d prev, Tuple3d now, Tuple3d front) {
        Tuple3d t = new Vector3d();
        t.add(now, front);
        t.add(t, prev);
        t.scale(1.0 / 3);
        return t;
    }

    Receiver receiver;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) count = savedInstanceState.getInt(walk);
        setContentView(R.layout.activity_main);
//        textView = (TextView) findViewById(R.id.textview);
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mApiClient.connect();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null)
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyWakelockTag");
            wakeLock.acquire(1000 * 600);
        }

        // initialize GoogleMaps
        initGMaps();

        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addAction(ACTIVITY_RESULT);
        filter.addAction("geofence");

        receiver = new Receiver();
        registerReceiver(receiver, filter);
        imageView = (ImageView) findViewById(R.id.activityImageView);
        textView = (TextView) findViewById(R.id.activityTextView);
        countTextView = (TextView) findViewById(R.id.countTextView);
        fullerCountTextView = (TextView) findViewById(R.id.fullerCountTextView);
        libraryCountTextView = (TextView) findViewById(R.id.libraryCountTextView);
        fullerCountTextView.setText(Integer.toString(fullerCount));
        libraryCountTextView.setText(Integer.toString(libraryCount));

    }

    int fullerCount = 0;
    int libraryCount = 0;

    @Override
    protected void onStop() {
        super.onStop();

        // Disconnect GoogleApiClient when stopping Activity
        mApiClient.disconnect();
        unregisterReceiver(receiver);
        if (locationTask != null) locationTask.dispose();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(walk, count);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null) wakeLock.release();
        task.clear();
        if (updateStep != null)
            updateStep.dispose();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "onConnected()");
        Intent intent = new Intent( this, ActivityRecognizedService.class );
        PendingIntent pendingIntent = PendingIntent.getService( this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT );
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates( mApiClient, 3000, pendingIntent );
        if (locationTask != null) locationTask.dispose();
        locationTask = Observable.interval(3, TimeUnit.SECONDS).subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> {
            getLastKnownLocation();
        });
        markerForGeofence(fullerLatLng, FULLER_GEOFENCE_ID);
        markerForGeofence(libraryLatLng, LIBRARY_GEOFENCE_ID);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "onConnectionSuspended()");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG, "onConnectionFailed()");
    }

    private Consumer<Long> callback = l -> {
        if (startCount) {
            List<Tuple3d> all = getLast(50, index, new ArrayList<>(averageAcceleration));
            List<Tuple3d> current = getLast(3, all.size() - 1, all);
            //get max from the last 50 data points
            Tuple3d max = Observable.just(all)
                    .flatMapIterable(v -> v).reduce((tuple3d, tuple3d2) -> new Vector3d(Math.max(tuple3d.x, tuple3d2.x),
                            Math.max(tuple3d.y, tuple3d2.y),
                            Math.max(tuple3d.z, tuple3d2.z))).blockingGet();

            //get min from the last 50 data points
            Tuple3d min = Observable.just(all)
                    .flatMapIterable(v -> v).reduce((tuple3d, tuple3d2) -> new Vector3d(Math.min(tuple3d.x, tuple3d2.x),
                            Math.min(tuple3d.y, tuple3d2.y),
                            Math.min(tuple3d.z, tuple3d2.z))).blockingGet();


            //calculate average from max min
            Tuple3d ave = new Vector3d();
            ave.sub(max, min);
            ave.scale(1.0 / 2);


            //choose axis
            Axis axis;
            if (ave.x >= ave.y && ave.x >= ave.z) {
                axis = Axis.x;
            } else if (ave.y >= ave.x && ave.y >= ave.z) {
                axis = Axis.y;
            } else axis = Axis.z;


            //calculate local max and local min from lAST 3 DATA POINTS
            Tuple3d localMax = Observable.just(current)
                    .flatMapIterable(v -> v)
                    .reduce((a, b) -> new Vector3d(Math.max(a.x, b.x),
                            Math.max(a.y, b.y),
                            Math.max(a.z, b.z))).blockingGet();

            Tuple3d localMin = Observable.just(current)
                    .flatMapIterable(v -> v)
                    .reduce((a, b) -> new Vector3d(Math.min(a.x, b.x),
                            Math.min(a.y, b.y),
                            Math.min(a.z, b.z))).blockingGet();

            //increment count if satisfy some small conditions. Quite basic :D
            switch (axis) {
                case x:
                    if (localMax.x >= ave.x && localMin.x <= ave.x) {
                        if (localMax.x - localMin.x > THRESHOLD) {
                            count += 1;
                            onStepIncrease();
                        }
                    }
                    break;
                case y:
                    if (localMax.y >= ave.y && localMin.y <= ave.y) {
                        if (localMax.y - localMin.y > THRESHOLD) {
                            count += 1;
                            onStepIncrease();
                        }
                    }
                    break;
                case z:
                    if (localMax.z >= ave.z && localMin.z <= ave.z) {
                        if (localMax.z - localMin.z > THRESHOLD) {
                            count += 1;
                            onStepIncrease();
                        }
                    }
                    break;
            }


        }

    };

    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager != null) {
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI); //60000 micro miliseconds delay
        }
        //update text every 200 ms
        task.add(Observable.interval(200, TimeUnit.MILLISECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe(v -> countTextView.setText("Step count: " + Integer.toString(count))));

        //check step every 200 ms
        if (currentActivity == null) {
            if (updateStep != null) updateStep.dispose();
            updateStep = null;
        } else {
            switch (currentActivity.getType()) {
                case DetectedActivity.STILL:
                    updateStep = null;
                    break;
                case DetectedActivity.RUNNING:
                    updateStep = Observable.interval(200, TimeUnit.MILLISECONDS).subscribe(callback);
                    break;
                case DetectedActivity.WALKING:
                    updateStep = Observable.interval(1000, TimeUnit.MILLISECONDS).subscribe(callback);
            }
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double[] linear_acceleration = new double[3];

        final double alpha = 0.8;

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        //put acceleration into array
        accelerations.set(index, new Vector3d(linear_acceleration[0], linear_acceleration[1], linear_acceleration[2]));


        //smoothing acceleration
        averageAcceleration.set(index, average3(accelerations.get(rotate(MAX_SIZE, index - 1)),
                accelerations.get(index),
                accelerations.get(rotate(MAX_SIZE, index + 1))));


        //increment index, start measuring when there are 50 data points
        if (index > 50) startCount = true;
        index = (index + 1) % MAX_SIZE;


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }


    public DetectedActivity currentActivity = null;
    public long activityStart = 0;
    public static String ACTIVITY_RESULT = "result";

    public class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case "result":
                    DetectedActivity activity = intent.getParcelableExtra(ActivityRecognizedService.RESULT);
                    if (currentActivity == null) {
                        currentActivity = activity;
                        activityStart = Calendar.getInstance().getTime().getTime();
                        textView.setText("You are " + fromActivity(activity.getType()));
                        imageView.setImageDrawable(getDrawable(getDrawableFrom(currentActivity)));
                    } else if (currentActivity.getType() != activity.getType()) {
                        long current = Calendar.getInstance().getTime().getTime();
                        Toast.makeText(MainActivity.this, String.format("You were %s for %d seconds",fromActivity(currentActivity.getType()), (current - activityStart) / 1000 ), Toast.LENGTH_SHORT).show();
                        currentActivity = activity;
                        activityStart = current;
                        textView.setText("You are " + fromActivity(activity.getType()));
                        imageView.setImageDrawable(getDrawable(getDrawableFrom(currentActivity)));
                        if (updateStep != null) {
                            updateStep.dispose();
                        }
                        updateStep = initUpdateStep(activity);
                    }
                    break;
                case "geofence":
                    //Entering,FULLER
                    String gfd = (String)intent.getExtras().get("geofenceTransitionDetails");
                    String[] split = gfd.split(",");
                    boolean e = split[0].equals("Entering");
                    switch(split[1]) {
                        case "FULLER":
                            inFuller = e;
                            prevStepCount = count;
                            break;
                        case "LIBRARY":
                            inLib = e;
                            prevStepCount = count;
                            break;
                    }
                    break;
            }
        }
    }

    public int prevStepCount = -100000;

    public void onStepIncrease() {
        Observable.fromCallable(() -> {

            if (count - prevStepCount == 6) {
                if (inFuller) {
                    fullerCount++;
                    fullerCountTextView.setText(Integer.toString(fullerCount));
                    Toast.makeText(this, "You have taken 6 steps inside fuller", Toast.LENGTH_LONG).show();
                }
                if (inLib) {
                    libraryCount++;
                    libraryCountTextView.setText(Integer.toString(libraryCount));

                    Toast.makeText(this, "You have taken 6 steps inside library", Toast.LENGTH_LONG).show();
                }
            }
            return 1;
        }).subscribeOn(AndroidSchedulers.mainThread()).subscribe();
    }

    private Disposable initUpdateStep(DetectedActivity activity) {
        switch (activity.getType()) {
            case DetectedActivity.RUNNING:
                return Observable.interval(200, TimeUnit.MILLISECONDS).subscribe(callback);
            case DetectedActivity.STILL:
                return null;
            case DetectedActivity.WALKING:
                return Observable.interval(1000, TimeUnit.MILLISECONDS).subscribe(callback);
            default:
                return null;
        }
    }

    static String fromActivity(int activity) {
        switch (activity) {
            case DetectedActivity.RUNNING:
                return "running";
            case DetectedActivity.WALKING:
                return "walking";
            case DetectedActivity.STILL:
                return "still";
            default:
                return "still";
        }
    }

    static int getDrawableFrom(DetectedActivity activity) {
        switch (activity.getType()) {
            case DetectedActivity.RUNNING:
                return R.drawable.running;
            case DetectedActivity.WALKING:
                return R.drawable.walking;
            case DetectedActivity.STILL:
                return R.drawable.still;
            default:
                return R.drawable.still;

        }
    }


    enum Axis {
        x, y, z
    }

    //======================== GeoFencing Stuff ================================

    private static final String TAG = MainActivity.class.getSimpleName();

    private MapFragment mapFragment;
    private GoogleMap map;
    private Location lastLocation;
    private LocationRequest locationRequest;
    private Marker locationMarker;
    private Marker geoFenceMarker;
    private PendingIntent geoFencePendingIntent;
    private Circle geoFenceLimits;

    public void setInFuller(boolean b) {
        this.inFuller = b;
    }

    public void setInLib(boolean b) {
        this.inLib = b;
    }

    // Defined in mili seconds.
    // This number in extremely low, and should be used only for debug
    private final int UPDATE_INTERVAL =  3 * 60 * 1000; // 3 minutes
    private final int FASTEST_INTERVAL = 30 * 1000;  // 30 secs
    private static final long GEO_DURATION = 60 * 60 * 1000;
    private static final String LIBRARY_GEOFENCE_ID = "LIBRARY";
    private static final String FULLER_GEOFENCE_ID = "FULLER";
    private static final float GEOFENCE_RADIUS = 34.0f; // in meters
    private final int GEOFENCE_REQ_CODE = 0;
    private static final String NOTIFICATION_MSG = "NOTIFICATION MSG";
    private final int REQ_PERMISSION = 999;
    private LatLng libraryLatLng = new LatLng(42.274339,-71.806580);
    private LatLng fullerLatLng = new LatLng(42.275000,-71.806600);
    private boolean inFuller = false;
    private boolean inLib = false;


    // Initialize GoogleMaps
    private void initGMaps(){
        mapFragment = (MapFragment) (getFragmentManager()
                .findFragmentById(R.id.map));
        ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
        params.height = 900;
        mapFragment.getView().setLayoutParams(params);
        mapFragment.getMapAsync(this);
    }

    // Callback called when Map is ready
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "onMapReady()");
        map = googleMap;
        map.setOnMapClickListener(this);
        map.setOnMarkerClickListener(this);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        Log.i(TAG, "onMapClick("+latLng +")");
//        markerForGeofence(latLng);
    }

    // Callback called when Marker is touched
    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.i(TAG, "onMarkerClickListener: " + marker.getPosition() );
        return false;
    }

    // Get last known location
    private void getLastKnownLocation() {
        Log.i(TAG, "getLastKnownLocation()");
        if ( checkPermission() ) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
            if ( lastLocation != null ) {
                Log.i(TAG, "LastKnown location. " +
                        "Long: " + lastLocation.getLongitude() +
                        " | Lat: " + lastLocation.getLatitude());
                markerLocation(new LatLng(lastLocation.getLatitude(),lastLocation.getLongitude()));
                startLocationUpdates();
            } else {
                Log.w(TAG, "No location retrieved yet");
                startLocationUpdates();
            }
        }
        else askPermission();
    }

    // Start location Updates
    private void startLocationUpdates(){
        Log.i(TAG, "startLocationUpdates()");
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(UPDATE_INTERVAL)
                .setFastestInterval(FASTEST_INTERVAL);

        if ( checkPermission() )
            LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, locationRequest, this);
    }

    // Check for permission to access Location
    private boolean checkPermission() {
        Log.i(TAG, "checkPermission()");
        // Ask for permission if it wasn't granted yet
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED );
    }

    // Asks for permission
    private void askPermission() {
        Log.i(TAG, "askPermission()");
        ActivityCompat.requestPermissions(
                this,
                new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                REQ_PERMISSION
        );
    }

    // Verify user's response of the permission requested
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionsResult()");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch ( requestCode ) {
            case REQ_PERMISSION: {
                if ( grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED ){
                    // Permission granted
                    if (locationTask != null) locationTask.dispose();
                    locationTask = Observable.interval(3, TimeUnit.SECONDS).subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread()).subscribe(v -> {
                        getLastKnownLocation();
                    });

                } else {
                    // Permission denied
                    permissionsDenied();
                }
                break;
            }
        }
    }

    Disposable locationTask = null;

    // App cannot work without the permissions
    private void permissionsDenied() {
        Log.w(TAG, "permissionsDenied()");
    }

    boolean hasZoom = false;
    // Create a Location Marker
    private void markerLocation(LatLng latLng) {
        Log.i(TAG, "markerLocation("+latLng+")");
        String title = latLng.latitude + ", " + latLng.longitude;
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .title(title);
        if ( map!=null ) {
            // Remove the anterior marker
            if ( locationMarker != null )
                locationMarker.remove();
            locationMarker = map.addMarker(markerOptions);
            float zoom = 14f;
            if (!hasZoom) {
                hasZoom = true;
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
            map.animateCamera(cameraUpdate);}
        }
    }

    // Create a marker for the geofence creation
    private void markerForGeofence(LatLng latLng, String gid) {
        Log.i(TAG, "markerForGeofence("+latLng+")");
        String title = latLng.latitude + ", " + latLng.longitude;
        // Define marker options
        MarkerOptions markerOptions = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                .title(title);
        if ( map!=null ) {
            // Remove last geoFenceMarker
//            if (geoFenceMarker != null)
//                geoFenceMarker.remove();

            geoFenceMarker = map.addMarker(markerOptions);
            startGeofence(gid);
        }
    }

    // Create a Geofence
    private Geofence createGeofence( LatLng latLng, float radius, String gid ) {
        Log.i(TAG, "createGeofence");
        return new Geofence.Builder()
                .setRequestId(gid)
                .setCircularRegion( latLng.latitude, latLng.longitude, radius)
                .setExpirationDuration( GEO_DURATION )
                .setTransitionTypes( Geofence.GEOFENCE_TRANSITION_ENTER
                        | Geofence.GEOFENCE_TRANSITION_EXIT )
                .build();
    }

    // Create a Geofence Request
    private GeofencingRequest createGeofenceRequest( Geofence geofence ) {
        Log.i(TAG, "createGeofenceRequest");
        return new GeofencingRequest.Builder()
                .setInitialTrigger( GeofencingRequest.INITIAL_TRIGGER_ENTER )
                .addGeofence( geofence )
                .build();
    }

    private PendingIntent createGeofencePendingIntent() {
        Log.i(TAG, "createGeofencePendingIntent");
        if ( geoFencePendingIntent != null )
            return geoFencePendingIntent;

        Intent intent = new Intent( this, GeofenceTransitionService.class);
        return PendingIntent.getService(
                this, GEOFENCE_REQ_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT );
    }

    // Add the created GeofenceRequest to the device's monitoring list
    private void addGeofence(GeofencingRequest request) {
        Log.i(TAG, "addGeofence");
        if (checkPermission())
            LocationServices.GeofencingApi.addGeofences(
                    mApiClient,
                    request,
                    createGeofencePendingIntent()
            );
        drawGeofence();
    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "onResult: " + status);
        if ( status.isSuccess() ) {
            drawGeofence();
        } else {
            Log.i(TAG, "onResult (failed): " + status);
        }
    }

    // Draw Geofence circle on GoogleMap
    private void drawGeofence() {
        Log.i(TAG, "drawGeofence()");

//        if ( geoFenceLimits != null )
//            geoFenceLimits.remove();

        CircleOptions circleOptions = new CircleOptions()
                .center( geoFenceMarker.getPosition())
                .strokeColor(Color.argb(50, 70,70,70))
                .fillColor( Color.argb(100, 150,150,150) )
                .radius( GEOFENCE_RADIUS );
        geoFenceLimits = map.addCircle( circleOptions );
    }

    // Start Geofence creation process
    private void startGeofence(String gid) {
        Log.i(TAG, "startGeofence()");
        if( geoFenceMarker != null ) {
            Geofence geofence = createGeofence( geoFenceMarker.getPosition(), GEOFENCE_RADIUS, gid );
            GeofencingRequest geofenceRequest = createGeofenceRequest( geofence );
            addGeofence( geofenceRequest );
        } else {
            Log.e(TAG, "Geofence marker is null");
        }
    }

    // Create a Intent send by the notification
    public static Intent makeNotificationIntent(Context context, String msg) {
        Intent intent = new Intent( context, MainActivity.class );
        intent.putExtra( NOTIFICATION_MSG, msg );
        return intent;
    }

    @Override
    public void onLocationChanged(Location location) {

    }
}
