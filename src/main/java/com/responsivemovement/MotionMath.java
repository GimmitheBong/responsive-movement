package com.responsivemovement;

final class MotionMath
{
    private MotionMath() {}

    static int difference(int from, int to) { return ((to - from + 1024) & 2047) - 1024; }

    static int heading(double dx, double dy)
    {
        double degrees = (360 - Math.toDegrees(Math.atan2(dy, dx)) - 90) % 360;
        if (degrees < 0) { degrees += 360; }
        return ((int) (degrees / 360 * 2048)) & 2047;
    }

    static double turn(double current, double target, double maximumStep)
    {
        // Keep fractional angles: rounding before calculating the difference
        // can overshoot a small target or make slow turns depend on frame rate.
        target = (target % 2048 + 2048) % 2048;
        current = (current % 2048 + 2048) % 2048;
        double delta = (target - current + 3072) % 2048 - 1024;
        double step = Math.max(0, maximumStep);
        if (Math.abs(delta) <= step) { return target; }
        double next = current + Math.copySign(step, delta);
        return (next + 2048) % 2048;
    }
}
