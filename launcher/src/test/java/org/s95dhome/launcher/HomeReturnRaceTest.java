package org.s95dhome.launcher;

/** Deterministic ordering tests: do not emulate Tizen visibility or claim physical zero-flash. */
public final class HomeReturnRaceTest {
    private static final String SAMSUNG = "com.samsung.tv.dummylauncher";
    private static final String LOCK = SAMSUNG + ".LockActivity";
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static HomeReturnRace begin(int delay) {
        HomeReturnRace race = new HomeReturnRace(); race.begin(1000, delay); return race;
    }
    public static void main(String[] args) {
        HomeReturnRace quick=begin(0);quick.submitted(1000,1001);
        check(quick.duplicateHome(1000,1400),"same Home duplicate suppressed");
        quick.cancel();
        check(!quick.duplicateHome(1000,1400),"next Home after explicit app/source action must not be swallowed");
        quick.begin(1400,0);quick.submitted(1400,1401);
        check(quick.duplicateHome(1400,1500),"new Home retains duplicate protection");
        check(!quick.duplicateHome(1400,3400),"next independent press after window accepted");
        for (int delay : new int[]{0, 80}) {
            HomeReturnRace race = begin(delay);
            check(!race.shouldCorrect(SAMSUNG, LOCK, 1020, 1030), "acted before initial request");
            race.submitted(1000, 1000 + delay);
            check(!race.shouldCorrect("org.s95dhome.launcher", "org.s95dhome.launcher.HomeActivity", 1100, 1101), "self-loop");
            check(!race.shouldCorrect(SAMSUNG, SAMSUNG + ".WebBrowserResolveActivity", 1100, 1101), "unrelated Samsung screen");
            check(!race.shouldCorrect("com.other.app", LOCK, 1100, 1101), "other app triggered correction");
            check(!race.shouldCorrect(SAMSUNG, LOCK, 900, 1101), "stale transition triggered correction");
            check(race.shouldCorrect(SAMSUNG, LOCK, 1100, 1101), "late Samsung transition was not followed");
            check(!race.shouldCorrect(SAMSUNG, LOCK, 1110, 1111), "coalesce burst notifications");
            check(race.shouldCorrect(SAMSUNG, LOCK, 4895, 4900), "observed 3.9-second Samsung takeover must be corrected");
            check(!race.shouldCorrect(SAMSUNG, LOCK, 5900, 5901), "no more than two corrections per Home action");
        }
        for (int delay : new int[]{160, 300, 650}) {
            HomeReturnRace race = begin(delay); race.submitted(1000, 1000 + delay);
            check(race.shouldCorrect(SAMSUNG, LOCK, 1800, 1801), "observed takeover corrected in every selected speed");
        }
        HomeReturnRace race = begin(0); race.submitted(1000, 1001); race.cancel();
        check(!race.active(1000), "cancelled scheduled return still active");
        check(!race.shouldCorrect(SAMSUNG, LOCK, 1100, 1101), "explicit user app launch was overridden");
        race = begin(0); race.submitted(1000, 1001);
        check(!race.shouldCorrect(SAMSUNG, LOCK, 7100, 7101), "correction escaped bounded observation window");
        race.begin(3000, 0);
        check(!race.active(1000), "previous Home scheduled request survived next Home event");
        race.submitted(1000, 3100);
        check(!race.shouldCorrect(SAMSUNG, LOCK, 3150, 3151), "stale request armed next event");
        race.submitted(3000, 3100);
        check(race.shouldCorrect(SAMSUNG, LOCK, 3150, 3151), "new Home event cannot follow transition");
        System.out.println("PASS: initial and observed 3.9-second takeover; burst coalescing; two-correction cap; all speed settings; explicit action cancellation; six-second deadline; consecutive Home isolation.");
    }
}
