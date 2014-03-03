package com.busck.myfacebookfeed;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class MyWallProvider extends ContentProvider {
    private static final String DB_NAME = "MyFacebookWall.db";
    private static final int DB_VERSION = 1;
    private MyDatabaseHelper mDatabaseHelper;
    public static final String CREATE_SQL = "CREATE TABLE " + Contract.TABLE_NAME + "( "
            + Contract.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Contract.MESSAGE_ID + " TEXT, "
            + Contract.MESSAGE + " TEXT, "
            + Contract.FROM_ID + " TEXT, "
            + Contract.FROM_NAME + " TEXT, "
            + Contract.CREATED_TIME + " TEXT, "
            + Contract.TYPE + " TEXT);";
    public static final int ALL_MESSAGES = 1;
    public static final int ONE_MESSAGE = 2;

    private static UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sUriMatcher.addURI(Contract.AUTHORITY, Contract.TABLE_NAME, ALL_MESSAGES);
        sUriMatcher.addURI(Contract.AUTHORITY, Contract.TABLE_NAME + "/#", ONE_MESSAGE);
    }

    public MyWallProvider() {
    }

    public static class Contract {
        public static final String ID = "_id";
        public static final String MESSAGE_ID = "message_id";
        public static final String FROM_ID = "from_id";
        public static final String FROM_NAME = "from_name";
        public static final String TYPE = "type";
        public static final String MESSAGE = "message";
        public static final String CREATED_TIME = "created_time";

        public static final String AUTHORITY = "com.busck.myfacebookfeed.provider";
        public static final String TABLE_NAME = "facebook_messages";
        public static final Uri FACEBOOK_WALL_URI
                = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int deleted = 0;
        switch (sUriMatcher.match(uri)) {
            case ALL_MESSAGES:
                deleted = db.delete(Contract.TABLE_NAME, selection, selectionArgs);
                break;
            case ONE_MESSAGE:
                selectionArgs = new String[]{uri.getLastPathSegment()};
                deleted = db.delete(Contract.TABLE_NAME, Contract.ID + " = ?", selectionArgs);
                break;
        }

        if (deleted > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return deleted;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case ALL_MESSAGES:
                return ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + Contract.TABLE_NAME;
            case ONE_MESSAGE:
                return ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + Contract.TABLE_NAME;
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        long id = -1;
        switch (sUriMatcher.match(uri)) {
            case ALL_MESSAGES:
                id = db.insert(Contract.TABLE_NAME, "", values);
                break;
        }

        Uri newMessage = null;
        if (id != -1) {
            newMessage = Uri.withAppendedPath(uri, String.valueOf(id));
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return newMessage;
    }

    @Override
    public boolean onCreate() {
        mDatabaseHelper = new MyDatabaseHelper(getContext(), DB_NAME, null, DB_VERSION);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
        Cursor cursor = null;
        switch (sUriMatcher.match(uri)) {
            case ALL_MESSAGES:
                cursor = db.query(Contract.TABLE_NAME, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case ONE_MESSAGE:
                selectionArgs = new String[]{uri.getLastPathSegment()};
                cursor = db.query(Contract.TABLE_NAME, projection, Contract.ID + " = ?", selectionArgs, null, null, null);
                break;
        }
        if (cursor != null) {
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int updated = 0;
        switch (sUriMatcher.match(uri)) {
            case ALL_MESSAGES:
                updated = db.update(Contract.TABLE_NAME, values, selection, selectionArgs);
                break;
            case ONE_MESSAGE:
                selectionArgs = new String[]{uri.getLastPathSegment()};
                updated = db.update(Contract.TABLE_NAME, values, Contract.ID + " = ?", selectionArgs);
                break;
        }

        if (updated > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return updated;
    }

    class MyDatabaseHelper extends SQLiteOpenHelper{

        public MyDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL(CREATE_SQL);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {

        }
    }
}
