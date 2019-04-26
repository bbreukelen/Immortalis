package eu.futuresoftware.immortalis;

import android.app.Application;

public class MyApplication extends Application {
    private static MyApplication mContext;

    private Immortalis immortalis;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        immortalis = new Immortalis(this, false); // Remove after debugging

        registerActivityLifecycleCallbacks(immortalis);

        //Fabric.with(this, new Crashlytics()); (optional, if you're using Crashlytics)
    }

    public static MyApplication getContext() {
        return mContext;
    }

    public Immortalis getImmortalis() {
        return immortalis;
    }
}