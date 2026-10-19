import Toybox.Activity;
import Toybox.ActivityMonitor;
import Toybox.ActivityRecording;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Position;
import Toybox.Sensor;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

enum WorkoutState {
    STATE_READY,
    STATE_RECORDING,
    STATE_PAUSED
}

//! One workout from activity pick to save/discard. Records a native Garmin activity
//! (ActivityRecording: its FIT syncs to Garmin Connect as usual) and, alongside, the per-second
//! samples for FitPub (SampleBuffer). Distance and moving time come from the native session, so
//! the watch shows the same numbers Garmin Connect will.
class WorkoutController {
    var state as WorkoutState = STATE_READY;
    var typeIndex as Number;
    //! Latest heart rate from the sensor, also before recording starts.
    var heartRate as Number?;
    //! Latest GPS accuracy (Position.QUALITY_*), so READY can say when the fix is good.
    var gpsQuality as Number = Position.QUALITY_NOT_AVAILABLE;

    private var _session as ActivityRecording.Session?;
    private var _buffer as SampleBuffer?;
    private var _timer as Timer.Timer = new Timer.Timer();
    private var _id as String = "";
    private var _startEpoch as Number = 0;
    private var _stepsBase as Number?;

    function initialize(typeIndex as Number) {
        self.typeIndex = typeIndex;
        Sensor.setEnabledSensors([Sensor.SENSOR_HEARTRATE] as Array<Sensor.SensorType>);
        Sensor.enableSensorEvents(method(:onSensor));
        Position.enableLocationEvents(Position.LOCATION_CONTINUOUS, method(:onPosition));
        _timer.start(method(:onTick), 1000, true);
    }

    function label() as String {
        return Activities.LABELS[typeIndex];
    }

    function start() as Void {
        _startEpoch = Time.now().value();
        _id = _startEpoch.toString() + "-" + (Math.rand() % 100000).toString();
        var session = ActivityRecording.createSession({
            :name => label(),
            :sport => Activities.SPORTS[typeIndex],
            :subSport => Activity.SUB_SPORT_GENERIC
        });
        session.start();
        _session = session;
        _buffer = new SampleBuffer(_id);
        _stepsBase = dailySteps();
        state = STATE_RECORDING;
    }

    function pause() as Void {
        if (_session != null && state == STATE_RECORDING) {
            _session.stop();
            state = STATE_PAUSED;
        }
    }

    function resume() as Void {
        if (_session != null && state == STATE_PAUSED) {
            _session.start();
            state = STATE_RECORDING;
        }
    }

    //! Saves the native activity, queues the summary for the phone and sends it right away.
    function save() as Void {
        var session = _session;
        if (session == null) {
            close();
            return;
        }
        if (session.isRecording()) {
            session.stop();
        }
        var duration = movingSeconds();
        var distance = distanceMeters();
        session.save();
        var chunks = (_buffer != null) ? _buffer.finish() : 0;
        PendingWorkouts.add({
            Protocol.KEY_ID => _id,
            Protocol.KEY_CREATED => _startEpoch,
            Protocol.KEY_LABEL => label(),
            Protocol.KEY_SPORT => Activities.KEYS[typeIndex],
            Protocol.KEY_DURATION => duration,
            Protocol.KEY_DISTANCE => distance,
            Protocol.KEY_CHUNKS => chunks
        } as Protocol.Workout);
        close();
        // Summaries first, then this workout's samples one chunk at a time while the app is
        // open; whatever does not get through, the phone asks for with `need`.
        PhoneLink.sendPending(new ChunkPusher(_id, chunks).method(:start));
    }

    function discard() as Void {
        if (_session != null) {
            if (_session.isRecording()) {
                _session.stop();
            }
            _session.discard();
        }
        if (_buffer != null) {
            _buffer.discard();
        }
        close();
    }

    //! Releases GPS, the heart-rate sensor and the timer; also when leaving READY without starting.
    function close() as Void {
        _timer.stop();
        Position.enableLocationEvents(Position.LOCATION_DISABLE, null);
        Sensor.enableSensorEvents(null);
        _session = null;
        _buffer = null;
    }

    function movingSeconds() as Number {
        var info = Activity.getActivityInfo();
        var time = (info != null) ? info.timerTime : null;
        return (time != null) ? time / 1000 : 0;
    }

    function distanceMeters() as Number {
        var info = Activity.getActivityInfo();
        var distance = (info != null) ? info.elapsedDistance : null;
        return (distance != null) ? distance.toNumber() : 0;
    }

    function onSensor(info as Sensor.Info) as Void {
        heartRate = info.heartRate;
    }

    function onPosition(info as Position.Info) as Void {
        gpsQuality = info.accuracy;
    }

    function onTick() as Void {
        if (state == STATE_RECORDING) {
            sample();
        }
        WatchUi.requestUpdate();
    }

    private function sample() as Void {
        var buffer = _buffer;
        if (buffer == null) {
            return;
        }
        var info = Activity.getActivityInfo();
        var latE6 = null;
        var lonE6 = null;
        var altitudeDm = null;
        var hr = heartRate;
        if (info != null) {
            var location = info.currentLocation;
            if (location != null) {
                var degrees = location.toDegrees();
                latE6 = (degrees[0].toDouble() * 1000000).toNumber();
                lonE6 = (degrees[1].toDouble() * 1000000).toNumber();
            }
            var altitude = info.altitude;
            if (altitude != null) {
                altitudeDm = (altitude * 10).toNumber();
            }
            if (info.currentHeartRate != null) {
                hr = info.currentHeartRate;
            }
        }
        var steps = null;
        var today = dailySteps();
        if (today != null && _stepsBase != null) {
            // The daily counter resets at midnight; restart the baseline rather than go negative.
            if (today < _stepsBase) {
                _stepsBase = 0;
            }
            steps = today - _stepsBase;
        }
        buffer.add(Time.now().value() - _startEpoch, latE6, lonE6, altitudeDm, hr, steps);
    }

    private function dailySteps() as Number? {
        var info = ActivityMonitor.getInfo();
        return (info != null) ? info.steps : null;
    }
}
