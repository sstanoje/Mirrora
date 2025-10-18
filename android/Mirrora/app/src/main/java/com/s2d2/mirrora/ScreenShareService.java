package com.s2d2.mirrora;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.s2d2.mirrora.connection.ConnectionConfig;
import com.s2d2.mirrora.connection.ConnectionController;
import com.s2d2.mirrora.connection.ConnectionRole;
import com.s2d2.mirrora.connection.DefaultConnectionFactory;
import com.s2d2.mirrora.connection.Room;
import com.s2d2.mirrora.connection.SenderController;
import com.s2d2.mirrora.ui.SharingActivityCompose;
import com.s2d2.mirrora.utils.WebUtils;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenShareService extends Service {

    public static final String ACTION_START = "com.s2d2.mirrora.START";
    public static final String ACTION_STOP  = "com.s2d2.mirrora.STOP";
    public static final String ACTION_SHARING_ENDED = "com.s2d2.mirrora.SHARING_ENDED";

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";
    public static final String EXTRA_SEND_SOUND  = "extra_send_sound";
    public static final String EXTRA_ROOM        = "extra_room";

    private static final String CHANNEL_ID = "screen_share";
    private static final int NOTIF_ID = 1;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile ScreenShareService sInstance;

    private MediaProjection mediaProjection;
    private ConnectionController connection;
    private boolean sendSound;
    private Room room;

    private final java.util.concurrent.ExecutorService teardown =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }

        final String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            startInForeground();

            final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            final Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            sendSound = intent.getBooleanExtra(EXTRA_SEND_SOUND, true);
            room = (Room) intent.getSerializableExtra(EXTRA_ROOM);

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                stopSelf(); return START_NOT_STICKY;
            }

            if (started.compareAndSet(false, true)) {
                initWebRTCAndStartCapture(resultCode, resultData);
            }
            return START_STICKY;

        } else if (ACTION_STOP.equals(action)) {
            // Remove the foreground notification immediately
            removeNotification();

            // Let UI know sharing ended so it can finish itself
            notifySharingEnded();

            // Do teardown off the main thread, then stop the service
            cleanupAsyncThenStopSelf();
            return START_NOT_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            int importance = NotificationManager.IMPORTANCE_MIN;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Screen sharing", importance);
            ch.setDescription("Shows that screen sharing is active");
            ch.setShowBadge(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(ch);
        }

        Intent intent = new Intent(this, SharingActivityCompose.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPI = PendingIntent.getActivity(
                this,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mirrora_transparent)
                .setContentTitle("Mirrora is sharing your screen")
                .setContentText("Tap to return to the app")
                .setContentIntent(contentPI)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false);

        Notification notif = b.build();

        if (Build.VERSION.SDK_INT >= 29) {
            int type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(NOTIF_ID, notif, type);
        } else {
            startForeground(NOTIF_ID, notif);
        }
    }

    /** Remove the foreground notification now (and cancel explicitly for safety). */
    private void removeNotification() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                // On pre-24, true means remove the notification
                stopForeground(true);
            }
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        } catch (Throwable ignore) {}
    }

    private void cleanupAsyncThenStopSelf() {
        // Take local copies and clear fields quickly on the main thread
        final ConnectionController c = connection;
        final MediaProjection mp = mediaProjection;
        connection = null;
        mediaProjection = null;

        teardown.execute(() -> {
            try {
                try { if (c != null) c.stop(); } catch (Throwable ignore) {}
                try { if (mp != null) mp.stop(); } catch (Throwable ignore) {}
            } finally {
                stopSelf();
            }
        });
    }

    private void notifySharingEnded() {
        try {
            Intent i = new Intent(ACTION_SHARING_ENDED).setPackage(getPackageName());
            sendBroadcast(i);
        } catch (Throwable ignore) {}
    }

    private void initWebRTCAndStartCapture(int resultCode, Intent mediaProjectionPermissionData) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, mediaProjectionPermissionData);
        if (mediaProjection == null) {
            stopSelf(); return;
        }

        ConnectionConfig cfg = new ConnectionConfig.Builder()
                .roomCode(room.getRoomNumber())
                .signalingUrl(WebUtils.SERVER_IP_ADDRESS)
                .iceServers(defaultIceServers())
                .build();

        connection = DefaultConnectionFactory.getInstance()
                .create(ConnectionRole.SENDER, cfg, this);

        ((SenderController) connection).attachMediaProjection(mediaProjection, sendSound);
        connection.start();
    }

    private static List<PeerConnection.IceServer> defaultIceServers() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        return list;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeNotification();
        started.set(false);
        sInstance = null;
        teardown.shutdown();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    public static void start(Context ctx, int resultCode, Intent data, boolean sendSound, Room room) {
        Intent i = new Intent(ctx, ScreenShareService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
                .putExtra(EXTRA_SEND_SOUND, sendSound)
                .putExtra(EXTRA_ROOM, room);
        ContextCompat.startForegroundService(ctx, i);
    }

    /** IMPORTANT: use this so ACTION_STOP is delivered to onStartCommand */
    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, ScreenShareService.class).setAction(ACTION_STOP);
        ContextCompat.startForegroundService(ctx, i);
    }

    @Nullable
    public static String getLocalOfferSdp() {
        if (sInstance == null) return null;
        if (sInstance.connection instanceof SenderController) {
            return ((SenderController) sInstance.connection).getLocalOfferSdp();
        }
        return null;
    }

    @NonNull
    public static List<IceCandidate> getLocalIceCandidatesSnapshot() {
        if (sInstance == null) return Collections.emptyList();
        if (sInstance.connection instanceof SenderController) {
            return ((SenderController) sInstance.connection).getLocalIceCandidatesSnapshot();
        }
        return Collections.emptyList();
    }

    public static void applyRemoteAnswer(@NonNull String sdp) {
        if (sInstance == null) return;
        if (sInstance.connection instanceof SenderController) {
            ((SenderController) sInstance.connection).applyRemoteAnswer(sdp);
        }
    }

    public static void addRemoteIce(@NonNull IceCandidate c) {
        if (sInstance == null) return;
        if (sInstance.connection instanceof SenderController) {
            ((SenderController) sInstance.connection).addRemoteIce(c);
        }
    }
}
