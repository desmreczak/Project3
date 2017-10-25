package com.example.dominik.project3;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;

/**
 * Created by Dominik on 10/15/2017.
 */

public class ActivityRecognizedService extends IntentService {

    public ActivityRecognizedService() {
        super("ActivityRecognizedService");
    }

    public ActivityRecognizedService(String name) {
        super(name);
    }

    public static String RESULT = "result";
    @Override
    protected void onHandleIntent(Intent intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity activity = handleDetectedActivities(result.getProbableActivities());
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(MainActivity.ACTIVITY_RESULT);
            broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
            broadcastIntent.putExtra(RESULT, activity);
            sendBroadcast(broadcastIntent);
        }
    }

    private DetectedActivity  handleDetectedActivities(List<DetectedActivity> probableActivities) {
        DetectedActivity def = new DetectedActivity(DetectedActivity.STILL, 100);
        int currentMax = -100;
        for (DetectedActivity activity : probableActivities) {
            switch (activity.getType()) {
                case DetectedActivity.IN_VEHICLE: {
                    Log.i("ActivityRecogition", "In Vehicle: " + activity.getConfidence());
                    break;
                }
                case DetectedActivity.ON_BICYCLE: {
                    Log.i("ActivityRecogition", "On Bicycle: " + activity.getConfidence());
                    break;
                }
                case DetectedActivity.ON_FOOT: {
                    Log.i("ActivityRecogition", "On Foot: " + activity.getConfidence());
                    break;
                }
                case DetectedActivity.RUNNING: {
                    Log.i("ActivityRecogition", "Running: " + activity.getConfidence());
                    if (activity.getConfidence() > currentMax) {
                        currentMax = activity.getConfidence();
                        def = activity;
                    }
                    break;
                }
                case DetectedActivity.STILL: {
                    Log.i("ActivityRecogition", "Still: " + activity.getConfidence());
                    if (activity.getConfidence() > currentMax) {
                        currentMax = activity.getConfidence();
                        def = activity;
                    }
                    break;
                }
                case DetectedActivity.TILTING: {
                    Log.i("ActivityRecogition", "Tilting: " + activity.getConfidence());
                    break;
                }
                case DetectedActivity.WALKING: {
                    Log.i("ActivityRecogition", "Walking: " + activity.getConfidence());
                    if (activity.getConfidence() >= 75) {
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
                        builder.setContentText("Are you walking?");
                        builder.setSmallIcon(R.mipmap.ic_launcher);
                        builder.setContentTitle(getString(R.string.app_name));
                        NotificationManagerCompat.from(this).notify(0, builder.build());
                    }
                    if (activity.getConfidence() > currentMax) {
                        currentMax = activity.getConfidence();
                        def = activity;
                    }
                    break;
                }
                case DetectedActivity.UNKNOWN: {
                    Log.i("ActivityRecogition", "Unknown: " + activity.getConfidence());
                    break;
                }
            }

        }
        return def;
    }
}

