package com.example.beautify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AssetBlob {
    private AssetBlob() {}

    public static String utf8(Context context, String assetPath) {
        try (InputStream in = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("read asset " + assetPath, e);
        }
    }

    public static Bitmap decodeBitmap(Context context, String assetPath) {
        try (InputStream in = context.getAssets().open(assetPath)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            BeautifyLog.e("AssetBlob", "decodeBitmap " + assetPath, e);
            return null;
        }
    }
}
