import Toybox.Activity;
import Toybox.Lang;

//! The activity types offered on the watch — the same set and keys as the Wear OS app
//! (`WorkoutActivityType`), so FitPub sees one vocabulary from both watches.
module Activities {
    const KEYS = ["RUN", "WALK", "HIKE", "BIKE", "OTHER"] as Array<String>;
    const LABELS = ["Run", "Walk", "Hike", "Bike", "Other"] as Array<String>;
    //! Sport of the native Garmin activity, as the Wear app maps them into its FIT.
    const SPORTS = [
        Activity.SPORT_RUNNING,
        Activity.SPORT_WALKING,
        Activity.SPORT_HIKING,
        Activity.SPORT_CYCLING,
        Activity.SPORT_GENERIC
    ] as Array<Activity.Sport>;
}
