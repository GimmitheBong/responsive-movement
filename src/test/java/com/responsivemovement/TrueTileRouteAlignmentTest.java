package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** 18:42/18:43 trace endpoints; collision fixtures are synthetic, not recorded scene maps. */
public class TrueTileRouteAlignmentTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static long nanos(int millis) { return millis * 1_000_000L; }

    @Test
    public void parallelRowProgressJoinsForwardInsteadOfTimingOutAndTurningBack()
    {
        // The capture follows y=6720 while trueTileIndicator advances along y=6592.
        MovementPath path = offsetWalk(OPEN);
        LocalPoint before = path.position(), authority = p(4928, 6592);
        MovementPath aligned = path.alignWalkAuthority(authority, true, nanos(200));
        assertNotNull(aligned);
        assertEquals(before, aligned.position());
        assertEquals(authority, aligned.confirmed());
        for (int ms = 220; ms <= 2000; ms += 20)
        {
            if (ms == 700) { assertTrue(aligned.accept(p(5184, 6592), true)); }
            if (ms == 1300) { assertTrue(aligned.accept(p(5440, 6592), true)); }
            before = aligned.position();
            aligned.advance(nanos(ms));
            assertTrue("no westward rubber band at " + ms, aligned.position().getX() >= before.getX());
            assertTrue(MovementPath.distance(before, aligned.position()) <= 8);
            assertTrue(aligned.clear());
            assertNotEquals("matching true-tile progress must keep the forecast alive", "recovery", aligned.phase());
        }
        assertEquals(p(5440, 6592), aligned.position());
        assertTrue(aligned.finished());
    }

    @Test
    public void authorityAnchoredDetourKeepsTheConfirmedBendsInsteadOfTheParallelRoute()
    {
        // Synthetic wall/counter: the old upper-row preview is legal, but new
        // authority approaches around the lower side. No collision map is logged.
        BiPredicate<LocalPoint, LocalPoint> corridor = (a, b) ->
        {
            if (!corridorTile(a) || !corridorTile(b) || MovementPath.distance(a, b) != 128) { return false; }
            return a.getX() == b.getX() || a.getY() == b.getY() ||
                corridorTile(p(a.getX(), b.getY())) && corridorTile(p(b.getX(), a.getY()));
        };
        MovementPath path = offsetWalk(corridor).alignWalkAuthority(p(4928, 6592), true, nanos(200));
        assertNotNull(path);
        for (int ms = 220; ms <= 3000; ms += 20)
        {
            if (ms == 700) { assertTrue(path.accept(p(5184, 6592), true)); }
            if (ms == 1300) { assertTrue(path.accept(p(5312, 6464), true)); }
            if (ms == 1900) { assertTrue(path.accept(p(5440, 6592), true)); }
            LocalPoint before = path.position();
            path.advance(nanos(ms));
            assertTrue("no return to the obsolete row", path.position().getX() >= before.getX());
            assertTrue(MovementPath.distance(before, path.position()) <= 8);
            assertTrue(path.clear());
            assertNotEquals("recovery", path.phase());
        }
        assertEquals(p(5440, 6592), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void anUnchangedTileAndAnOlderOppositeTickCannotGrantAnotherPredictionBudget()
    {
        MovementPath path = offsetWalk(OPEN);
        long deadline = path.predictionDeadlineNanos();
        assertNull(path.alignWalkAuthority(path.confirmed(), true, nanos(200)));
        assertNull(path.alignWalkAuthority(p(4416, 6592), true, nanos(200)));
        assertEquals(deadline, path.predictionDeadlineNanos());
        LocalPoint before = path.position();
        assertNull(path.alignWalkAuthority(p(4928, 6592), true, nanos(1100)));
        assertEquals(before, path.position());
    }

    @Test
    public void blockedConnectorsAndUnsupportedAuthorityCannotShortcutTheOldCheckedEdge()
    {
        boolean[] blocked = {false};
        BiPredicate<LocalPoint, LocalPoint> wall = (a, b) -> !blocked[0] || a.getY() == b.getY();
        MovementPath path = offsetWalk(wall);
        LocalPoint before = path.position();
        blocked[0] = true;
        assertNull(path.alignWalkAuthority(p(4928, 6592), true, nanos(200)));
        assertNull(path.alignWalkAuthority(p(5696, 6592), true, nanos(200)));
        assertEquals(before, path.position());
        assertTrue(path.clear());
    }

    @Test
    public void alignmentRetainsFractionalTimingAndBoundedRecoveryIfAuthorityStops()
    {
        MovementPath path = offsetWalk(OPEN).alignWalkAuthority(p(4928, 6592), true, nanos(200));
        assertNotNull(path);
        LocalPoint before = path.position();
        path.advance(nanos(200));
        assertEquals("the already spent frame budget cannot be repeated", before, path.position());
        long deadline = path.predictionDeadlineNanos();
        assertNull(path.alignWalkAuthority(path.confirmed(), true, nanos(400)));
        assertEquals(deadline, path.predictionDeadlineNanos());
        for (int ms = 220; ms <= 4000; ms += 20)
        {
            before = path.position();
            path.advance(nanos(ms));
            assertTrue(MovementPath.distance(before, path.position()) <= 8);
            assertTrue(path.clear());
        }
        assertEquals(p(4928, 6592), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void forwardRowJoinsWorkInBothAxesAndBothDirections()
    {
        for (boolean vertical : new boolean[] {false, true})
        {
            for (int direction : new int[] {-1, 1})
            {
                for (int side : new int[] {-1, 1})
                {
                    LocalPoint start = tile(30, 30, vertical), authority = tile(30, 30 + side, vertical);
                    LocalPoint goal = tile(30 + direction * 6, 30 + side, vertical);
                    MovementPath path = MovementPath.anticipate(start, authority,
                        tile(30 + direction * 6, 30, vertical), true, 0, 1,
                        MovementPath.freshDeadline(0), OPEN);
                    assertNotNull(path);
                    advance(path, 0, 100);
                    path = path.retargetWalk(goal, true, nanos(100));
                    assertNotNull(path);
                    advance(path, 100, 200);
                    LocalPoint before = path.position();
                    path = path.alignWalkAuthority(tile(30 + direction * 2, 30 + side, vertical), true, nanos(200));
                    assertNotNull(path);
                    assertEquals(before, path.position());
                    for (int ms = 220; ms <= 1000; ms += 20)
                    {
                        before = path.position();
                        path.advance(nanos(ms));
                        int progress = vertical ? path.position().getY() - before.getY() : path.position().getX() - before.getX();
                        assertTrue("no reversal while joining authority", progress * direction >= 0);
                        assertTrue(MovementPath.distance(before, path.position()) <= 8);
                        assertTrue(path.clear());
                    }
                    assertEquals(vertical ? authority.getX() : authority.getY(),
                        vertical ? path.position().getX() : path.position().getY());
                }
            }
        }
    }

    @Test
    public void alignmentUsesTheSameClockAtDifferentFrameCadences()
    {
        MovementPath fast = offsetWalk(OPEN).alignWalkAuthority(p(4928, 6592), true, nanos(200));
        MovementPath slow = offsetWalk(OPEN).alignWalkAuthority(p(4928, 6592), true, nanos(200));
        assertNotNull(fast); assertNotNull(slow);
        for (int ms = 210; ms <= 800; ms += 10)
        {
            fast.advance(nanos(ms));
            if (ms % 40 == 0)
            {
                slow.advance(nanos(ms));
                assertEquals(fast.position(), slow.position());
            }
        }
    }

    @Test
    public void unfinishedConfirmedPrefixesKeepTheirExistingHandling()
    {
        MovementPath path = MovementPath.idle(p(4672, 6592), 0, 1, OPEN);
        assertTrue(path.accept(p(4800, 6720), false));
        advance(path, 0, 200);
        assertTrue(path.anticipateContinuation(p(5440, 6720), true, nanos(200)));
        assertNull(path.alignWalkAuthority(p(4928, 6592), true, nanos(200)));
        advance(path, 200, 640);
        assertEquals(p(4800, 6720), path.position());
    }

    private static MovementPath offsetWalk(BiPredicate<LocalPoint, LocalPoint> collision)
    {
        LocalPoint start = p(4672, 6720);
        MovementPath path = MovementPath.anticipate(start, p(4672, 6592), p(5312, 6720), true,
            0, 1, MovementPath.freshDeadline(0), collision);
        assertNotNull(path);
        advance(path, 0, 100);
        path = path.retargetWalk(p(5440, 6592), true, nanos(100));
        assertNotNull(path);
        advance(path, 100, 200);
        return path;
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 20; ms <= to; ms += 20) { path.advance(nanos(ms)); }
    }

    private static LocalPoint tile(int x, int y, boolean transpose)
    {
        return p((transpose ? y : x) * 128 + 64, (transpose ? x : y) * 128 + 64);
    }

    private static boolean corridorTile(LocalPoint point)
    {
        int x = point.getX(), y = point.getY();
        return x >= 4672 && x <= 5440 && (y == 6720 ||
            y == 6592 && (x <= 5184 || x == 5440) || y == 6464 && x >= 5184);
    }
}
