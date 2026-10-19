import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! Home screen: app name and the phone-link status, centred so it reads the same on round and
//! square displays of any size (no layout XML — positions derive from the dc).
class HomeView extends WatchUi.View {

    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var cx = dc.getWidth() / 2;
        var cy = dc.getHeight() / 2;

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(0x4CAF50, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy, Graphics.FONT_MEDIUM, WatchUi.loadResource(Rez.Strings.AppName) as String,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy + dc.getFontHeight(Graphics.FONT_MEDIUM), Graphics.FONT_XTINY,
            WatchUi.loadResource(Rez.Strings.StatusNotLinked) as String, Graphics.TEXT_JUSTIFY_CENTER);
    }
}
