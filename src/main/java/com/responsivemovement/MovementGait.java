package com.responsivemovement;

/** Holds the native locomotion pose through a short gap between confirmed legs. */
final class MovementGait
{
    private static final long BRIDGE_NANOS = 100_000_000L;
    private long lastMoving = -1;
    private boolean lastRunning;

    boolean present(boolean moving, boolean running, boolean confirmedFinished,
        boolean destinationAhead, boolean nativeInTransit, boolean ordinaryMovement,
        boolean noActionOrEffect, long now)
    {
        if (moving)
        {
            if (ordinaryMovement && noActionOrEffect)
            {
                lastMoving = now;
                lastRunning = running;
            }
            else { reset(); }
            return true;
        }
        if (confirmedFinished && destinationAhead && nativeInTransit && ordinaryMovement && noActionOrEffect &&
            lastMoving >= 0 && now >= lastMoving && now - lastMoving <= BRIDGE_NANOS)
        {
            return true;
        }
        reset();
        return false;
    }

    boolean running() { return lastRunning; }
    void reset() { lastMoving = -1; lastRunning = false; }
}
