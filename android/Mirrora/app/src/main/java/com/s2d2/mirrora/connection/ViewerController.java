package com.s2d2.mirrora.connection;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;
import org.webrtc.VideoSink;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewerController implements ConnectionController {

    private final Context app;
    private final ConnectionConfig cfg;

    private EglBase eglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private JavaAudioDeviceModule audioDeviceModule;

    private final List<IceCandidate> localIce = Collections.synchronizedList(new ArrayList<>());
    private @Nullable String localAnswerSdp;

    private @Nullable VideoSink remoteSink;

    private volatile boolean connected = false;

    public ViewerController(@NonNull Context ctx, @NonNull ConnectionConfig cfg) {
        this.app = ctx.getApplicationContext();
        this.cfg = cfg;
    }

    public ViewerController attachRemoteSink(@NonNull VideoSink sink) {
        this.remoteSink = sink;
        return this;
    }

    @Nullable
    public EglBase.Context getEglBaseContext() {
        return (eglBase != null) ? eglBase.getEglBaseContext() : null;
    }

    @Override
    public void start() {
        if (connected) return;

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                        .builder(app)
                        .createInitializationOptions()
        );

        eglBase = EglBase.create();
        DefaultVideoEncoderFactory enc =
                new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory dec =
                new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        audioDeviceModule = JavaAudioDeviceModule.builder(app)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(enc)
                .setVideoDecoderFactory(dec)
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration pcCfg = new PeerConnection.RTCConfiguration(cfg.iceServers);
        pcCfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(pcCfg, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate c) {
                synchronized (localIce) { localIce.add(c); }
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState s) {}
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState s) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}

            @Override public void onTrack(RtpTransceiver t) {
                MediaStreamTrack track = t.getReceiver().track();
                if (track instanceof VideoTrack && remoteSink != null) {
                    ((VideoTrack) track).addSink(remoteSink);
                }
            }
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] ms) {
                MediaStreamTrack track = r.track();
                if (track instanceof VideoTrack && remoteSink != null) {
                    ((VideoTrack) track).addSink(remoteSink);
                }
            }

            @Override public void onAddStream(MediaStream s) {}
            @Override public void onRemoveStream(MediaStream s) {}
            @Override public void onDataChannel(org.webrtc.DataChannel dc) {}
            @Override public void onRenegotiationNeeded() {}
        });

        if (peerConnection == null) {
            throw new IllegalStateException("createPeerConnection returned null");
        }

        peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
        peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));

        connected = true;
    }

    public void applyRemoteOffer(@NonNull String offerSdp) {
        if (peerConnection == null) throw new IllegalStateException("start() not called");

        SessionDescription remote = new SessionDescription(SessionDescription.Type.OFFER, offerSdp);
        peerConnection.setRemoteDescription(new org.webrtc.SdpObserver() {
            @Override public void onSetSuccess() {
                MediaConstraints ansCons = new MediaConstraints();
                ansCons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                ansCons.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

                peerConnection.createAnswer(new org.webrtc.SdpObserver() {
                    @Override public void onCreateSuccess(SessionDescription sdp) {
                        peerConnection.setLocalDescription(new org.webrtc.SdpObserver() {
                            @Override public void onSetSuccess() {
                                localAnswerSdp = sdp.description;
                            }
                            @Override public void onSetFailure(String s) {}
                            @Override public void onCreateSuccess(SessionDescription s) {}
                            @Override public void onCreateFailure(String s) {}
                        }, sdp);
                    }
                    @Override public void onSetSuccess() {}
                    @Override public void onCreateFailure(String s) {}
                    @Override public void onSetFailure(String s) {}
                }, ansCons);
            }
            @Override public void onSetFailure(String s) {}
            @Override public void onCreateSuccess(SessionDescription s) {}
            @Override public void onCreateFailure(String s) {}
        }, remote);
    }

    public void addRemoteIce(@NonNull IceCandidate c) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(c);
        }
    }

    @Nullable
    public String getLocalAnswerSdp() {
        return localAnswerSdp;
    }

    @NonNull
    public List<IceCandidate> getLocalIceCandidatesSnapshot() {
        synchronized (localIce) {
            return new ArrayList<>(localIce);
        }
    }

    @Override
    public void stop() {
        connected = false;
        try { if (peerConnection != null) { peerConnection.close(); peerConnection = null; } } catch (Throwable ignore) {}
        try { if (factory != null) { factory.dispose(); factory = null; } } catch (Throwable ignore) {}
        try { if (audioDeviceModule != null) { audioDeviceModule.release(); audioDeviceModule = null; } } catch (Throwable ignore) {}
        try { if (eglBase != null) { eglBase.release(); eglBase = null; } } catch (Throwable ignore) {}
        localAnswerSdp = null;
        synchronized (localIce) { localIce.clear(); }
        remoteSink = null;
    }

    @Override
    public boolean isConnected() { return connected; }
}
