// app/src/main/java/com/s2d2/mirrora/connection/SenderController.java
package com.s2d2.mirrora.connection;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SenderController implements ConnectionController {

    private final Context app;
    private final ConnectionConfig cfg;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private SurfaceTextureHelper surfaceHelper;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private JavaAudioDeviceModule audioDeviceModule;

    private android.media.projection.MediaProjection mediaProjection;
    private boolean sendSound;

    private final List<IceCandidate> localIce = Collections.synchronizedList(new ArrayList<>());
    private String localOfferSdp;
    private ScreenCapturerAndroid capturer;

    private volatile boolean connected;

    public SenderController(Context ctx, ConnectionConfig cfg) {
        this.app = ctx.getApplicationContext();
        this.cfg = cfg;
    }

    @Override
    public void start() {
        if (connected) return;
        if (mediaProjection == null) {
            throw new IllegalStateException("mediaProjection not set; call attachMediaProjection(...) before start().");
        }

        // 1) Init WebRTC
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions()
        );

        eglBase = EglBase.create();
        DefaultVideoEncoderFactory enc = new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory dec = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        JavaAudioDeviceModule.Builder builder = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false);

        Toast.makeText(app, "SOUND: " + sendSound, Toast.LENGTH_SHORT).show();
        if (sendSound) {
            builder.setMediaProjection(mediaProjection)
                    .usePlaybackCapture(true)
                    .setUseStereoInput(true);
        }
        audioDeviceModule = builder.createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(enc)
                .setVideoDecoderFactory(dec)
                .createPeerConnectionFactory();

        if (factory == null) throw new IllegalStateException("PeerConnectionFactory creation failed");

        // 2) PeerConnection
        PeerConnection.RTCConfiguration pcCfg = new PeerConnection.RTCConfiguration(this.cfg.iceServers);
        pcCfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(pcCfg, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate c) { synchronized (localIce) { localIce.add(c); } }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState s) {}
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
            @Override public void onAddStream(MediaStream s) {}
            @Override public void onRemoveStream(MediaStream s) {}
            @Override public void onDataChannel(org.webrtc.DataChannel dc) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] ms) {}
            @Override public void onRemoveTrack(RtpReceiver receiver) { PeerConnection.Observer.super.onRemoveTrack(receiver); }
            @Override public void onTrack(RtpTransceiver t) {}
        });

        if (peerConnection == null) throw new IllegalStateException("createPeerConnection returned null");

        // 3) Screen capturer -> video source/track
        videoSource = factory.createVideoSource(true);
        surfaceHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.getEglBaseContext());

        Log.d("SenderController", "MediaProjection: " + mediaProjection);

        capturer = new ScreenCapturerAndroid(
                mediaProjection,
                new android.media.projection.MediaProjection.Callback() { @Override public void onStop() { stop(); } });

        capturer.initialize(surfaceHelper, app, videoSource.getCapturerObserver());

        DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int[] sz = fitWithin(dm.widthPixels, dm.heightPixels, 1280, 720);
        int width  = sz[0];
        int height = sz[1];
        int fps    = 50;
        capturer.startCapture(width, height, fps);

        videoTrack = factory.createVideoTrack("screen", videoSource);
        videoTrack.setEnabled(true);
        RtpSender vSender = peerConnection.addTrack(videoTrack);
        if (vSender != null) {
            RtpParameters p = vSender.getParameters();
            if (p.encodings != null && !p.encodings.isEmpty()) {
                for (RtpParameters.Encoding e : p.encodings) {
                    e.maxBitrateBps = 15_000_000;
                    e.minBitrateBps = 6_000_000;
                    e.maxFramerate  = fps;
                    e.scaleResolutionDownBy = 1.0;
                }
                vSender.setParameters(p);
            }
        }

        if (sendSound) {
            MediaConstraints audioCons = new MediaConstraints();
            audioCons.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "false"));
            audioCons.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "false"));
            audioCons.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "false"));
            audioCons.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "false"));

            audioSource = factory.createAudioSource(audioCons);
            audioTrack  = factory.createAudioTrack("sys-audio", audioSource);
            audioTrack.setEnabled(true);
            peerConnection.addTrack(audioTrack, Collections.singletonList("ARDAMS"));

            for (RtpSender s : peerConnection.getSenders()) {
                MediaStreamTrack t = s.track();
                if (t != null && "audio".equals(t.kind())) {
                    RtpParameters p = s.getParameters();
                    if (p.encodings != null && !p.encodings.isEmpty()) {
                        for (RtpParameters.Encoding e : p.encodings) {
                            e.maxBitrateBps = 192_000;
                        }
                        s.setParameters(p);
                    }
                }
            }
        }

        for (RtpTransceiver t : peerConnection.getTransceivers()) {
            MediaStreamTrack.MediaType mt = t.getMediaType();
            if (mt == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO ||
                    mt == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                t.setDirection(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY);
            }
        }

        // 4) SDP offer (prefer H264 + Opus stereo)
        MediaConstraints offerCons = new MediaConstraints();
        offerCons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        offerCons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

        peerConnection.createOffer(new org.webrtc.SdpObserver() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                String munged = preferH264First(sdp.description);
                munged = preferOpusStereo(munged);
                SessionDescription withMods = new SessionDescription(sdp.type, munged);
                peerConnection.setLocalDescription(new org.webrtc.SdpObserver() {
                    @Override public void onSetSuccess() {}
                    @Override public void onSetFailure(String s) {}
                    @Override public void onCreateSuccess(SessionDescription s) {}
                    @Override public void onCreateFailure(String s) {}
                }, withMods);
                localOfferSdp = munged;
            }
            @Override public void onSetSuccess() {}
            @Override public void onCreateFailure(String s) {}
            @Override public void onSetFailure(String s) {}
        }, offerCons);

        connected = true;
    }

    @Override
    public void stop() {
        connected = false;
        try { if (capturer != null) { capturer.stopCapture(); capturer.dispose(); capturer = null; } } catch (Throwable ignore) {}
        try { if (videoTrack != null) { videoTrack.dispose(); videoTrack = null; } } catch (Throwable ignore) {}
        try { if (videoSource != null) { videoSource.dispose(); videoSource = null; } } catch (Throwable ignore) {}
        try { if (audioTrack != null) { audioTrack.dispose(); audioTrack = null; } } catch (Throwable ignore) {}
        try { if (audioSource != null) { audioSource.dispose(); audioSource = null; } } catch (Throwable ignore) {}
        try { if (peerConnection != null) { peerConnection.close(); peerConnection = null; } } catch (Throwable ignore) {}
        try { if (surfaceHelper != null) { surfaceHelper.dispose(); surfaceHelper = null; } } catch (Throwable ignore) {}
        try { if (factory != null) { factory.dispose(); factory = null; } } catch (Throwable ignore) {}
        try { if (audioDeviceModule != null) { audioDeviceModule.release(); audioDeviceModule = null; } } catch (Throwable ignore) {}
        try { if (eglBase != null) { eglBase.release(); eglBase = null; } } catch (Throwable ignore) {}
    }

    @Override public boolean isConnected() { return connected; }

    public SenderController attachMediaProjection(android.media.projection.MediaProjection mp, boolean sendSound) {
        this.mediaProjection = mp;
        this.sendSound = sendSound;
        return this;
    }

    // ---- getters for Activity via Service ----
    @Nullable public String getLocalOfferSdp() { return localOfferSdp; }

    @NonNull
    public List<IceCandidate> getLocalIceCandidatesSnapshot() {
        synchronized (localIce) { return new ArrayList<>(localIce); }
    }

    public void applyRemoteAnswer(@NonNull String sdp) {
        if (peerConnection == null) return;
        SessionDescription ans = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
        peerConnection.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override public void onSetSuccess() {}
            @Override public void onSetFailure(String s) {}
            @Override public void onCreateSuccess(SessionDescription s) {}
            @Override public void onCreateFailure(String s) {}
        }, ans);
    }

    public void addRemoteIce(@NonNull IceCandidate c) {
        if (peerConnection == null) return;
        peerConnection.addIceCandidate(c);
    }

    private static int[] fitWithin(int w, int h, int maxW, int maxH) {
        double s = Math.min(maxW/(double)w, maxH/(double)h);
        if (s >= 1) return new int[]{w, h};
        return new int[]{Math.max(320, (int)Math.round(w*s)), Math.max(240,(int)Math.round(h*s))};
    }

    private static String preferH264First(String sdp) {
        java.util.regex.Pattern pH264 = java.util.regex.Pattern.compile("(?m)^a=rtpmap:(\\d+) H264/90000");
        java.util.regex.Pattern pM    = java.util.regex.Pattern.compile("(?m)^m=video \\d+ [A-Z/]+ (.+)$");
        java.util.regex.Matcher mH    = pH264.matcher(sdp);
        java.util.regex.Matcher mM    = pM.matcher(sdp);

        java.util.List<String> h264 = new java.util.ArrayList<>();
        while (mH.find()) h264.add(mH.group(1));
        if (h264.isEmpty() || !mM.find()) return sdp;

        String[] all = Objects.requireNonNull(mM.group(1)).trim().split("\\s+");
        java.util.Set<String> hset = new java.util.HashSet<>(h264);
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (String pt : all) if (!hset.contains(pt)) rest.add(pt);

        String reordered = String.join(" ", h264) + (rest.isEmpty() ? "" : " " + String.join(" ", rest));
        return sdp.substring(0, mM.start(1)) + reordered + sdp.substring(mM.end(1));
    }

    private static String preferOpusStereo(String sdp) {
        java.util.regex.Matcher mAudio = java.util.regex.Pattern
                .compile("(?m)^m=audio \\d+ [A-Z/]+ (.+)$").matcher(sdp);
        if (!mAudio.find()) return sdp;

        java.util.regex.Matcher mOpus = java.util.regex.Pattern
                .compile("(?m)^a=rtpmap:(\\d+) opus/48000").matcher(sdp);
        if (!mOpus.find()) return sdp;
        String opusPt = mOpus.group(1);

        java.util.regex.Pattern pFmtp = java.util.regex.Pattern
                .compile("(?m)^a=fmtp:" + opusPt + " .*");
        String fmtp = "a=fmtp:" + opusPt +
                " stereo=1;sprop-stereo=1;maxaveragebitrate=192000;minptime=10;ptime=20";

        String out = sdp;
        if (pFmtp.matcher(out).find()) {
            out = pFmtp.matcher(out).replaceAll(fmtp);
        } else {
            int insertAfter = mOpus.end();
            out = out.substring(0, insertAfter) + "\r\n" + fmtp + out.substring(insertAfter);
        }
        return out;
    }
}
