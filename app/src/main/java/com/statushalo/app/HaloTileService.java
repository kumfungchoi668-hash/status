package com.statushalo.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class HaloTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override public void onClick() {
        super.onClick();
        StatusOverlayService service = StatusOverlayService.instance;
        if (service != null) {
            service.setOverlayEnabled(!service.isOverlayEnabled());
            refresh();
            return;
        }

        Intent i;
        if (Settings.canDrawOverlays(this)) {
            i = new Intent(this, MainActivity.class);
        } else {
            i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) return;
        StatusOverlayService service = StatusOverlayService.instance;
        boolean enabled = service != null && service.isOverlayEnabled();
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Status Halo");
        tile.updateTile();
    }
}
