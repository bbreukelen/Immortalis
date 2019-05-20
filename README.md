#Immortalis

Immortalis means immortal and can be used to keep an Android app alive and in the foreground 
whatever the circumstances.

Immortalis will recover the app after the following events:
+ A crash
+ App moved to the background
+ Killed by memory cleaning
+ ANR: Application not responding (locking main thread)

#### Crashes
On a crash, the app will restart after a few seconds.  
When working with Fabric Crashlytics, the crash log will still be handled and reported.

#### Kill command
When the app is killed by another process or from the commandline, it will recover.  

#### Moving app to background
When a user presses the home button or opens the task manager, or when another app presents itself
to the foreground, the app will automatically push itself to the front after a few seconds.
This can be overruled by pressing the Android back button in the current setup.
This behaviour can be modified obviously. 

#### ANRs
If the app is locking the main thread for several seconds, it will force stop and restart.
The ANR will be logged to crashlytics if setup.

#### What it cannot recover from
Immortalis uses alarms to recover from some events. When these alarms are cleared alongside the app,
it will not be able to recover. Alarms are cleared in the following situations:  
+ The app data is cleared from the System > Apps menu: this stops the app and removes all alarms
+ The app is force killed from the System > Apps menu: this also removes all alarms 
+ The Android Studio "stop" button also stops alarms.  


#### Usage
Use the code in this repository as an example.  

An application class extending the Android application must be created with the following:  
````
private static MyApplication mContext;
private Immortalis immortalis;
    
@Override
public void onCreate() {
    super.onCreate();
    mContext = this;
    immortalis = new Immortalis(this, false); // Change false to true to disable Immortalis
    registerActivityLifecycleCallbacks(immortalis);
    Fabric.with(this, new Crashlytics()); // optional, if you're using Crashlytics
}

public static MyApplication getContext() {
    return mContext;
}
    
public Immortalis getImmortalis() {
    return immortalis;
}
````  
          
Add to Manifest:  
`````
<receiver android:name=".Immortalis$ImmortalisBroadcastReceiver" />
`````
  
Add to Launcher Activity in onCreate before anything else: 
````
MyApplication.getContext().getImmortalis().appStarted(getIntent());
````

If you wish to disable Immortalis on Back Pressed, add to all Activities:  
````
public void onBackPressed() { 
    MyApplication.getContext().getImmortalis().onBackPressed();
}
````

#### Donate
If you like this code, please consider donating. This is optional of course.  
[![paypal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://paypal.me/bbreukelen)
