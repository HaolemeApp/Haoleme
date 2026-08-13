package com.haoleme.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Small durable cache for cloud snapshots. Large console output stays in files. */
final class AppCacheDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "haoleme-app-cache.db";
    private static final int DATABASE_VERSION = 1;

    AppCacheDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
        db.rawQuery("PRAGMA busy_timeout=3000", null).close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE snapshots (cache_key TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE run_details (run_id TEXT PRIMARY KEY, payload TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_snapshots_updated ON snapshots(updated_at)");
        db.execSQL("CREATE INDEX idx_run_details_updated ON run_details(updated_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version one intentionally has no destructive migration path.
    }

    synchronized String getSnapshot(String key) {
        return getValue("snapshots", "cache_key", key);
    }

    synchronized long getSnapshotUpdatedAt(String key) {
        try (Cursor cursor = getReadableDatabase().query(
                "snapshots", new String[]{"updated_at"}, "cache_key=?",
                new String[]{key}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    synchronized void putSnapshot(String key, String payload, long updatedAt) {
        putValue("snapshots", "cache_key", key, payload, updatedAt);
    }

    synchronized String getRunDetail(String runId) {
        return getValue("run_details", "run_id", runId);
    }

    synchronized void putRunDetail(String runId, String payload, long updatedAt) {
        putValue("run_details", "run_id", runId, payload, updatedAt);
    }

    synchronized void removeRunDetail(String runId) {
        getWritableDatabase().delete("run_details", "run_id=?", new String[]{runId});
    }

    synchronized List<String> snapshotKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        String selection = prefix == null || prefix.isEmpty()
                ? null
                : "cache_key LIKE ? ESCAPE '\\'";
        String[] args = selection == null ? null : new String[]{escapeLike(prefix) + "%"};
        try (Cursor cursor = getReadableDatabase().query(
                "snapshots", new String[]{"cache_key"}, selection, args,
                null, null, null)) {
            while (cursor.moveToNext()) {
                keys.add(cursor.getString(0));
            }
        }
        return keys;
    }

    synchronized void removeSnapshot(String key) {
        getWritableDatabase().delete("snapshots", "cache_key=?", new String[]{key});
    }

    synchronized void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("snapshots", null, null);
            db.delete("run_details", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized void clearRunData() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("snapshots", "cache_key=? OR cache_key LIKE ? ESCAPE '\\'",
                    new String[]{"cached_runs_json", escapeLike("cached_runs_json_") + "%"});
            db.delete("run_details", null, null);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized long approximateBytes() {
        long total = 0L;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(LENGTH(cache_key) + LENGTH(payload)), 0) FROM snapshots", null)) {
            if (cursor.moveToFirst()) total += cursor.getLong(0);
        }
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(LENGTH(run_id) + LENGTH(payload)), 0) FROM run_details", null)) {
            if (cursor.moveToFirst()) total += cursor.getLong(0);
        }
        return total;
    }

    private String getValue(String table, String keyColumn, String key) {
        try (Cursor cursor = getReadableDatabase().query(
                table, new String[]{"payload"}, keyColumn + "=?",
                new String[]{key}, null, null, null, "1")) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private void putValue(String table, String keyColumn, String key, String payload, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put(keyColumn, key);
        values.put("payload", payload == null ? "" : payload);
        values.put("updated_at", updatedAt);
        getWritableDatabase().insertWithOnConflict(
                table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
