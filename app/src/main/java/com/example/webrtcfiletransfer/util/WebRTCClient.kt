package com.example.webrtcfiletransfer.util

import android.app.Application
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

interface WebRTCListener {
    fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    fun onSignalNeeded(sdp: SessionDescription)
    fun onIceCandidateNeeded(candidate: IceCandidate)
    fun onDataChannelOpened()
    fun onDataChannelMessage(buffer: ByteBuffer)
}

class WebRTCClient(
    private val application: Application,
    private val listener: WebRTCListener
) {
    private val TAG = "WebRTCClient"

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.ekiga.net").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.ideasip.com").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.rixtelecom.se").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.schlund.de").createIceServer(),
        PeerConnection.IceServer.builder("turn:turn.bistri.com:80").setUsername("homeo").setPassword("homeo").createIceServer(),
        PeerConnection.IceServer.builder("turn:turn.anyfirewall.com:443?transport=tcp").setUsername("webrtc").setPassword("webrtc").createIceServer(),
        PeerConnection.IceServer.builder("turn:numb.viagenie.ca").setUsername("webrtc@live.com").setPassword("muazkh").createIceServer()
    )

    private val peerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(application)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var sdpCallback: ((SessionDescription) -> Unit)? = null

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                listener.onIceCandidateNeeded(it)
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            newState?.let {
                Log.d(TAG, "onIceConnectionChange: $it")
                listener.onConnectionStateChange(it)
            }
        }

        override fun onDataChannel(dc: DataChannel?) {
            Log.d(TAG, "onDataChannel: A remote data channel is available.")
            dataChannel = dc
            setupDataChannelObserver()
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }

    init {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServer)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)
        val dcInit = DataChannel.Init().apply { ordered = true }
        dataChannel = peerConnection?.createDataChannel("fileChannel", dcInit)
        setupDataChannelObserver()
    }

    private fun setupDataChannelObserver() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel state is: ${dataChannel?.state()}")
                if (dataChannel?.state() == DataChannel.State.OPEN) {
                    listener.onDataChannelOpened()
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let { listener.onDataChannelMessage(it.data) }
            }
        })
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            sdp?.let {
                peerConnection?.setLocalDescription(this, it)
                // The callback is used for the OFFER flow to bundle it with metadata
                // The listener is used for the ANSWER flow
                sdpCallback?.invoke(it)
                sdpCallback = null // One-time use
                listener.onSignalNeeded(it)
            }
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        sdpCallback = callback
        val mediaConstraints = MediaConstraints()
        peerConnection?.createOffer(sdpObserver, mediaConstraints)
    }

    fun createAnswer() {
        val mediaConstraints = MediaConstraints()
        peerConnection?.createAnswer(sdpObserver, mediaConstraints)
    }

    fun onRemoteSessionReceived(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun sendData(data: ByteBuffer) {
        val buffer = DataChannel.Buffer(data, false) // false for binary
        dataChannel?.send(buffer)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
    }
}
