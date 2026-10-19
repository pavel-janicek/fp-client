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
    Test.assertEqual("workout", Protocol.TYPE_WORKOUT);
    Test.assertEqual("ack", Protocol.TYPE_ACK);
    Test.assertEqual("pull", Protocol.TYPE_PULL);
    return true;
}

(:test)
function workoutMessageCarriesTypeVersionAndFields(logger as Logger) as Boolean {
    var message = Protocol.workoutMessage({ "id" => "42-7", "created" => 1700000000, "label" => "Run" });
    Test.assertEqual("workout", message["t"]);
    Test.assertEqual(1, message["v"]);
    Test.assertEqual("42-7", message["id"]);
    Test.assertEqual(1700000000, message["created"]);
    Test.assertEqual("Run", message["label"]);
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
