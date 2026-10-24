package uk.co.eaton.drive.util;
/** GPS Location.getSpeed() is metres per second. */
public final class SpeedMath {
    private static final double MPH_PER_MPS = 2.2369362921;
    private SpeedMath() { }
    public static int toMph(float mps) {
        if (Float.isNaN(mps) || Float.isInfinite(mps) || mps < 0f)
            throw new IllegalArgumentException("Invalid speed");
        return (int) Math.round(mps * MPH_PER_MPS);
    }
    public static boolean isFresh(long nowElapsedMs, long locationElapsedMs) {
        return nowElapsedMs >= locationElapsedMs && nowElapsedMs - locationElapsedMs <= 6000L;
    }
}
