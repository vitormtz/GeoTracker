package com.example.geotracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "geotracker.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_ROUTES = "routes";
    public static final String COLUMN_ROUTE_ID = "id";
    public static final String COLUMN_ROUTE_NAME = "name";

    public static final String TABLE_ROUTE_POINTS = "route_points";
    public static final String COLUMN_POINT_ID = "id";
    public static final String COLUMN_POINT_ROUTE_ID = "route_id";
    public static final String COLUMN_POINT_LAT = "latitude";
    public static final String COLUMN_POINT_LNG = "longitude";
    public static final String COLUMN_POINT_SEQUENCE = "sequence";

    private static final String CREATE_TABLE_ROUTES =
            "CREATE TABLE " + TABLE_ROUTES + " (" +
                    COLUMN_ROUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_ROUTE_NAME + " TEXT);";

    private static final String CREATE_TABLE_ROUTE_POINTS =
            "CREATE TABLE " + TABLE_ROUTE_POINTS + " (" +
                    COLUMN_POINT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_POINT_ROUTE_ID + " INTEGER, " +
                    COLUMN_POINT_LAT + " REAL, " +
                    COLUMN_POINT_LNG + " REAL, " +
                    COLUMN_POINT_SEQUENCE + " INTEGER, " +
                    "FOREIGN KEY (" + COLUMN_POINT_ROUTE_ID + ") REFERENCES " +
                    TABLE_ROUTES + "(" + COLUMN_ROUTE_ID + "));";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_ROUTES);
        db.execSQL(CREATE_TABLE_ROUTE_POINTS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ROUTE_POINTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ROUTES);
        onCreate(db);
    }

    public long saveRoute(String routeName, List<LatLng> points) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues routeValues = new ContentValues();
        routeValues.put(COLUMN_ROUTE_NAME, routeName);

        long routeId = db.insert(TABLE_ROUTES, null, routeValues);

        int sequence = 0;
        for (LatLng point : points) {
            ContentValues pointValues = new ContentValues();
            pointValues.put(COLUMN_POINT_ROUTE_ID, routeId);
            pointValues.put(COLUMN_POINT_LAT, point.latitude);
            pointValues.put(COLUMN_POINT_LNG, point.longitude);
            pointValues.put(COLUMN_POINT_SEQUENCE, sequence++);

            db.insert(TABLE_ROUTE_POINTS, null, pointValues);
        }

        return routeId;
    }

    public List<RouteInfo> getAllRoutes() {
        List<RouteInfo> routes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ROUTES,
                new String[]{COLUMN_ROUTE_ID, COLUMN_ROUTE_NAME},
                null, null, null, null, COLUMN_ROUTE_NAME + " DESC");

        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROUTE_NAME));

                routes.add(new RouteInfo(id, name));
            } while (cursor.moveToNext());

            cursor.close();
        }

        return routes;
    }

    public List<LatLng> getRoutePoints(long routeId) {
        List<LatLng> points = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ROUTE_POINTS,
                new String[]{COLUMN_POINT_LAT, COLUMN_POINT_LNG},
                COLUMN_POINT_ROUTE_ID + " = ?",
                new String[]{String.valueOf(routeId)},
                null, null, COLUMN_POINT_SEQUENCE);

        if (cursor.moveToFirst()) {
            do {
                double lat = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_POINT_LAT));
                double lng = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_POINT_LNG));

                points.add(new LatLng(lat, lng));
            } while (cursor.moveToNext());

            cursor.close();
        }

        return points;
    }

    public static class RouteInfo {
        private final int id;
        private final String name;

        public RouteInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }
}
