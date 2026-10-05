package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Footprint reservation belongs to the single path, including rebase and checked run chords. */
public class NpcPreviewBoundaryTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void clippingBeforeKnightMergingKeepsTheLogicalCornerAndConfirmedStepsCanFinish()
    {
        MovementPath path = MovementPath.anticipate(p(10, 10), p(10, 10), p(14, 11), true,
            0, 1, MovementPath.freshDeadline(0), OPEN, false, p(15, 11), p(15, 11));
        assertNotNull(path);
        for (int ms = 20; ms <= 580; ms += 20) { path.advance(ms * 1_000_000L); }
        assertTrue(path.accept(p(12, 10), true));
        for (int ms = 600; ms <= 1000; ms += 20) { path.advance(ms * 1_000_000L); }
        // The capped logical tile is the first half of the next running knight.
        assertEquals(p(13, 10), path.position());
        assertTrue(path.position().getX() <= p(13, 10).getX());
        assertFalse(path.moving());
        assertTrue(path.clear());
        assertTrue(path.accept(p(14, 11), true));
        for (int ms = 1020; ms <= 1500; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(p(14, 11), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void rebaseMovesNpcReservationWithTheWholeForecastAndDoesNotRenewIt()
    {
        LocalPoint start = p(10, 10), npc = p(16, 10), destination = p(15, 10);
        MovementPath path = MovementPath.anticipate(start, start, destination, true,
            0, 1, MovementPath.freshDeadline(0), OPEN, false, npc, npc);
        long deadline = path.predictionDeadlineNanos();
        path.advance(100_000_000L);
        assertTrue(path.resumeScene(1024, 5120, new LocalPoint(start.getX() + 1024, start.getY() + 5120, 0),
            new LocalPoint(destination.getX() + 1024, destination.getY() + 5120, 0), true, 200_000_000L));
        assertEquals(deadline, path.predictionDeadlineNanos());
        for (int ms = 220; ms <= 600; ms += 20) { path.advance(ms * 1_000_000L); }
        assertTrue(path.accept(new LocalPoint(p(12, 10).getX() + 1024, start.getY() + 5120, 0), true));
        for (int ms = 620; ms <= 1400; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(new LocalPoint(p(14, 10).getX() + 1024, start.getY() + 5120, 0), path.position());
        assertFalse(path.moving());
        assertTrue(path.clear());
    }

    @Test
    public void closeIdleSeedKeepsAPositionalReserveIndependentlyOfFrameCadenceAndSpeed()
    {
        for (double speed : new double[] {0.9, 1, 1.1, 2})
        {
            MovementPath fine = closeSeed(speed), coarse = closeSeed(speed);
            long deadline = fine.predictionDeadlineNanos();
            for (int ms = 20; ms <= 800; ms += 20) { fine.advance(ms * 1_000_000L); }
            for (int ms = 100; ms <= 800; ms += 100) { coarse.advance(ms * 1_000_000L); }
            assertEquals(fine.position(), coarse.position());
            assertTrue(fine.position().getX() > p(10, 10).getX());
            assertTrue(fine.position().getX() < p(11, 10).getX());
            assertTrue("no artificial idle publication", fine.moving());
            assertEquals(deadline, fine.predictionDeadlineNanos());
            assertEquals(p(10, 10), fine.confirmed());
        }
    }

    @Test
    public void easingEndsOnConfirmationAndNeverExtendsThePredictionTimeout()
    {
        MovementPath confirmed = closeSeed(1), rejected = closeSeed(1);
        for (int ms = 20; ms <= 600; ms += 20)
        {
            confirmed.advance(ms * 1_000_000L);
            rejected.advance(ms * 1_000_000L);
        }
        assertTrue(confirmed.accept(p(12, 10), true));
        int before = confirmed.position().getX();
        confirmed.advance(620_000_000L);
        assertEquals("restore ordinary run pacing", before + 8, confirmed.position().getX());
        rejected.advance(901_000_000L);
        assertEquals("recovery", rejected.phase());
        for (int ms = 921; ms <= 1601; ms += 20) { rejected.advance(ms * 1_000_000L); }
        assertEquals(p(10, 10), rejected.position());
        assertTrue(rejected.finished());
    }

    @Test
    public void longerAndQueuedNpcRoutesKeepTheirExistingPacing()
    {
        MovementPath longer = MovementPath.anticipate(p(10, 10), p(10, 10), p(14, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN, false, p(15, 10), p(15, 10));
        longer.advance(100_000_000L);
        assertEquals(p(10, 10).getX() + 40, longer.position().getX());

        MovementPath queued = MovementPath.idle(p(10, 10), 0, 1, OPEN);
        assertTrue(queued.accept(p(12, 10), true));
        assertTrue(queued.anticipateContinuation(p(14, 10), true, 0, false, p(15, 10), p(15, 10)));
        for (int ms = 20; ms <= 700; ms += 20) { queued.advance(ms * 1_000_000L); }
        assertEquals("do not ease a seed behind confirmed movement", p(10, 10).getX() + 280, queued.position().getX());
    }

    private static MovementPath closeSeed(double speed)
    {
        return MovementPath.anticipate(p(10, 10), p(10, 10), p(12, 10), true,
            0, speed, MovementPath.freshDeadline(0), OPEN, false, p(13, 10), p(13, 10));
    }

    @Test
    public void rangedBoundarySurvivesQueuedContinuationAndCloserNativeFlagsWithoutNewTime()
    {
        MovementPath path = MovementPath.idle(p(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(p(12, 10), true));
        assertTrue(path.anticipateContinuation(p(19, 10), true, 0, false, p(20, 10), p(20, 10), 7));
        long deadline = path.predictionDeadlineNanos();
        for (int ms = 20; ms <= 800; ms += 20) { path.advance(ms * 1_000_000L); }
        // Two confirmed run tiles take 640 ms; the next tile is halfway
        // traversed at 800 ms. The forecast boundary remains p(13,10).
        assertEquals(new LocalPoint(p(12, 10).getX() + 64, p(12, 10).getY(), 0), path.position());
        assertTrue(path.holdCombatForecast(p(19, 10), 800_000_000L));
        assertEquals(deadline, path.predictionDeadlineNanos());
        assertFalse(path.holdCombatForecast(p(19, 10), 901_000_000L));
        // A real one/two-tile server step may finish inside the reserved region.
        assertTrue(path.accept(p(14, 10), true));
        for (int ms = 820; ms <= 1400; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(p(14, 10), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void rangedReservationTranslatesAcrossRebaseWithoutFallingBackToTwoTiles()
    {
        MovementPath path = MovementPath.anticipate(p(10, 10), p(10, 10), p(19, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN, false, p(20, 10), p(20, 10), 7);
        path.advance(100_000_000L);
        assertTrue(path.resumeScene(1024, 5120,
            new LocalPoint(p(10, 10).getX() + 1024, p(10, 10).getY() + 5120, 0),
            new LocalPoint(p(19, 10).getX() + 1024, p(19, 10).getY() + 5120, 0), true, 200_000_000L));
        for (int ms = 220; ms <= 600; ms += 20) { path.advance(ms * 1_000_000L); }
        assertTrue(path.accept(new LocalPoint(p(12, 10).getX() + 1024, p(12, 10).getY() + 5120, 0), true));
        for (int ms = 620; ms <= 1400; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(new LocalPoint(p(13, 10).getX() + 1024, p(13, 10).getY() + 5120, 0), path.position());
        assertTrue(path.clear());
    }
}
