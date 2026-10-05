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
        angle = MotionMath.turn(angle, followNative ? nativeTarget : movementHeading, maximumStep);
        // Once native facing takes over, a later idle click must hold the
        // currently displayed angle, not resume an earlier route's heading.
        if (followNative) { movementHeading = angle; }
    }

    double angle() { return angle; }
}
