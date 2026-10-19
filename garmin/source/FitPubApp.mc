import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

//! Entry point (manifest `entry`). 10a scaffolding: one static home screen; the phone link (10b)
//! and recording (10c) hang off this class later.
class FitPubApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
    }

    function onStop(state as Dictionary?) as Void {
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        return [new HomeView(), new HomeDelegate()];
    }
}
