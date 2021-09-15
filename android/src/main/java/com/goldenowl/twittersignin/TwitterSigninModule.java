package com.goldenowl.twittersignin;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.google.gson.Gson;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.SessionManager;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterAuthToken;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.core.models.User;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Response;

public class TwitterSigninModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public static String TAG = "RNTwitterSignIn";
    private final ReactApplicationContext reactContext;
    TwitterAuthClient twitterAuthClient;

    public TwitterSigninModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @ReactMethod
    public void init(String consumerKey, String consumerSecret, Promise promise) {
        TwitterConfig config = new TwitterConfig.Builder(this.reactContext)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig(consumerKey, consumerSecret))
                .debug(true)
                .build();
        Twitter.initialize(config);
        WritableMap map = Arguments.createMap();
        promise.resolve(map);
    }

    @ReactMethod
    public void logIn(final Promise promise) {
        twitterAuthClient = new TwitterAuthClient();
        twitterAuthClient.authorize(getCurrentActivity(), new com.twitter.sdk.android.core.Callback<TwitterSession>() {
            @Override
            public void success(Result<TwitterSession> result) {
                final TwitterSession session = result.data;
                TwitterAuthToken twitterAuthToken = session.getAuthToken();
                final WritableMap map = Arguments.createMap();
                map.putString("authToken", twitterAuthToken.token);
                map.putString("authTokenSecret", twitterAuthToken.secret);
                map.putString("name", session.getUserName());
                map.putString("userID", Long.toString(session.getUserId()));
                map.putString("userName", session.getUserName());

                //Getting the account service of the user logged in
                TwitterCore.getInstance().getApiClient(session).getAccountService().verifyCredentials(true, false, true).enqueue(new retrofit2.Callback<User>() {
                    @Override
                    public void onResponse(Call<User> call, Response<User> response) {
                        if (response.isSuccessful()){
                            User user = response.body();
                            Object x = user;
                            final Gson gson = new Gson();
                            final String convertedUser = gson.toJson(x);
                            WritableMap returnMap = null;
                            try {
                                returnMap = convertJsonToMap(new JSONObject(convertedUser));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            map.putMap("user", returnMap);
                            promise.resolve(map);
                        }
                    }

                    @Override
                    public void onFailure(Call<User> call, Throwable t) {
                        map.putString("user", "COULD_NOT_FETCH");
                        promise.reject(
                                "COULD_NOT_FETCH",
                                map.toString(),
                                new Exception("Failed to obtain userr", t));
                    }
                });
            }

            @Override
            public void failure(TwitterException exception) {
                promise.reject("USER_CANCELLED", exception.getMessage(), exception);
            }
        });
    }

    @ReactMethod
    public void logOut() {

        TwitterCore instance = TwitterCore.getInstance();
        SessionManager<TwitterSession> sessionManager = instance.getSessionManager();
        Map<Long, TwitterSession> sessions = sessionManager.getSessionMap();
        System.out.println("TWITTER SEESIONS " + +sessions.size());
        Set<Long> sessids = sessions.keySet();
        for (Long sessid : sessids) {
            System.out.println("TWITTER SESSION CLEARING " + sessid);
            instance.getSessionManager().clearSession(sessid);
        }

        sessionManager
                .clearActiveSession();
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    @Override
    public void onActivityResult(Activity currentActivity, int requestCode, int resultCode, Intent data) {
        if (twitterAuthClient != null && twitterAuthClient.getRequestCode() == requestCode) {
            twitterAuthClient.onActivityResult(requestCode, resultCode, data);
        }
    }

    public WritableMap convertJsonToMap(JSONObject jsonObject) throws JSONException {
        WritableMap map = new WritableNativeMap();

        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.putMap(key, convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.putArray(key, convertJsonToArray((JSONArray) value));
                if (("option_values").equals(key)) {
                    map.putArray("options", convertJsonToArray((JSONArray) value));
                }
            } else if (value instanceof Boolean) {
                map.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                map.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                map.putDouble(key, (Double) value);
            } else if (value instanceof String) {
                map.putString(key, (String) value);
            } else {
                map.putString(key, value.toString());
            }
        }
        return map;
    }

    public WritableArray convertJsonToArray(JSONArray jsonArray) throws JSONException {
        WritableArray array = new WritableNativeArray();

        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                array.pushMap(this.convertJsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                array.pushArray(convertJsonToArray((JSONArray) value));
            } else if (value instanceof Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof String) {
                array.pushString((String) value);
            } else {
                array.pushString(value.toString());
            }
        }
        return array;
    }
}
