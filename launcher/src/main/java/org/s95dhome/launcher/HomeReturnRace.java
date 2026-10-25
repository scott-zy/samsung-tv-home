package org.s95dhome.launcher;

/** One Home action, one initial request, at most two event-driven corrections within six seconds. */
final class HomeReturnRace {
    static final long WINDOW_MS = 6000;
    private long event = -1, primary = -1;
    private int delay;
    private boolean cancelled;
    private int corrections;
    private long lastCorrection;
    void begin(long eventTime, int selectedDelay) {
        event = eventTime; primary = -1; delay = selectedDelay; corrections = 0; lastCorrection=-1; cancelled = false;
    }
    void submitted(long eventTime, long now) { if (event == eventTime && !cancelled) primary = now; }
    void cancel() { cancelled = true; }
    boolean duplicateHome(long previousEvent, long now) {
        // An explicit app/source action cancels the old race: its next Home is a new action.
        return active(previousEvent) && now >= previousEvent && now - previousEvent < 2000;
    }
    boolean active(long eventTime) { return event == eventTime && !cancelled; }
    boolean observing(long now) { return event >= 0 && !cancelled && now >= event && now - event <= WINDOW_MS; }
    long eventTime() { return event; }
    boolean shouldCorrect(String pkg, String activity, long receivedAt, long now) {
        if (!observing(now) || primary < 0 || receivedAt < primary || receivedAt > now || corrections >= 2) return false;
        if(lastCorrection>=0&&now-lastCorrection<750)return false;
        if (!"com.samsung.tv.dummylauncher".equals(pkg)) return false;
        if (!"com.samsung.tv.dummylauncher.LockActivity".equals(activity)
            && !"com.samsung.tv.dummylauncher.MainActivity".equals(activity)) return false;
        corrections++;lastCorrection=now;
        return true;
    }
}
