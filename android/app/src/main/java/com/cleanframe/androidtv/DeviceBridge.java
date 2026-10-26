package com.cleanframe.androidtv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Android side of FrameDevice (see src/main/web/device.js). Every method returns
 * synchronously: "" means it worked, "missing" means the app is not installed, and any
 * other string is an error to show.
 */
final class DeviceBridge {
    /* The box's usual Home screens, most likely first. Mi Box uses Android TV Home. */
    private static final List<String> STOCK_HOMES = Arrays.asList(
            "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher", "com.mitv.tvhome", "com.android.launcher3");
    /* Boot-time placeholders that also claim to be Home; never open these. */
    private static final Set<String> NOT_HOMES = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.android.tv.settings", "com.android.provision"));
    private static final int ICON_SIZE = 192;

    private final Activity activity;
    private final PackageManager pm;
    private final Map<String, byte[]> icons = new HashMap<>();

    DeviceBridge(Activity activity) {
        this.activity = activity;
        this.pm = activity.getPackageManager();
    }

    @JavascriptInterface
    public String apps() {
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        seen.add(activity.getPackageName());
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            String pkg = r.activityInfo.packageName;
            if (!seen.add(pkg)) continue;
            try {
                out.put(new JSONObject().put("appId", pkg).put("name", String.valueOf(r.loadLabel(pm))));
            } catch (JSONException ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean installed(String pkg) {
        return pkg != null && launchIntent(pkg) != null;
    }

    @JavascriptInterface
    public String launch(String pkg) {
        Intent intent = pkg == null ? null : launchIntent(pkg);
        if (intent == null) return "missing";
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return start(intent);
    }

    @JavascriptInterface
    public String home() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo pick = null;
        int best = Integer.MAX_VALUE;
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || NOT_HOMES.contains(pkg)) continue;
            int rank = STOCK_HOMES.indexOf(pkg);
            if (rank < 0) rank = STOCK_HOMES.size();
            if (rank < best) { best = rank; pick = r; }
        }
        if (pick == null) return "The box's usual Home screen could not be found.";
        Intent intent = new Intent(query)
                .setComponent(new ComponentName(pick.activityInfo.packageName, pick.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(intent);
    }

    @JavascriptInterface
    public String settings() {
        return start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** PNG bytes for an installed app's icon, served to the page at /appicon/<package>.png. */
    byte[] iconPng(String pkg) {
        if (!pkg.matches("[A-Za-z0-9._]{1,160}")) return null;
        synchronized (icons) {
            if (icons.containsKey(pkg)) return icons.get(pkg);
        }
        byte[] png = null;
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            Bitmap bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            d.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
            d.draw(canvas);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            bitmap.recycle();
            png = bytes.toByteArray();
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
        synchronized (icons) { icons.put(pkg, png); }
        return png;
    }

    private Intent launchIntent(String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    private String start(Intent intent) {
        try {
            activity.startActivity(intent);
            return "";
        } catch (ActivityNotFoundException e) {
            return "missing";
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            return reason == null || reason.isEmpty() ? e.getClass().getSimpleName() : reason;
        }
    }
}
