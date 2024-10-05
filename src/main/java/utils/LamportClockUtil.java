package utils;

import java.util.concurrent.atomic.AtomicInteger;

public class LamportClockUtil {
    // Singleton instance, eager initialization
    private static final LamportClockUtil INSTANCE = new LamportClockUtil();

    private final AtomicInteger time;

    /**
     * Private constructor to prevent instantiation from other classes.
     */
    private LamportClockUtil() {
        this.time = new AtomicInteger(0);
    }

    /**
     * Provides access to the singleton instance.
     *
     * @return the singleton instance of LamportClockUtil
     */
    public static LamportClockUtil getInstance() {
        return INSTANCE;
    }

    /**
     * Increment the local time, used for handling local events.
     *
     * @return the incremented local time
     */
    public int increment() {
        return time.incrementAndGet();
    }

    /**
     * Increment the local time before sending a message.
     *
     * @return the incremented local time
     */
    public int sendEvent() {
        return increment();
    }

    /**
     * Update the local time upon receiving a message.
     * The local time is updated to max(localTime, receivedTime) + 1.
     *
     * @param receivedTime the time received from the message
     */
    public void receiveEvent(int receivedTime) {
        // Update the local time to max(localTime, receivedTime) + 1
//        time.updateAndGet(localTime -> Math.max(localTime, receivedTime) + 1);
    }

    /**
     * Get the current local time.
     *
     * @return the current local time
     */
    public int getTime() {
        return time.get();
    }
}
