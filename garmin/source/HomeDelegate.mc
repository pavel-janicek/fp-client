import Toybox.Lang;
import Toybox.WatchUi;

//! Input for the home screen. 10b prototype: START/select queues a test workout and sends it to
//! the phone; Back falls through to the system and exits.
class HomeDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        PendingWorkouts.addTestWorkout();
        PhoneLink.sendPending(null);
        WatchUi.requestUpdate();
        return true;
    }
}
