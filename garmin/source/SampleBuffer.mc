import Toybox.Application;
import Toybox.Lang;
import Toybox.System;

//! The per-second samples the phone will need to build its FIT (sent with 10d), written to
//! Storage in chunks so neither the app nor the 64 KB background service ever holds a whole
//! workout. Each chunk is one flat Array of SAMPLE_FIELDS numbers per sample — integers only,
//! so 32-bit Floats never cost GPS precision:
//!   [secondsSinceStart, latE6, lonE6, altitudeDm, heartRate, steps]
//! with null for a value the watch did not have (no GPS fix, no heart rate).
//!
//! Storage is finite and device-dependent (10 MB on every current target; one hour ≈ 100 KB): if
//! a write fails, sampling stops but the native Garmin activity keeps recording, and the summary
//! still reaches the phone.
class SampleBuffer {
    static const SAMPLE_FIELDS = 6;
    //! ~4 KB per chunk serialized, far under Storage's 32 KB per-value limit.
    static const SAMPLES_PER_CHUNK = 120;

    private var _id as String;
    private var _current as Array<Number or Null> = [] as Array<Number or Null>;
    private var _chunks as Number = 0;
    private var _full as Boolean = false;

    function initialize(id as String) {
        _id = id;
    }

    function add(seconds as Number, latE6 as Number?, lonE6 as Number?, altitudeDm as Number?,
            heartRate as Number?, steps as Number?) as Void {
        if (_full) {
            return;
        }
        _current.addAll([seconds, latE6, lonE6, altitudeDm, heartRate, steps]);
        if (_current.size() >= SAMPLES_PER_CHUNK * SAMPLE_FIELDS) {
            flush();
        }
    }

    //! Writes the last partial chunk; returns how many chunks are stored.
    function finish() as Number {
        flush();
        return _chunks;
    }

    function discard() as Void {
        for (var i = 0; i < _chunks; i++) {
            Storage.deleteValue(PendingWorkouts.chunkKey(_id, i));
        }
        _chunks = 0;
        _current = [] as Array<Number or Null>;
    }

    private function flush() as Void {
        if (_full || _current.size() == 0) {
            return;
        }
        try {
            Storage.setValue(PendingWorkouts.chunkKey(_id, _chunks), _current as Array<Storage.ValueType>);
            _chunks++;
        } catch (e) {
            System.println("FP: sample storage full, sampling stopped");
            _full = true;
        }
        _current = [] as Array<Number or Null>;
    }
}
