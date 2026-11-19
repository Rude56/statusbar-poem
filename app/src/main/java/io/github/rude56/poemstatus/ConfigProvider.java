package io.github.rude56.poemstatus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public final class ConfigProvider extends ContentProvider {
    private static volatile long lastSystemUiCallAt = 0L;
    public static long lastSystemUiCallAt() {
        return lastSystemUiCallAt;
    }

    @Override
    public boolean onCreate() {
        return true;
    }
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!FeatureAudit.PROVIDER_METHOD.equals(method)) {
            return null;
        }
        if (extras == null || !FeatureAudit.PROVIDER_TOKEN.equals(extras.getString("token"))) {
            return null;
        }
        if (getContext() == null) {
            return null;
        }

        try {
            if (FeatureAudit.SCOPE_PACKAGE.equals(getCallingPackage())) {
                lastSystemUiCallAt = android.os.SystemClock.elapsedRealtime();
            }
        } catch (Throwable ignored) {

        }
        String json;
        try {
            json = ConfigStore.readJson(getContext());
        } catch (Throwable t) {
            return null;
        }
        Bundle out = new Bundle();
        out.putString("json", json);
        return out;
    }
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
