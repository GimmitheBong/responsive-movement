package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

/** Captured coordinates with synthetic collision fixtures, not recorded scene maps. */
public class SceneContinuityTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;

    @Test
    public void firstReportedCrossingKeepsSouthboundPreviewInsteadOfReturningToAuthority()
    {
        // 2026-09-29 23:51:08.189 +10:00: draw=(7104,1878), authority=(7104,2240).
        // Next scene adds 5120 to Y; authority advances to old Y=1984.
        MovementPath path = preview(7104, 2496, 7232, 1088, OPEN);
        advance(path, 0, 600);
        assertTrue(path.accept(point(7104, 2240), true));
        advance(path, 600, 1404);
        assertEquals(point(7104, 1878), path.position());
        assertTrue(path.resumeScene(0, 5120, point(7104, 7104), point(7232, 6208), true, nanos(1436)));
        assertEquals(point(7104, 6998), path.position());
        assertEquals("preview", path.phase());
        assertForward(path, 1436, 1924);
        assertTrue(path.accept(point(7104, 6848), true));
        assertForward(path, 1924, 2200);
    }

    @Test
    public void secondReportedCrossingTranslatesBothAxesWithoutTheBackwardStepAndWait()
    {
        // 23:52:41.188: draw=(6080,1838), authority=(6080,2112).
        // Rebase adds (1024,5120); next authority is (7104,6976).
        MovementPath path = preview(6080, 2496, 6080, 1344, OPEN);
        advance(path, 0, 600);
        assertTrue(path.accept(point(6080, 2240), true));
        advance(path, 600, 1200);
        assertTrue(path.accept(point(6080, 2112), true));
        advance(path, 1200, 1496);
        assertEquals(point(6080, 1838), path.position());
        assertTrue(path.resumeScene(1024, 5120, point(7104, 6976), point(7104, 6464), true, nanos(1530)));
        assertEquals(point(7104, 6958), path.position());
        assertForward(path, 1530, 2020);
        assertTrue(path.accept(point(7104, 6720), true));
        assertForward(path, 2020, 2300);
    }

    @Test
    public void repeatedRebasesWithoutProgressDoNotRenewPredictionOrSpendLoadingTime()
    {
        MovementPath path = preview(6080, 2496, 6080, 1344, OPEN);
        advance(path, 0, 100);
        long hardDeadline = path.predictionDeadlineNanos();
        int y = path.position().getY();
        assertTrue(path.resumeScene(0, 5120, point(6080, 7616), point(6080, 6464), true, nanos(300)));
        path.advance(nanos(300));
        assertEquals(y + 5120, path.position().getY());
        assertTrue(path.resumeScene(0, -5120, point(6080, 2496), point(6080, 1344), true, nanos(700)));
        assertEquals(hardDeadline, path.predictionDeadlineNanos());
        path.advance(nanos(899));
        assertEquals("preview", path.phase());
        path.advance(nanos(901));
        assertEquals("recovery", path.phase());
        advance(path, 901, 2000);
        assertEquals(point(6080, 2496), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void expiredOrChangedDestinationsDoNotResurrectTheOldPreview()
    {
        for (boolean expired : new boolean[] {false, true})
        {
            MovementPath path = preview(6080, 2496, 6080, 1344, OPEN);
            advance(path, 0, 100);
            int now = expired ? 2000 : 200;
            LocalPoint destination = point(6080, expired ? 6464 : 7744);
            assertTrue(path.resumeScene(0, 5120, point(6080, 7616), destination, true, nanos(now)));
            assertEquals("confirmed", path.phase());
            advance(path, now, now + 500);
            assertEquals(point(6080, 7616), path.position());
            assertTrue(path.finished());
        }
    }

    @Test
    public void newCollisionMapMustValidateTheRetainedPreviewAndRecovery()
    {
        boolean[] open = {true};
        MovementPath path = preview(6080, 2496, 6080, 1344, (a, b) -> open[0]);
        advance(path, 0, 100);
        open[0] = false;
        assertFalse(path.resumeScene(0, 5120, point(6080, 7616), point(6080, 6464), true, nanos(200)));
    }

    @Test
    public void confirmedCornersSurviveRecoveryInTheirOriginalOrder()
    {
        MovementPath path = MovementPath.idle(point(1344, 1344), 0, 1, OPEN);
        assertTrue(path.accept(point(1472, 1472), false));
        assertTrue(path.accept(point(1600, 1344), false));
        advance(path, 0, 200);
        LocalPoint before = path.position();
        assertTrue(path.resumeScene(1024, 5120, point(2624, 6464), null, false, nanos(1000)));
        assertEquals(point(before.getX() + 1024, before.getY() + 5120), path.position());
        advance(path, 1000, 1440);
        assertEquals(point(2496, 6592), path.position());
        advance(path, 1440, 2080);
        assertEquals(point(2624, 6464), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void arrivalMayClearTheDestinationWhileConfirmingTheLastPreview()
    {
        MovementPath path = preview(6080, 2496, 6080, 2240, OPEN);
        advance(path, 0, 100);
        assertTrue(path.resumeScene(0, 5120, point(6080, 7360), null, true, nanos(200)));
        assertEquals("confirmed", path.phase());
        assertForward(path, 200, 600);
        advance(path, 600, 800);
        assertEquals(point(6080, 7360), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void checkedKnightKeepsItsFractionalTrajectoryAcrossTheRebase()
    {
        MovementPath path = preview(6080, 2496, 6336, 2368, OPEN);
        advance(path, 0, 100);
        LocalPoint before = path.position();
        assertTrue(path.resumeScene(1024, 5120, point(7104, 7616), point(7360, 7488), true, nanos(200)));
        assertEquals(point(before.getX() + 1024, before.getY() + 5120), path.position());
        advance(path, 200, 300);
        assertEquals(point(before.getX() + 1068, before.getY() + 5098), path.position());
        assertTrue(path.clear());
    }

    @Test
    public void aDifferentWorldViewCannotInheritTheRoute()
    {
        MovementPath path = preview(6080, 2496, 6080, 1344, OPEN);
        assertFalse(path.resumeScene(0, 0, new LocalPoint(6080, 2496, 7),
            new LocalPoint(6080, 1344, 7), true, nanos(200)));
    }

    private static MovementPath preview(int x, int y, int goalX, int goalY, BiPredicate<LocalPoint, LocalPoint> collision)
    {
        MovementPath path = MovementPath.anticipate(point(x, y), point(x, y), point(goalX, goalY),
            true, 0, 1.1, MovementPath.freshDeadline(0), collision);
        assertNotNull(path);
        return path;
    }

    private static void assertForward(MovementPath path, int from, int to)
    {
        for (int time = from + 4; time <= to; time += 4)
        {
            LocalPoint before = path.position();
            path.advance(nanos(time));
            assertTrue("southbound progress at " + time, path.position().getY() < before.getY());
            assertTrue(path.clear());
        }
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int time = from + 4; time < to; time += 4) { path.advance(nanos(time)); }
        path.advance(nanos(to));
    }

    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }
    private static long nanos(int millis) { return millis * 1_000_000L; }
}
