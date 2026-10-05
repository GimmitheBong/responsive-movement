package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementFacingTest
{
    @Test
    public void aTinyFinalLegCannotAccelerateTheTurnAndArrivalFinishesSmoothly()
    {
        LocalPoint start = new LocalPoint(1344, 1344, 0), end = new LocalPoint(1472, 1344, 0);
        MovementPath path = MovementPath.idle(start, 0, 1, (a, b) -> true);
        assertTrue(path.accept(end, false));
        for (int ms = 100; ms <= 600; ms += 100) { path.advance(ms * 1_000_000L); }
        assertEquals(end.getX() - 8, path.position().getX());

        MovementFacing facing = new MovementFacing();
        facing.reset(0);
        path.advance(640_000_000L);
        assertTrue(path.finished());
        assertEquals(1, path.turnFraction(), 0);
        facing.movement(path.turnX(), path.turnY());
        facing.advance(0, false, 10);
        assertEquals("arrival must not bypass the turn rate", 2038, facing.angle(), 0);
        // The native actor still faces the old way. A held yellow-click arrival
        // keeps turning to the last travel heading instead of freezing sideways.
        for (int frame = 0; frame < 60; ++frame) { facing.advance(0, false, 10); }
        assertEquals(1536, facing.angle(), 0);
    }

    @Test
    public void rapidDirectionChangesAndNativeFacingShareTheSameLimit()
    {
        MovementFacing facing = new MovementFacing();
        facing.reset(0);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {1, -1}, {-1, 1}};
        for (int[] direction : directions)
        {
            double before = facing.angle();
            facing.movement(direction[0], direction[1]);
            facing.advance(1024, false, 3.5);
            assertTrue(angularDistance(before, facing.angle()) <= 3.5 + 0.000001);
        }
        double before = facing.angle();
        facing.advance(1024, true, 3.5);
        assertEquals(3.5, angularDistance(before, facing.angle()), 0.000001);
    }

    @Test
    public void fractionalTurningMatchesAcrossFrameRatesAndSpeedChanges()
    {
        MovementFacing split = new MovementFacing(), whole = new MovementFacing();
        split.reset(2000); whole.reset(2000);
        split.movement(-1, 0); whole.movement(-1, 0);
        for (int frame = 0; frame < 100; ++frame) { split.advance(0, false, 0.3); }
        whole.advance(0, false, 30);
        assertEquals(whole.angle(), split.angle(), 0.000001);
        for (int frame = 0; frame < 100; ++frame) { split.advance(0, false, 0.7); }
        whole.advance(0, false, 70);
        assertEquals(whole.angle(), split.angle(), 0.000001);
        // Both crossed the orientation wrap with no rounding-driven speedup.
        assertEquals(52, split.angle(), 0.000001);
    }

    @Test
    public void anIdleClickAfterNativeFacingCannotResumeAnOldMovementHeading()
    {
        MovementFacing facing = new MovementFacing();
        facing.reset(0);
        facing.movement(1, 0);
        facing.advance(0, false, 10);
        facing.advance(512, true, 20.5);
        double beforeClick = facing.angle();
        for (int frame = 0; frame < 5; ++frame) { facing.advance(512, false, 10); }
        assertEquals(beforeClick, facing.angle(), 0);
    }

    @Test
    public void sceneResetDropsTheOldTravelHeading()
    {
        MovementFacing facing = new MovementFacing();
        facing.reset(0);
        facing.movement(1, 0);
        facing.advance(0, false, 10);
        facing.reset(1024);
        facing.advance(0, false, 10);
        assertEquals(1024, facing.angle(), 0);
    }

    private static double angularDistance(double from, double to)
    {
        return Math.abs((to - from + 3072) % 2048 - 1024);
    }
}
