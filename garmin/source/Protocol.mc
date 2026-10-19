import Toybox.Lang;

//! Watch ↔ phone message contract (Connect IQ Mobile SDK). Mirrored on the phone in
//! `com.fpclient.android.garmin.GarminProtocol`; the wire strings are pinned by unit tests on
//! both sides (`ProtocolTest.mc` here, `GarminProtocolTest` in `:app`), so keep them in sync.
//!
//! Every message is a Dictionary with a type and a protocol version:
//! - watch → phone `workout` {id, created, label}: resent until the phone acks it;
//! - phone → watch `ack` {id}: the phone has stored the workout, drop it from the queue;
//! - phone → watch `pull`: resend everything still queued (the phone sends it when it starts,
//!   because a message that woke a cold phone process can be lost before its SDK is ready).
(:background)
module Protocol {
    const VERSION = 1;

    const KEY_TYPE = "t";
    const KEY_VERSION = "v";
    const KEY_ID = "id";
    const KEY_CREATED = "created";
    const KEY_LABEL = "label";

    const TYPE_WORKOUT = "workout";
    const TYPE_ACK = "ack";
    const TYPE_PULL = "pull";

    //! A queued workout as stored and sent: id and label are Strings, created is epoch seconds.
    typedef Workout as Dictionary<String, String or Number>;

    //! 10b carries only a test workout; the recorded samples arrive with 10c/10d.
    function workoutMessage(workout as Workout) as Workout {
        return {
            KEY_TYPE => TYPE_WORKOUT,
            KEY_VERSION => VERSION,
            KEY_ID => workout[KEY_ID],
            KEY_CREATED => workout[KEY_CREATED],
            KEY_LABEL => workout[KEY_LABEL]
        } as Workout;
    }

    //! The message type, or null for anything that is not a v1 FitPub message.
    function typeOf(data as Object?) as String? {
        if (!(data instanceof Dictionary)) {
            return null;
        }
        var type = (data as Dictionary)[KEY_TYPE];
        return (type instanceof String) ? type as String : null;
    }
}
