package com.example.webrtcfiletransfer.util

import android.app.Application
import android.util.Log
import com.example.webrtcfiletransfer.data.model.SignalData
import org.webrtc.*
import java.nio.ByteBuffer

// Listener interface to communicate WebRTC events back to the calling activity.
interface WebRTCListener {
    fun onConnectionStateChange(state: PeerConnection.IceConnectionState)
    fun onSignalNeeded(signal: SignalData)
    fun onDataChannelOpened()
    fun onDataChannelMessage(buffer: ByteBuffer)
}

class WebRTCClient(
    private val application: Application,
    private val listener: WebRTCListener
) {
    private val TAG = "WebRTCClient"

    // List of STUN/TURN servers for NAT traversal.
    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    // Factory for creating PeerConnection instances.
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

    // Observer for PeerConnection events.
    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                // Create a SignalData object with the candidate's properties.
                val signal = SignalData(
                    type = "ICE_CANDIDATE",
                    sdp = it.sdp,
                    sdpMid = it.sdpMid,
                    sdpMLineIndex = it.sdpMLineIndex
                )
                listener.onSignalNeeded(signal)
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
        val dcInit = DataChannel.Init()
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
                val signalType = if (it.type == SessionDescription.Type.OFFER) "OFFER" else "ANSWER"
                // Create a SignalData object with the SDP.
                val signal = SignalData(type = signalType, sdp = it.description)
                listener.onSignalNeeded(signal)
            }
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    fun createOffer() {
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

    fun sendFile(data: ByteBuffer) {
        val buffer = DataChannel.Buffer(data, true) // true for binary data
        dataChannel?.send(buffer)
    }

    fun close() {
        dataChannel?.close()
        peerConnection?.close()
    }
}
