package com.responsivemovement;

/** One turn budget per prepared frame, with a retained heading for short arrivals. */
final class MovementFacing
{
    private double angle;
    private double movementHeading;

    void reset(int nativeAngle)
    {
        angle = movementHeading = nativeAngle & 2047;
    }

    void movement(double dx, double dy)
    {
        movementHeading = MotionMath.heading(dx, dy);
    }

    void hold() { movementHeading = angle; }

    void advance(int nativeTarget, boolean followNative, double maximumStep)
    {
        // Reaching a tile does not force a snap or freeze an unfinished turn.
        // The controller holds the last travel heading until native facing is eligible.
        double target = followNative ? nativeTarget : movementHeading;
        double error = (target - angle + 3072) % 2048 - 1024;
        double distance = Math.abs(error), budget = Math.max(0, maximumStep);
        // Ease the last few degrees instead of abruptly going from the turn cap
        // to zero on every small connector. Integrate in turn-budget units so a
        // held target behaves identically when a frame is split into subframes.
        double tail = 32;
        double linear = Math.min(budget, Math.max(0, distance - tail));
        double remainder = (distance - linear) * Math.exp(-(budget - linear) / tail);
        double step = distance - remainder;
        if (distance <= Math.min(budget, 0.125)) { step = distance; }
        angle = MotionMath.turn(angle, target, step);
        // Once native facing takes over, a later idle click must hold the
        // currently displayed angle, not resume an earlier route's heading.
        if (followNative) { movementHeading = angle; }
    }

    double angle() { return angle; }
}
