import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

//! Workout input, following Garmin's own activity apps: START starts, pauses (and offers the
//! save menu) or resumes; BACK while a workout runs pauses and opens the menu instead of leaving,
//! so a stray press never loses a workout. On touch watches a tap acts as START.
class WorkoutDelegate extends WatchUi.BehaviorDelegate {
    private var _controller as WorkoutController;

    function initialize(controller as WorkoutController) {
        BehaviorDelegate.initialize();
        _controller = controller;
    }

    function onSelect() as Boolean {
        var state = _controller.state;
        System.println("FP: START in state " + state);
        if (state == STATE_READY) {
            _controller.start();
        } else if (state == STATE_RECORDING) {
            _controller.pause();
            PauseMenu.show(_controller);
        } else {
            _controller.resume();
        }
        WatchUi.requestUpdate();
        return true;
    }

    function onBack() as Boolean {
        if (_controller.state == STATE_READY) {
            _controller.close();
            return false;
        }
        _controller.pause();
        PauseMenu.show(_controller);
        return true;
    }
}
