package com.busck.myfacebookfeed;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.UiLifecycleHelper;
import com.facebook.widget.LoginButton;
import com.facebook.widget.ProfilePictureView;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;


public class MainActivity extends FragmentActivity {

    private Uri mMyPhotoUri;
    private int PHOTO_REQUEST_CODE = 101;
    private static final String ACTION_UPDATE_FROM_WALL = "com.busck.myfacebookfeed.action.UPDATE_FROM_WALL";

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new FacebookLoginFragment())
                    .commit();
        }

        Intent intent = new Intent(this, SyncFeedService.class);
        intent.setAction(ACTION_UPDATE_FROM_WALL);
        alarmIntent = PendingIntent.getService(this, 0, intent, 0);
        alarmMgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, alarmIntent);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshFeed(View view) {
        SyncFeedService.startFeedUpdate(this);

    }

    public void postStatus(View view) {
        requestExtraPermissions();

        EditText statusField = (EditText) findViewById(R.id.text_to_post);
        String status = statusField.getText().toString();
        if(status !=null){
        SyncFeedService.startStatusUpload(this, status);
        }
    }

    public void postPhoto(View view) {
        Intent intent = new Intent (MediaStore.ACTION_IMAGE_CAPTURE);
        mMyPhotoUri = createPhotoUri(this);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, mMyPhotoUri);

        startActivityForResult(intent, PHOTO_REQUEST_CODE);
    }

    private void requestExtraPermissions(){
        Session session = Session.getActiveSession();
        if(session.isOpened()){
            session.requestNewPublishPermissions(
                    new Session.NewPermissionsRequest(this, "publish_stream"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            SyncFeedService.startPhotoUpload(this, mMyPhotoUri);
        }

        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

    private Uri createPhotoUri(Context context) {
        File mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        StringBuilder stringBuilder = new StringBuilder();
        return Uri.fromFile(new File(stringBuilder.append(mediaStorageDir.getPath())
                .append(File.separator)
                .append("IMG_")
                .append(timeStamp)
                .append(".jpg").toString()));
    }



    /**
     * A placeholder fragment containing a simple view.
     */
    public static class FacebookLoginFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

        private Session.StatusCallback mCallback = new Session.StatusCallback(){
            @Override
            public void call(Session session, SessionState state, Exception exception){
                onSessionStateChange(session, state, exception);
            }
        };
        private UiLifecycleHelper mUiHelper;
        private SimpleCursorAdapter mListAdapter;


        public FacebookLoginFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mUiHelper = new UiLifecycleHelper(getActivity(), mCallback);
            mUiHelper.onCreate(savedInstanceState);
        }

        @Override
        public void onResume() {
            super.onResume();
            Session session = Session.getActiveSession();
            if(session!= null && (session.isOpened() || session.isClosed())){
                onSessionStateChange(session, session.getState(), null);
            }

   mUiHelper.onResume();
        }

        private void onSessionStateChange(Session session, SessionState state, Exception e) {
            if(session.isOpened()){
                Toast.makeText(getActivity(), "You are logged in to Facebook", Toast.LENGTH_LONG).show();
            } else if (session.isClosed()){
                SyncFeedService.startLogout(getActivity());
                Toast.makeText(getActivity(), "You are logged out from Facebook", Toast.LENGTH_LONG).show();
            }


        }



        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mUiHelper.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onPause() {
            super.onPause();
            mUiHelper.onPause();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            mUiHelper.onDestroy();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            mUiHelper.onSaveInstanceState(outState);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.facebook_login, container, false);
            LoginButton loginButton = (LoginButton) rootView.findViewById(R.id.facebookLoginButton);
            loginButton.setFragment(this);
            loginButton.setReadPermissions("user_status", "user_friends",
                    "friends_status", "read_stream");

            mListAdapter = new SimpleCursorAdapter(getActivity(),
                    R.layout.listview_item,
                    null,
                    new String[]{MyWallProvider.Contract.FROM_NAME,
                           MyWallProvider.Contract.MESSAGE,  MyWallProvider.Contract.FROM_ID},
                    new int[]{R.id.from_name, R.id.message, R.id.profile_picture}, 0);
            mListAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                @Override
                public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                    if (columnIndex == cursor.getColumnIndex( MyWallProvider.Contract.FROM_ID)) {
                        ((ProfilePictureView) view).setProfileId(cursor.getString(columnIndex));
                        return true;
                    }
                    return false;
                }
            });

            ListView facebookMessages = (ListView) rootView.findViewById(R.id.post_listview);
            facebookMessages.setAdapter(mListAdapter);

            getLoaderManager().initLoader(0, null, this);
            return rootView;
        }
        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(),
                    MyWallProvider.Contract.FACEBOOK_WALL_URI,
                    new String[]{MyWallProvider.Contract.ID,
                            MyWallProvider.Contract.FROM_NAME,
                            MyWallProvider.Contract.FROM_ID,
                            MyWallProvider.Contract.MESSAGE},
                    null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> objectLoader, Cursor cursor) {
            mListAdapter.swapCursor(cursor);
            Log.d("FacebookApp", "Loader finished: " + cursor.getCount());
        }

        @Override
        public void onLoaderReset(Loader<Cursor> objectLoader) {
            Log.d("facebookApp", "Loader reset!");
        }


    }

}
