import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

//! Entry point (manifest `entry`). Also the background process's app object, hence (:background);
//! the foreground-only parts (views, the live message callback) stay outside that annotation.
(:background)
class FitPubApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
    }

    function onStop(state as Dictionary?) as Void {
    }

    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new BackgroundService()];
    }

    (:typecheck(disableBackgroundCheck))
    function getInitialView() as [Views] or [Views, InputDelegates] {
        // Wake the background service for phone messages while the app is closed; the
        // registration persists, so doing it on every launch is enough.
        Background.registerForPhoneAppMessageEvent();
        // While the app is open, messages arrive here instead.
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        return [new HomeView(), new HomeDelegate()];
    }

    (:typecheck(disableBackgroundCheck))
    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        PhoneLink.handle(msg.data, null);
        WatchUi.requestUpdate();
    }

    //! A background run finished while the app is open: the queue may have changed.
    (:typecheck(disableBackgroundCheck))
    function onBackgroundData(data as Application.PersistableType) as Void {
        WatchUi.requestUpdate();
    }
}
