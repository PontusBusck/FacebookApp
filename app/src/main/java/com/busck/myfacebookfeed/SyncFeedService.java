package com.busck.myfacebookfeed;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class SyncFeedService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_UPDATE_FROM_WALL = "com.busck.myfacebookfeed.action.UPDATE_FROM_WALL";
    private static final String ACTION_USER_LOGOUT = "com.busck.myfacebookfeed.action.USER_LOGOUT";
    private static final String ACTION_POST_PHOTO = "com.busck.myfacebookfeed.action.POST_PHOTO";
    private static final String FACEBOOK_PREFS = "facebook_settings";
    private static final String SINCE_VALUE = "SinceValue";
    private static final String ACTION_POST_STATUS = "com.busck.myfacebookfeed.action.POST_STATUS";


    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startFeedUpdate(Context context) {
        Intent intent = new Intent(context, SyncFeedService.class);
        intent.setAction(ACTION_UPDATE_FROM_WALL);
        context.startService(intent);
    }

    public static void startLogout(Context context) {
        Intent intent = new Intent(context, SyncFeedService.class);
        intent.setAction(ACTION_USER_LOGOUT);
        context.startService(intent);
    }

    public static void startPhotoUpload(Context context, Uri photoUri){
        Intent intent = new Intent(context, SyncFeedService.class);
        intent.setAction(ACTION_POST_PHOTO);
        intent.setData(photoUri);
        context.startService(intent);
    }

    public static void startStatusUpload(Context context, String status) {
        Intent intent = new Intent(context, SyncFeedService.class);
        intent.setAction(ACTION_POST_STATUS);
        intent.putExtra("StatusUpdateString", status);
        context.startService(intent);

    }
    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */


    public SyncFeedService() {
        super("SyncFeedService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_UPDATE_FROM_WALL.equals(action)) {
                handleActionUpdateFromWall();
            } else if (ACTION_POST_PHOTO.equals(action)) {
                handlePhotoUpload(intent.getData());
            } else if (ACTION_USER_LOGOUT.equals(action)) {
                handleActionLogout();
            } else if (ACTION_POST_STATUS.equals(action)) {
                handleStatusUpdate(intent.getStringExtra("StatusUpdateString"));
            }
        }
    }

    private void handleStatusUpdate(String status) {
            Request request = Request.newStatusUpdateRequest(
                    Session.getActiveSession(), status,
                    new Request.Callback() {
                        @Override
                        public void onCompleted(Response response) {
                            if (response.getError() == null)
                                Log.d("noError", "Status Updated");
                        }
                    });
            request.executeAndWait();
    }

    private void handleActionLogout() {
        getContentResolver()
                .delete(MyWallProvider.Contract.FACEBOOK_WALL_URI,
                        null, null);
        SharedPreferences preferences
                = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
        preferences.edit().remove(SINCE_VALUE).apply();
    }

    private void handlePhotoUpload(Uri photoUri) {
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        if(session != null && isOpened && photoUri != null){
            try {
                ContentResolver resolver = getContentResolver();
                Bitmap bitmap = BitmapFactory.decodeStream(resolver.openInputStream(photoUri));
                Request request = Request.newUploadPhotoRequest(session, bitmap, new Request.Callback(){
                    @Override
                    public void onCompleted(Response response) {
                        Log.d("PhotoUploadResponse", "Response: " + response.getError());
                    }
                });
                Response response = request.executeAndWait();
                GraphObject graphObject = response.getGraphObject();
                if(graphObject != null){
                    Log.d("MyGraphObject", graphObject.toString());
                }else{
                    Log.d("wrong", "Response: " + response);
                }

            } catch (FileNotFoundException e){
                Log.e("wrong", "Error uploading photo to Facebook!");
            }
        }
    }

    private void handleActionUpdateFromWall() {
        Session session = Session.getActiveSession();
        boolean isOpened = session.isOpened();
        if(session != null && isOpened){
            SharedPreferences preferences = getSharedPreferences(FACEBOOK_PREFS, MODE_PRIVATE);
            long sinceValue = preferences.getLong(SINCE_VALUE, -1);
            Bundle parameters = new Bundle();
            parameters.putString("fields", "id,from,message,type");
            String graphPath = "me/home";
            if(sinceValue > 0){
                parameters.putLong("since", sinceValue);
            }

            Request request = new Request(session, graphPath, parameters, HttpMethod.GET);
            Response response = request.executeAndWait();
            Log.d("whatwentwrong", "Response: " + response.getError());

            GraphObject graphObject = response.getGraphObject();
            Log.d("whatwentwrong2", "Got graphObject: " + graphObject);
            if (graphObject != null){
                long currentTimeInSeconds = System.currentTimeMillis() / 1000;
                preferences.edit().putLong(SINCE_VALUE, currentTimeInSeconds).apply();

                JSONArray dataArray = (JSONArray) graphObject.getProperty("data");
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject wallMessage = null;
                    try{
                        wallMessage = dataArray.getJSONObject(i);
                        storeWallMessage(wallMessage);
                    } catch(JSONException e){
                        Log.e("JSONobjectWentWrong", "wentWrong", e);
                    }
                }
            }
        }
    }

    private void storeWallMessage(JSONObject wallMessage) throws JSONException{

        String messageId = wallMessage.getString("id");
        JSONObject from = wallMessage.getJSONObject("from");
        String fromId = from.getString("id");
        String fromName = from.getString("name");
        String type = wallMessage.getString("type");
        String message = wallMessage.getString("message");
        String createdTime = wallMessage.getString("created_time");

        ContentValues values = new ContentValues();
        values.put(MyWallProvider.Contract.MESSAGE_ID, messageId);
        values.put(MyWallProvider.Contract.FROM_ID, fromId);
        values.put(MyWallProvider.Contract.FROM_NAME, fromName);
        values.put(MyWallProvider.Contract.MESSAGE, message);
        values.put(MyWallProvider.Contract.TYPE, type);
        values.put(MyWallProvider.Contract.CREATED_TIME, createdTime);
        Uri newMessage = getContentResolver()
                .insert(MyWallProvider.Contract.FACEBOOK_WALL_URI,
                        values);
        if (newMessage == null) {
            Log.e("facebookApp", "Invalid message!");
        } else {
            Log.d("facebookApp", "Inserted: " + newMessage);
        }
    }



}
