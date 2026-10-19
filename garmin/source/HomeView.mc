import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! Home screen: app name, how many workouts still wait for the phone, and the 10b prototype hint.
//! Centred so it reads the same on round and square displays of any size (no layout XML —
//! positions derive from the dc).
class HomeView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var cx = dc.getWidth() / 2;
        var cy = dc.getHeight() / 2;
        var line = dc.getFontHeight(Graphics.FONT_XTINY);

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(0x4CAF50, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy - line, Graphics.FONT_MEDIUM, WatchUi.loadResource(Rez.Strings.AppName) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var pending = PendingWorkouts.count();
        var status = pending == 0
            ? WatchUi.loadResource(Rez.Strings.StatusAllSent) as String
            : Lang.format(WatchUi.loadResource(Rez.Strings.StatusPending) as String, [pending]);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy + line / 2, Graphics.FONT_XTINY, status, Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy + line * 3 / 2, Graphics.FONT_XTINY,
            WatchUi.loadResource(Rez.Strings.HintSendTest) as String, Graphics.TEXT_JUSTIFY_CENTER);
    }
}
