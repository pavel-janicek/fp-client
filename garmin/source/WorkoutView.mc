import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Position;
import Toybox.WatchUi;

//! The workout screen: activity and state on top, moving time large in the middle, distance (GPS
//! state before START) and heart rate below, and what START does at the bottom. Positions derive
//! from the dc, so one layout serves round and square displays from 208 to 454 px.
class WorkoutView extends WatchUi.View {
    private var _controller as WorkoutController;

    function initialize(controller as WorkoutController) {
        View.initialize();
        _controller = controller;
    }

    function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var state = _controller.state;

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        var title = _controller.label().toUpper();
        // READY must not look like a running workout: the list's START only opens this screen,
        // the timer starts with the next START.
        var titleColor = 0x4CAF50;
        if (state == STATE_READY) {
            titleColor = Graphics.COLOR_LT_GRAY;
        } else if (state == STATE_PAUSED) {
            title = title + " · " + WatchUi.loadResource(Rez.Strings.Paused);
            titleColor = Graphics.COLOR_YELLOW;
        }
        dc.setColor(titleColor, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 18 / 100, Graphics.FONT_SMALL, title, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(state == STATE_READY ? Graphics.COLOR_DK_GRAY : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 40 / 100, Graphics.FONT_NUMBER_MEDIUM, formatTime(_controller.movingSeconds()),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var hr = _controller.heartRate;
        if (state == STATE_READY) {
            var gpsReady = _controller.gpsQuality >= Position.QUALITY_GOOD;
            dc.setColor(gpsReady ? 0x4CAF50 : Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, h * 60 / 100, Graphics.FONT_SMALL,
                WatchUi.loadResource(gpsReady ? Rez.Strings.GpsReady : Rez.Strings.GpsSearching) as String,
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        } else {
            var km = _controller.distanceMeters() / 1000.0;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, h * 60 / 100, Graphics.FONT_MEDIUM, km.format("%.2f") + " km",
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 72 / 100, Graphics.FONT_SMALL, (hr != null ? hr.toString() : "--") + " bpm",
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(state == STATE_READY ? 0x4CAF50 : Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 85 / 100, state == STATE_READY ? Graphics.FONT_TINY : Graphics.FONT_XTINY, hint(state),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    private function hint(state as WorkoutState) as String {
        if (state == STATE_READY) {
            return WatchUi.loadResource(Rez.Strings.HintStart) as String;
        }
        if (state == STATE_PAUSED) {
            return WatchUi.loadResource(Rez.Strings.HintResume) as String;
        }
        return WatchUi.loadResource(Rez.Strings.HintPause) as String;
    }

    static function formatTime(seconds as Number) as String {
        var h = seconds / 3600;
        var m = (seconds % 3600) / 60;
        var s = seconds % 60;
        return h > 0
            ? h.toString() + ":" + m.format("%02d") + ":" + s.format("%02d")
            : m.format("%02d") + ":" + s.format("%02d");
    }
}
