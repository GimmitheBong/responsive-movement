package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementGaitTest
{
    @Test
    public void oneFrameConfirmedGapsKeepTheRunPoseWithoutAddingMovement()
    {
        // 20:29:57.237–20:30:13.477: a two-tile run ends a frame before
        // the next server endpoint, while the native actor is still in transit.
        LocalPoint start = new LocalPoint(2752, 4416, 0);
        LocalPoint first = new LocalPoint(3008, 4416, 0);
        LocalPoint second = new LocalPoint(3264, 4416, 0);
        MovementPath path = MovementPath.idle(start, 0, 1.1, (a, b) -> true);
        MovementGait gait = new MovementGait();
        assertTrue(path.accept(first, true));
        for (int ms = 20; ms <= 580; ms += 20)
        {
            path.advance(ms * 1_000_000L);
            assertTrue(gait.present(path.moving(), path.running(), path.finished(),
                true, true, true, true, ms * 1_000_000L));
        }
        path.advance(600_000_000L);
        assertFalse(path.moving());
        assertEquals(first, path.position());
        assertTrue(gait.present(false, false, path.finished(), true, true, true, true, 600_000_000L));
        assertTrue(gait.running());
        assertTrue(path.accept(second, true));
        path.advance(620_000_000L);
        assertTrue(gait.present(path.moving(), path.running(), path.finished(),
            true, true, true, true, 620_000_000L));
        assertTrue(path.running());
        assertTrue(path.position().getX() > first.getX());
    }

    @Test
    public void actualStopsActionsAndSceneChangesReleaseTheBridge()
    {
        MovementGait gait = new MovementGait();
        assertFalse(gait.present(false, false, true, true, true, true, true, 0));
        assertTrue(gait.present(true, false, false, true, true, true, true, 20_000_000L));
        assertFalse(gait.running());
        assertFalse(gait.present(false, false, true, false, true, true, true, 40_000_000L));
        assertTrue(gait.present(true, true, false, true, true, true, true, 50_000_000L));
        assertFalse(gait.present(false, false, true, true, true, true, false, 60_000_000L));
        assertTrue(gait.present(true, true, false, true, true, true, true, 70_000_000L));
        gait.reset();
        assertFalse(gait.present(false, false, true, true, true, true, true, 80_000_000L));
    }

    @Test
    public void staleDestinationCannotKeepRunningAfterTheShortGap()
    {
        MovementGait gait = new MovementGait();
        assertTrue(gait.present(true, true, false, true, true, true, true, 0));
        assertTrue(gait.present(false, false, true, true, true, true, true, 40_000_000L));
        assertFalse(gait.present(false, false, true, true, true, true, true, 120_000_000L));
        assertFalse(gait.present(false, false, true, true, true, true, true, 140_000_000L));
        assertTrue(gait.present(true, true, false, true, true, true, true, 200_000_000L));
        assertFalse(gait.present(false, false, true, true, false, true, true, 220_000_000L));
    }
}
