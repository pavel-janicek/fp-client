import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Position;
import Toybox.WatchUi;

//! The workout screen: activity and state on top, moving time large in the middle, distance and
//! heart rate below, and what START does at the bottom. Positions derive from the dc, so one
//! layout serves round and square displays from 240 to 454 px.
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
        var titleColor = 0x4CAF50;
        if (state == STATE_PAUSED) {
            title = title + " · " + WatchUi.loadResource(Rez.Strings.Paused);
            titleColor = Graphics.COLOR_YELLOW;
        }
        dc.setColor(titleColor, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 18 / 100, Graphics.FONT_SMALL, title, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 40 / 100, Graphics.FONT_NUMBER_MEDIUM, formatTime(_controller.movingSeconds()),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var km = _controller.distanceMeters() / 1000.0;
        var hr = _controller.heartRate;
        dc.drawText(cx, h * 60 / 100, Graphics.FONT_MEDIUM, km.format("%.2f") + " km",
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 72 / 100, Graphics.FONT_SMALL, (hr != null ? hr.toString() : "--") + " bpm",
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, h * 85 / 100, Graphics.FONT_XTINY, hint(state), Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    private function hint(state as WorkoutState) as String {
        if (state == STATE_READY) {
            var gps = _controller.gpsQuality >= Position.QUALITY_GOOD
                ? WatchUi.loadResource(Rez.Strings.GpsReady) as String
                : WatchUi.loadResource(Rez.Strings.GpsSearching) as String;
            return gps + " · " + (WatchUi.loadResource(Rez.Strings.HintStart) as String);
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
