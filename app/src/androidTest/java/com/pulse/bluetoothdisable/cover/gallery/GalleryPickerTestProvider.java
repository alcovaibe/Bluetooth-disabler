package com.pulse.bluetoothdisable.cover.gallery;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only. Its standalone process cannot use Kotlin bundled in the target APK. */
public final class GalleryPickerTestProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "/picked.mp4".equals(uri.getPath()) ? "video/mp4" : "image/jpeg"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final int flags;
        switch (mode) {
            case "r": flags = ParcelFileDescriptor.MODE_READ_ONLY; break;
            case "w":
            case "wt":
                flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
                break;
            default: throw new FileNotFoundException();
        }
        return ParcelFileDescriptor.open(source(uri), flags);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = source(uri);
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    private File source(Uri uri) {
        if (!"/picked.jpg".equals(uri.getPath()) && !"/invalid.jpg".equals(uri.getPath()) && !"/picked.mp4".equals(uri.getPath())) {
            throw new IllegalArgumentException("Unknown dummy fixture");
        }
        return new File(getContext().getCacheDir(), "gallery-picker-" + uri.getLastPathSegment());
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        return source(uri).delete() ? 1 : 0;
    }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
