package uk.co.eaton.drive.routing;

/** Driving preferences passed to the local BRouter calculation. */
public final class RouteOptions {
    public final boolean avoidMotorways, avoidTolls;
    public final int alternativeIndex;

    public RouteOptions(boolean avoidMotorways, boolean avoidTolls, int alternativeIndex) {
        if (alternativeIndex < 0 || alternativeIndex > 1)
            throw new IllegalArgumentException("Only primary and one alternate are supported");
        this.avoidMotorways = avoidMotorways;
        this.avoidTolls = avoidTolls;
        this.alternativeIndex = alternativeIndex;
    }
    public RouteOptions alternative() {
        return new RouteOptions(avoidMotorways, avoidTolls, 1);
    }
    public RouteOptions primary() {
        return new RouteOptions(avoidMotorways, avoidTolls, 0);
    }
}
