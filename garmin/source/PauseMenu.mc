import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

//! Resume / Save / Discard for a paused workout. Save and Discard close the workout screen too,
//! returning to the activity list.
module PauseMenu {
    function show(controller as WorkoutController) as Void {
        var menu = new WatchUi.Menu2({ :title => WatchUi.loadResource(Rez.Strings.Paused) as String });
        menu.addItem(new WatchUi.MenuItem(WatchUi.loadResource(Rez.Strings.Resume) as String, null, :resume, null));
        menu.addItem(new WatchUi.MenuItem(WatchUi.loadResource(Rez.Strings.Save) as String, null, :save, null));
        menu.addItem(new WatchUi.MenuItem(WatchUi.loadResource(Rez.Strings.Discard) as String, null, :discard, null));
        WatchUi.pushView(menu, new PauseMenuDelegate(controller), WatchUi.SLIDE_UP);
    }
}

class PauseMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _controller as WorkoutController;

    function initialize(controller as WorkoutController) {
        Menu2InputDelegate.initialize();
        _controller = controller;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        System.println("FP: pause menu " + id);
        if (id == :resume) {
            _controller.resume();
            WatchUi.popView(WatchUi.SLIDE_DOWN);
            return;
        }
        if (id == :save) {
            _controller.save();
        } else {
            _controller.discard();
        }
        // Close the menu and the workout screen.
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        WatchUi.popView(WatchUi.SLIDE_DOWN);
    }
}
