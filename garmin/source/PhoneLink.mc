import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;

//! Sending to and handling messages from FP Client on the phone. Shared by the foreground app
//! and the background service. A completed transmit only means the Garmin Connect app took the
//! message — not that FP Client stored it — so the queue is cleared by the phone's `ack` alone.
(:background)
module PhoneLink {
    //! When FP Client last sent anything (epoch seconds), so the watch can tell "phone silent".
    const LAST_PHONE_KEY = "lastPhone";

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

    //! Transmits one stored sample chunk; [onDone] as in sendPending. A chunk that no longer exists
    //! (its workout was acked or dropped) is skipped.
    function sendChunk(id as String, index as Number, onDone as Method?) as Void {
        var data = Storage.getValue(PendingWorkouts.chunkKey(id, index));
        if (!(data instanceof Array)) {
            if (onDone != null) {
                onDone.invoke();
            }
            return;
        }
        var message = Protocol.chunkMessage(id, index, data as Array) as Dictionary<Communications.TransmitKeyType, Communications.TransmitType>;
        Communications.transmit(message, null, new TransmitListener(new TransmitTracker(1, onDone)));
    }

    //! Applies one phone message. [onDone] as in sendPending.
    function handle(data as Object?, onDone as Method?) as Void {
        var type = Protocol.typeOf(data);
        System.println("FP: phone message " + type);
        if (type != null) {
            Storage.setValue(LAST_PHONE_KEY, Time.now().value());
        }
        if (Protocol.TYPE_ACK.equals(type)) {
            PendingWorkouts.remove((data as Dictionary<String, Object?>)[Protocol.KEY_ID]);
            if (onDone != null) {
                onDone.invoke();
            }
        } else if (Protocol.TYPE_PULL.equals(type)) {
            sendPending(onDone);
        } else if (Protocol.TYPE_NEED.equals(type)) {
            var request = data as Dictionary<String, Object?>;
            var id = request[Protocol.KEY_ID];
            var index = request[Protocol.KEY_INDEX];
            if (id instanceof String && index instanceof Number) {
                sendChunk(id, index, onDone);
            } else if (onDone != null) {
                onDone.invoke();
            }
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

//! Pushes one workout's chunks after Save, strictly one transmit at a time (a burst can overflow
//! the transmit queue). A failed chunk is not retried here — the phone requests it with `need`.
class ChunkPusher {
    private var _id as String;
    private var _count as Number;
    private var _next as Number = 0;

    function initialize(id as String, count as Number) {
        _id = id;
        _count = count;
    }

    function start() as Void {
        sendNext();
    }

    function sendNext() as Void {
        if (_next >= _count) {
            return;
        }
        var index = _next;
        _next++;
        PhoneLink.sendChunk(_id, index, method(:sendNext));
    }
}
