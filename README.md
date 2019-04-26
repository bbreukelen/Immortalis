Immortalis, meaning immortal is created by Boudewijn van Breukelen on April 26th 2019
This class will keep the Android app alive in most conditions:
Crash: Catched, uses Crashlytics (if initiated after Immortalis) and then restarts the app
Background: Pulls app beck into foreground
Android memory cleaning: Restarts the app

Known scenario's that stop the app:
  Press back button/escape key/right mouse key (disables Immortalis)
  Force stop app from Settings > Apps (cleans alarms, if you're quick)
  Killing app from Android Studio (cleans alarms)

Usage:
Create an application class extending the Android application MyApplication.
in onCreate:
  immortalis = new Immortalis(this, BuildConfig.DEBUG);
  registerActivityLifecycleCallbacks(immortalis);
  (optional) Fabric.with(this, new Crashlytics());
Add getter method getImmortalis() { return immortalis; }

Add to Manifest: <receiver android:name=".Immortalis$ImmortalisBroadcastReceiver" />
Add to Primary Activity in onCreate: MyApplication.getContext().getImmortalis().appStarted(getIntent());
Add to all Activities: public void onBackPressed() { MyApplication.getContext().getImmortalis().onBackPressed(); }

On the Immortalis constructor, if the 2nd parameter is true, restarts are disabled (for debugging)
