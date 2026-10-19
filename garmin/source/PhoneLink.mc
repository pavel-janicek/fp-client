import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;

//! Sending to and handling messages from FP Client on the phone. Shared by the foreground app
//! and the background service. A completed transmit only means the Garmin Connect app took the
//! message — not that FP Client stored it — so the queue is cleared by the phone's `ack` alone.
(:background)
module PhoneLink {

    //! Transmits every queued workout. [onDone] (background only) runs once all transmits have
    //! reported back, so the service can exit; it runs immediately when nothing is queued.
    function sendPending(onDone as Method?) as Void {
        var entries = PendingWorkouts.all();
        if (entries.size() == 0) {
            if (onDone != null) {
                onDone.invoke();
            }
            return;
        }
        var tracker = new TransmitTracker(entries.size(), onDone);
        for (var i = 0; i < entries.size(); i++) {
            var message = Protocol.workoutMessage(entries[i]) as Dictionary<Communications.TransmitKeyType, Communications.TransmitType>;
            Communications.transmit(message, null, new TransmitListener(tracker));
        }
    }

    //! Applies one phone message. [onDone] as in sendPending.
    function handle(data as Object?, onDone as Method?) as Void {
        var type = Protocol.typeOf(data);
        System.println("FP: phone message " + type);
        if (Protocol.TYPE_ACK.equals(type)) {
            PendingWorkouts.remove((data as Dictionary<String, Object?>)[Protocol.KEY_ID]);
            if (onDone != null) {
                onDone.invoke();
            }
        } else if (Protocol.TYPE_PULL.equals(type)) {
            sendPending(onDone);
        } else {
            if (onDone != null) {
                onDone.invoke();
            }
        }
    }
}

//! Counts outstanding transmits and fires the completion callback once.
(:background)
class TransmitTracker {
    private var _remaining as Number;
    private var _onDone as Method?;

    function initialize(count as Number, onDone as Method?) {
        _remaining = count;
        _onDone = onDone;
    }

    function finished() as Void {
        _remaining--;
        if (_remaining <= 0 && _onDone != null) {
            var callback = _onDone;
            _onDone = null;
            callback.invoke();
        }
    }
}

(:background)
class TransmitListener extends Communications.ConnectionListener {
    private var _tracker as TransmitTracker;

    function initialize(tracker as TransmitTracker) {
        ConnectionListener.initialize();
        _tracker = tracker;
    }

    function onComplete() as Void {
        System.println("FP: transmit complete");
        _tracker.finished();
    }

    function onError() as Void {
        // Stays queued; the 5-minute retry or the phone's next `pull` sends it again.
        System.println("FP: transmit failed");
        _tracker.finished();
    }
}
