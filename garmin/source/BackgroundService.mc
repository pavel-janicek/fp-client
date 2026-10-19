import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;

//! Runs while the app is closed: answers the phone (`pull` / `ack`) and retries queued workouts
//! every 5 minutes. The platform kills a service after ~30 s, so every path ends in exit().
(:background)
class BackgroundService extends System.ServiceDelegate {

    function initialize() {
        ServiceDelegate.initialize();
    }

    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        PhoneLink.handle(msg.data, method(:finish));
    }

    function onTemporalEvent() as Void {
        PhoneLink.sendPending(method(:finish));
    }

    function finish() as Void {
        Background.exit(null);
    }
}
