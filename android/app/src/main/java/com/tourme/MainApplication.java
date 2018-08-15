package de.chillkroetenteich.expaths2;

import android.app.Application;

import com.facebook.react.ReactApplication;

// https://github.com/facebook/react-native-fbsdk
import com.facebook.CallbackManager;
import com.facebook.FacebookSdk;
import com.facebook.reactnative.androidsdk.FBSDKPackage;
import com.facebook.appevents.AppEventsLogger;

import co.apptailor.googlesignin.RNGoogleSigninPackage;
import com.agontuk.RNFusedLocation.RNFusedLocationPackage;
import com.oblador.vectoricons.VectorIconsPackage;
import com.airbnb.android.react.maps.MapsPackage;
import io.invertase.firebase.RNFirebasePackage;
import io.invertase.firebase.RNFirebasePackage;
import io.invertase.firebase.auth.RNFirebaseAuthPackage;
import io.invertase.firebase.firestore.RNFirebaseFirestorePackage;
import io.invertase.firebase.fabric.crashlytics.RNFirebaseCrashlyticsPackage;
import io.invertase.firebase.analytics.RNFirebaseAnalyticsPackage;
import com.oblador.vectoricons.VectorIconsPackage;
import com.airbnb.android.react.maps.MapsPackage;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;
import com.agontuk.RNFusedLocation.RNFusedLocationPackage;

import java.util.Arrays;
import java.util.List;

public class MainApplication extends Application implements ReactApplication {

    private static CallbackManager mCallbackManager = CallbackManager.Factory.create();

    protected static CallbackManager getCallbackManager() {
        return mCallbackManager;
    }

    private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
        @Override
        public boolean getUseDeveloperSupport() {
            return BuildConfig.DEBUG;
        }

        @Override
        protected List<ReactPackage> getPackages() {
            return Arrays.<ReactPackage>asList(
                    new MainReactPackage(),
                    new FBSDKPackage(mCallbackManager),
                    new RNGoogleSigninPackage(),
                    new RNFusedLocationPackage(),
                    new VectorIconsPackage(),
                    new MapsPackage(),
                    new RNFirebasePackage(),
                    new RNFirebaseAuthPackage(),
                    new RNFirebaseFirestorePackage(),
                    new RNFirebaseCrashlyticsPackage(),
                    new RNFirebaseAnalyticsPackage()
            );
        }


    };

    @Override
    public ReactNativeHost getReactNativeHost() {
        return mReactNativeHost;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SoLoader.init(this, /* native exopackage */ false);
        FacebookSdk.sdkInitialize(getApplicationContext());
    }
}
