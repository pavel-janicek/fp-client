import Toybox.Lang;

//! Watch ↔ phone message contract (Connect IQ Mobile SDK). Mirrored on the phone in
//! `com.fpclient.android.garmin.GarminProtocol`; the wire strings are pinned by unit tests on
//! both sides (`ProtocolTest.mc` here, `GarminProtocolTest` in `:app`), so keep them in sync.
//!
//! Every message is a Dictionary with a type and a protocol version:
//! - watch → phone `workout` {id, created, label, sport, duration, distance, chunks}: a finished
//!   workout's summary, resent until the phone acks it;
//! - watch → phone `chunk` {id, index, data}: one stored sample chunk (SampleBuffer). Pushed in
//!   order right after Save, and on demand for every `need`;
//! - phone → watch `need` {id, index}: send that chunk (the phone missed it);
//! - phone → watch `ack` {id}: the phone holds the summary and every chunk — drop it all;
//! - phone → watch `pull`: resend everything still queued (the phone sends it when it starts,
//!   because a message that woke a cold phone process can be lost before its SDK is ready).
(:background)
module Protocol {
    const VERSION = 1;

    const KEY_TYPE = "t";
    const KEY_VERSION = "v";
    const KEY_ID = "id";
    //! Start of the workout, epoch seconds.
    const KEY_CREATED = "created";
    const KEY_LABEL = "label";
    //! Activity key shared with the Wear OS app: RUN, WALK, HIKE, BIKE, OTHER.
    const KEY_SPORT = "sport";
    //! Moving time in seconds (pauses excluded).
    const KEY_DURATION = "duration";
    //! Distance in whole metres.
    const KEY_DISTANCE = "distance";
    //! Number of sample chunks the workout has (0 when none could be stored).
    const KEY_CHUNKS = "chunks";
    const KEY_INDEX = "index";
    const KEY_DATA = "data";

    const TYPE_WORKOUT = "workout";
    const TYPE_ACK = "ack";
    const TYPE_PULL = "pull";
    const TYPE_CHUNK = "chunk";
    const TYPE_NEED = "need";

    //! A queued workout as stored and sent.
    typedef Workout as Dictionary<String, String or Number>;

    function workoutMessage(workout as Workout) as Workout {
        return {
            KEY_TYPE => TYPE_WORKOUT,
            KEY_VERSION => VERSION,
            KEY_ID => workout[KEY_ID],
            KEY_CREATED => workout[KEY_CREATED],
            KEY_LABEL => workout[KEY_LABEL],
            KEY_SPORT => workout[KEY_SPORT],
            KEY_DURATION => workout[KEY_DURATION],
            KEY_DISTANCE => workout[KEY_DISTANCE],
            KEY_CHUNKS => workout[KEY_CHUNKS]
        } as Workout;
    }

    function chunkMessage(id as String, index as Number, data as Array) as Dictionary<String, String or Number or Array> {
        return {
            KEY_TYPE => TYPE_CHUNK,
            KEY_VERSION => VERSION,
            KEY_ID => id,
            KEY_INDEX => index,
            KEY_DATA => data
        } as Dictionary<String, String or Number or Array>;
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
