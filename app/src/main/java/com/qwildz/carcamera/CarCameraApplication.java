package com.qwildz.carcamera;

import android.app.Application;
import android.content.Context;

import com.qwildz.blunolibrary.BlunoLibrary;

import timber.log.Timber;

/**
 * Created by qwildz on 20/01/2017.
 */

public class CarCameraApplication extends Application {

    BlunoLibrary blunoLibrary;

    public static BlunoLibrary getBluno(Context context) {
        CarCameraApplication application = (CarCameraApplication) context.getApplicationContext();
        return application.blunoLibrary;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        blunoLibrary = new BlunoLibrary(this);
    }
}
