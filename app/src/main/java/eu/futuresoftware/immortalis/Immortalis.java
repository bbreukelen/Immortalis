package eu.futuresoftware.immortalis;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.github.anrwatchdog.ANRWatchDog;

import java.util.HashSet;
import java.util.Set;

/*
 * Immortalis, meaning immortal is created by Boudewijn van Breukelen on April 26th 2019
 * This class will keep the Android app alive in most conditions:
 * Crash: Catched, uses Crashlytics (if initiated after Immortalis) and then restarts the app
 * Background: Pulls app back into foreground
 * Android memory cleaning: Restarts the app
 * ANRs: Will crash the app and restart it after 5 seconds
 *
 * Known scenario's that stop the app:
 *   Press back button/escape key/right mouse key (disables Immortalis)
 *   Force stop app from Settings > Apps (cleans alarms, if you're quick)
 *   Killing app from Android Studio (cleans alarms)
 *
 * Usage:
 * Create an application class extending the Android application MyApplication.
 * Add implementation 'com.github.anrwatchdog:anrwatchdog:1.4.0' to gradle
 * in onCreate:
 *   immortalis = new Immortalis(this, BuildConfig.DEBUG);
 *   registerActivityLifecycleCallbacks(immortalis);
 *   (optional) Fabric.with(this, new Crashlytics());
 * Add getter method getImmortalis() { return immortalis; }
 *
 * Add to Manifest: <receiver android:name=".Immortalis$ImmortalisBroadcastReceiver" />
 * Add to Primary Activity in onCreate: MyApplication.getContext().getImmortalis().appStarted(getIntent());
 * Add to all Activities: public void onBackPressed() { MyApplication.getContext().getImmortalis().onBackPressed(); }
 *
 * On the Immortalis constructor, if the 2nd parameter is true, restarts are disabled (for debugging)
 */

public class Immortalis implements Application.ActivityLifecycleCallbacks {

    private static String TAG = "Immortalis";
    private Context c;
    private int activitiesActive = 0;
    private long activitiesActiveLastChanged = 0;
    private boolean backupMode = false;

    public static int ALARM_SHORT = 0;
    public static int ALARM_LONG = 1;

    private static String MSG_RESPAWN = "RESPAWN";

