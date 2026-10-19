import Toybox.Application;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

//! Start screen: the activity types, then a sync row that shows whether workouts still wait for
//! the phone and resends them when selected (like "Sync now" in the Wear OS app).
class HomeMenu extends WatchUi.Menu2 {
    private static const SYNC_ID = :sync;

    function initialize() {
        Menu2.initialize({ :title => WatchUi.loadResource(Rez.Strings.AppName) as String });
        for (var i = 0; i < Activities.LABELS.size(); i++) {
            addItem(new WatchUi.MenuItem(Activities.LABELS[i], null, i, null));
        }
        addItem(new WatchUi.MenuItem(WatchUi.loadResource(Rez.Strings.SyncNow) as String, syncStatus(), SYNC_ID, null));
    }

    function onShow() as Void {
        refresh();
        Menu2.onShow();
    }

    //! Updates the sync row; also called when a phone message changed the queue.
    function refresh() as Void {
        var index = findItemById(SYNC_ID);
        var item = (index >= 0) ? getItem(index) : null;
        if (item != null) {
            item.setSubLabel(syncStatus());
        }
        WatchUi.requestUpdate();
    }

    //! "All sent", or how many wait plus when the phone last answered — "no reply yet" usually
    //! means FP Client (or Garmin Connect) is missing on the phone.
    private function syncStatus() as String {
        var pending = PendingWorkouts.count();
        if (pending == 0) {
            return WatchUi.loadResource(Rez.Strings.StatusAllSent) as String;
        }
        var last = Storage.getValue(PhoneLink.LAST_PHONE_KEY);
        if (!(last instanceof Number)) {
            return Lang.format(WatchUi.loadResource(Rez.Strings.StatusPendingNever) as String, [pending]);
        }
        return Lang.format(WatchUi.loadResource(Rez.Strings.StatusPendingSeen) as String,
            [pending, formatAge(Time.now().value() - last)]);
    }

    static function formatAge(seconds as Number) as String {
        if (seconds < 3600) {
            return (seconds / 60).toString() + " min";
        }
        if (seconds < 48 * 3600) {
            return (seconds / 3600).toString() + " h";
        }
        return (seconds / 86400).toString() + " d";
    }
}

class HomeMenuDelegate extends WatchUi.Menu2InputDelegate {

    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        if (id instanceof Number) {
            System.println("FP: open " + Activities.KEYS[id]);
            var controller = new WorkoutController(id);
            WatchUi.pushView(new WorkoutView(controller), new WorkoutDelegate(controller), WatchUi.SLIDE_UP);
        } else {
            PhoneLink.sendPending(null);
        }
    }
}
