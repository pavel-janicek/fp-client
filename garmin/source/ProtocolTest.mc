import Toybox.Lang;
import Toybox.Test;

//! Pins the watch side of the phone message contract, mirroring `GarminProtocolTest` in `:app` —
//! a changed string on either side breaks one of the two tests before it breaks a paired watch.
//! Run: see docs/GARMIN.md → "Unit tests".
(:test)
function protocolWireStringsMatchPhone(logger as Logger) as Boolean {
    Test.assertEqual(1, Protocol.VERSION);
    Test.assertEqual("t", Protocol.KEY_TYPE);
    Test.assertEqual("v", Protocol.KEY_VERSION);
    Test.assertEqual("id", Protocol.KEY_ID);
    Test.assertEqual("created", Protocol.KEY_CREATED);
    Test.assertEqual("label", Protocol.KEY_LABEL);
    Test.assertEqual("sport", Protocol.KEY_SPORT);
    Test.assertEqual("duration", Protocol.KEY_DURATION);
    Test.assertEqual("distance", Protocol.KEY_DISTANCE);
    Test.assertEqual("chunks", Protocol.KEY_CHUNKS);
    Test.assertEqual("index", Protocol.KEY_INDEX);
    Test.assertEqual("data", Protocol.KEY_DATA);
    Test.assertEqual("chunk", Protocol.TYPE_CHUNK);
    Test.assertEqual("need", Protocol.TYPE_NEED);
    Test.assertEqual("workout", Protocol.TYPE_WORKOUT);
    Test.assertEqual("ack", Protocol.TYPE_ACK);
    Test.assertEqual("pull", Protocol.TYPE_PULL);
    return true;
}

(:test)
function workoutMessageCarriesTypeVersionAndFields(logger as Logger) as Boolean {
    var message = Protocol.workoutMessage({ "id" => "42-7", "created" => 1700000000, "label" => "Run",
        "sport" => "RUN", "duration" => 1880, "distance" => 5230, "chunks" => 16 });
    Test.assertEqual("workout", message["t"]);
    Test.assertEqual(1, message["v"]);
    Test.assertEqual("42-7", message["id"]);
    Test.assertEqual(1700000000, message["created"]);
    Test.assertEqual("Run", message["label"]);
    Test.assertEqual("RUN", message["sport"]);
    Test.assertEqual(1880, message["duration"]);
    Test.assertEqual(5230, message["distance"]);
    Test.assertEqual(16, message["chunks"]);
    return true;
}

(:test)
function chunkMessageCarriesIndexAndFlatSamples(logger as Logger) as Boolean {
    var message = Protocol.chunkMessage("42-7", 3, [0, 50123456, 14123456, 2345, 140, 12]);
    Test.assertEqual("chunk", message["t"]);
    Test.assertEqual(1, message["v"]);
    Test.assertEqual("42-7", message["id"]);
    Test.assertEqual(3, message["index"]);
    Test.assertEqual(6, (message["data"] as Array).size());
    return true;
}

(:test)
function typeOfRejectsNonFitPubMessages(logger as Logger) as Boolean {
    Test.assertEqual("ack", Protocol.typeOf({ "t" => "ack", "id" => "1" }));
    Test.assert(Protocol.typeOf("ack") == null);
    Test.assert(Protocol.typeOf({ "t" => 3 }) == null);
    Test.assert(Protocol.typeOf(null) == null);
    return true;
}

(:test)
function workoutTimeFormatsWithAndWithoutHours(logger as Logger) as Boolean {
    Test.assertEqual("00:07", WorkoutView.formatTime(7));
    Test.assertEqual("31:20", WorkoutView.formatTime(1880));
    Test.assertEqual("1:02:03", WorkoutView.formatTime(3723));
    return true;
}

(:test)
function activityTablesStayAligned(logger as Logger) as Boolean {
    Test.assertEqual(Activities.KEYS.size(), Activities.LABELS.size());
    Test.assertEqual(Activities.KEYS.size(), Activities.SPORTS.size());
    Test.assertEqual("RUN", Activities.KEYS[0]);
    return true;
}