    public Immortalis(Context c, boolean restartDisabled) {
        this.c = c;
        if (restartDisabled) {
            Log.w(TAG, "Restart disabled");
            return;
        }

        // Start listening for application crashes and hard crash the app so it can be restarted
        // Not adding this part restarts the app only once after which Android cleans the alarms the 2nd crash
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.e(TAG, "Caught a crash... Force killing and waiting for alarm to respawn");
                Log.e("System.err", Log.getStackTraceString(e));
                System.exit(2);
            }
        });

        // Start ANR watchdog
        new ANRWatchDog(2500)
                .setIgnoreDebugger(true)
                .setReportMainThreadOnly()
                .setANRInterceptor(new ANRWatchDog.ANRInterceptor() {
                    @Override
                    public long intercept(long duration) {
                        long ret = 5000 - duration;
                        Log.e("ANRWatchdog", "Detected an ANR of " + duration + "ms. Will crash when hanging for 5000ms in total.");
                        Immortalis.this.resetShortAlarm();
                        return ret;
                    }
                })
                .start();

        // Start alarms
        startAlarms();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //// Public Methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public void appStarted(Intent i) {
        if (i.getBooleanExtra(MSG_RESPAWN, false)) {
            Log.w(TAG, "Respawned by Immortalis ");
        }
    }

    public void onBackPressed() {
        Log.w(TAG, "App stopped with back button press. Immortalis disabled");
        pause();
        System.exit(0);
    }

    public void pause() {
        Log.w(TAG, "Immortalis paused.");
        cancelAlarms();
    }

    public void resume() {
        Log.w(TAG, "Immortalis resumed.");
        startAlarms();
    }

    public void startBackupMode() {
        // Stop timers and start only the backup alarm so we have 30 seconds
        Log.w(TAG, "Immortalis backup mode activated.");
        backupMode = true;
        cancelAlarms();
        startAlarm(ALARM_LONG); // Backup alarm
    }

    public void resetShortAlarm() {
        cancelAlarm(ALARM_SHORT);
        startAlarm(ALARM_SHORT);
    }

    public void resetLongAlarm() {
        cancelAlarm(ALARM_LONG);
        startAlarm(ALARM_LONG);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //// Private Methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void startAlarms() {
        startAlarm(ALARM_SHORT); // Short
        startAlarm(ALARM_LONG); // Long
    }

    private void alarmReceived(int alarmId) {
        //Log.d(TAG, "Alarm received: " + (alarmId == 0 ? "short" : "long"));
        startAlarm(alarmId);
        checkIfStillRunningInForeground();

        // If running in backupmode, start the quick alarm at first backup alarm received
        if (backupMode && alarmId == ALARM_LONG) {
            backupMode = false;
            startAlarm(ALARM_SHORT);
        }
    }

    private void checkIfStillRunningInForeground() {
        // Check if no activities for a while. The lastChanged condition is there to avoid acting
        // acting on something that's still in process. For instance a screen rotation stops the activity
        // and restarts it a short while later. 1500ms is quite a long time, but we don't need it to
        // restart super quick, as long as it will eventually restart and bring the app back to the foreground
        if (activitiesActive == 0 && System.currentTimeMillis() > activitiesActiveLastChanged + 1500) {
            Log.e(TAG, "Not running in foreground. Pulling back to the front.");
            restartApp();
        }
    }

    private void restartApp() {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra(MSG_RESPAWN, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private void startAlarm(int alarmId) {
        PendingIntent pendingIntent = makePendingIntent(alarmId);
        long time = System.currentTimeMillis() + (alarmId == ALARM_SHORT ? 3100 : 30000); // 3.1s and 30s as backup
        int SDK_INT = Build.VERSION.SDK_INT;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            if (SDK_INT < Build.VERSION_CODES.KITKAT) {
                am.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
            } else if (SDK_INT < Build.VERSION_CODES.M) {
                am.setExact(AlarmManager.RTC_WAKEUP, time, pendingIntent);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent);
            }
        } else {
            Log.e(TAG, "Could not get Alarm Manager");
        }
    }

    private PendingIntent makePendingIntent(int alarmId) {
        Intent intent = new Intent(c, ImmortalisBroadcastReceiver.class);
        intent.putExtra("alarmId", alarmId);
        return PendingIntent.getBroadcast(c, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void cancelAlarms() {
        Log.d(TAG, "Cancelling alarms");
        cancelAlarm(ALARM_SHORT);
        cancelAlarm(ALARM_LONG);
    }

    private void cancelAlarm(int alarmId) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(makePendingIntent(alarmId));
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //// ActivityLifeCycleCallback methods - to listen to activities starting and stopping
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

    @Override
    public void onActivityStarted(Activity activity) {
        activitiesActiveLastChanged = System.currentTimeMillis();
        activitiesActive++;
        Log.d(TAG, String.valueOf(activitiesActive) + " activities active");
    }

    @Override
    public void onActivityResumed(Activity activity) { }

    @Override
    public void onActivityPaused(Activity activity) { }

    @Override
    public void onActivityStopped(Activity activity) {
        activitiesActiveLastChanged = System.currentTimeMillis();
        activitiesActive--;
        Log.d(TAG, String.valueOf(activitiesActive) + " activities active");
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

    @Override
    public void onActivityDestroyed(Activity activity) { }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //// Broadcast Receiver - receiving alarms
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class ImmortalisBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Receiver for internal Immortalis alarms and external app signaling to stop/start this app

            // Internal Immortalis alarm?
            Bundle bundle = intent.getExtras();
            if (bundle == null || !bundle.containsKey("alarmId")) return;
            MyApplication.getContext().getImmortalis().alarmReceived(bundle.getInt("alarmId", ALARM_SHORT));
        }
    }
}

