import Toybox.Application;
import Toybox.Background;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;

//! Workouts waiting for the phone's ack, persisted in Application.Storage so they survive the
//! app closing, a background run ending and a reboot. Nothing is dropped until the phone has
//! acknowledged it (see Protocol). Written from the foreground and the background service alike
//! (allowed in the background since Connect IQ 3.2; this app requires 5.0).
(:background)
module PendingWorkouts {
    const STORAGE_KEY = "pending";
    //! Retry interval while anything is queued; 5 minutes is the platform minimum.
    const RETRY_SECONDS = 5 * 60;
    //! Oldest entries are dropped beyond this, so a phone that never answers cannot exhaust storage.
    const MAX_ENTRIES = 20;

    function all() as Array<Protocol.Workout> {
        var stored = Storage.getValue(STORAGE_KEY);
        return (stored instanceof Array) ? stored as Array<Protocol.Workout> : [] as Array<Protocol.Workout>;
    }

    function count() as Number {
        return all().size();
    }

    //! Queues a test workout (10b prototype) and returns it.
    function addTestWorkout() as Protocol.Workout {
        var now = Time.now().value();
        var workout = {
            Protocol.KEY_ID => now.toString() + "-" + (Math.rand() % 100000).toString(),
            Protocol.KEY_CREATED => now,
            Protocol.KEY_LABEL => "Test workout"
        } as Protocol.Workout;
        var entries = all();
        entries.add(workout);
        while (entries.size() > MAX_ENTRIES) {
            entries = entries.slice(1, null);
        }
        save(entries);
        return workout;
    }

    function remove(id as Object?) as Void {
        var kept = [] as Array<Protocol.Workout>;
        var entries = all();
        for (var i = 0; i < entries.size(); i++) {
            var entryId = entries[i][Protocol.KEY_ID] as String;
            if (!entryId.equals(id)) {
                kept.add(entries[i]);
            }
        }
        save(kept);
    }

    //! Persists [entries] and keeps the retry timer in step: armed while anything is queued.
    function save(entries as Array<Protocol.Workout>) as Void {
        Storage.setValue(STORAGE_KEY, entries as Array<Storage.ValueType>);
        if (entries.size() > 0) {
            if (Background.getTemporalEventRegisteredTime() == null) {
                Background.registerForTemporalEvent(new Time.Duration(RETRY_SECONDS));
            }
        } else {
            Background.deleteTemporalEvent();
        }
    }
}
