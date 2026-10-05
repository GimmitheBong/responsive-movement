package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiagonalReversalTest
{
    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void capturedShortDiagonalReturnsImmediatelyInsteadOfFinishingTheOldLeg()
    {
        // 18:55:48 capture: visible (5857,5857), authority/destination (5952,5952),
        // previous destination (5824,5824). Use 480ms for an exact sub-tile replay.
        LocalPoint start = point(5952, 5952), oldTarget = point(5824, 5824);
        MovementPath path = MovementPath.anticipate(start, start, oldTarget, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> true);
        advance(path, 0, 480);
        assertEquals(point(5856, 5856), path.position());
        MovementPath reverse = path.anticipateReversal(start, true, 480_000_000L);
        assertNotNull(reverse);
        assertEquals(path.position(), reverse.position());
        assertFalse(reverse.running());
        advance(reverse, 480, 500);
        assertEquals(point(5860, 5860), reverse.position());
        advance(reverse, 500, 960);
        assertEquals(start, reverse.position());
        assertTrue(reverse.finished());
    }

    @Test
    public void allDiagonalDirectionsKeepTheFractionAndCollisionCheckedReturn()
    {
        for (int dx : new int[] {-1, 1})
        {
            for (int dy : new int[] {-1, 1})
            {
                LocalPoint start = point(5952, 5952), end = point(5952 + dx * 256, 5952 + dy * 256);
                MovementPath path = MovementPath.idle(start, 0, 1, (a, b) -> true);
                assertTrue(path.accept(end, true));
                advance(path, 0, 200);
                MovementPath reverse = path.anticipateReversal(start, true, 200_000_000L);
                assertNotNull(reverse);
                assertEquals(path.position(), reverse.position());
                advance(reverse, 200, 220);
                assertEquals(point(path.position().getX() - dx * 8, path.position().getY() - dy * 8), reverse.position());
                assertTrue(reverse.accept(point(5952 + dx * 128, 5952 + dy * 128), true));
                advance(reverse, 220, 400);
                assertTrue(reverse.accept(start, true));
                advance(reverse, 400, 1000);
                assertEquals(start, reverse.position());
                assertTrue(reverse.finished());
                assertTrue(reverse.clear());
            }
        }
    }

    @Test
    public void blockedReverseEdgeAndDifferentDiagonalMustNotBeShortcuts()
    {
        LocalPoint start = point(5952, 5952), end = point(6208, 6208);
        MovementPath oneWay = MovementPath.idle(start, 0, 1, (a, b) -> b.getX() > a.getX() && b.getY() > a.getY());
        assertTrue(oneWay.accept(end, true));
        advance(oneWay, 0, 200);
        assertNull(oneWay.anticipateReversal(start, true, 200_000_000L));
        MovementPath path = MovementPath.idle(start, 0, 1, (a, b) -> true);
        assertTrue(path.accept(end, true));
        advance(path, 0, 200);
        assertNull(path.anticipateReversal(point(5824, 5952), true, 200_000_000L));
        assertNull(path.anticipateReversal(start, true, 2000_000_000L));
    }
}
