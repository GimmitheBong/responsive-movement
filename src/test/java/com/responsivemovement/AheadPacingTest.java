package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Leading previews slow under the same route/clock; true authority and credit remain unchanged. */
public class AheadPacingTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void walkAndRunPreviewsGentlySlowAsTheyLeadAuthority()
    {
        for (boolean run : new boolean[] {false, true})
        {
            MovementPath base = preview(run), slowed = preview(run);
            long horizon = slowed.predictionDeadlineNanos();
            advance(base, 0, 400, 20, 0, 0);
            advance(slowed, 0, 400, 20, 0, 10);
            int normal = base.position().getX() - tile(10, 10).getX();
            int actual = slowed.position().getX() - tile(10, 10).getX();
            assertTrue(actual < normal);
            assertTrue(actual >= normal * 0.9);
            assertEquals(tile(10, 10), slowed.confirmed());
            assertEquals(horizon, slowed.predictionDeadlineNanos());
            assertTrue(slowed.clear());
        }
    }

    @Test
    public void zeroAndClampedStrengthsRetainPositiveBoundedPreviewPace()
    {
        int previous = Integer.MAX_VALUE;
        for (int percent : new int[] {-10, 0, 5, 10, 25, 50, 100})
        {
            MovementPath path = preview(true);
            advance(path, 0, 500, 20, 0, percent);
            int travelled = path.position().getX() - tile(10, 10).getX();
            assertTrue(travelled <= previous);
            assertTrue("even maximum slowdown keeps at least half pace", travelled >= 100);
            if (percent <= 0) { assertEquals(200, travelled); }
            previous = travelled;
        }
    }

    @Test
    public void slowdownIsFrameIndependentAcrossRunCornersAndKnights()
    {
        for (LocalPoint goal : new LocalPoint[] {tile(14, 10), tile(12, 11), tile(11, 13)})
        {
            LocalPoint reference = null;
            for (int cadence : new int[] {8, 20, 33, 100})
            {
                MovementPath path = MovementPath.anticipate(tile(10, 10), tile(10, 10), goal,
                    true, 0, 1, MovementPath.freshDeadline(0), OPEN);
                advance(path, 0, 600, cadence, 0, 25);
                if (reference == null) { reference = path.position(); }
                assertEquals(reference, path.position());
                assertTrue(path.clear());
            }
        }
    }

    @Test
    public void catchUpAndSlowdownShareTheConfirmedToPreviewBoundaryAndOneClock()
    {
        LocalPoint reference = null;
        for (int cadence : new int[] {8, 20, 33, 100})
        {
            MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
            assertTrue(path.accept(tile(11, 10), false));
            assertTrue(path.anticipateContinuation(tile(14, 10), false, 0));
            long horizon = path.predictionDeadlineNanos();
            advance(path, 0, 800, cadence, 50, 50);
            if (reference == null) { reference = path.position(); }
            assertEquals(reference, path.position());
            assertTrue(path.position().getX() > tile(11, 10).getX());
            assertTrue(path.position().getX() < tile(11, 10).getX() + 57);
            assertEquals(horizon, path.predictionDeadlineNanos());
        }
    }

    @Test
    public void slowdownDoesNotReduceConfirmedCatchUpOrOrdinaryTravel()
    {
        MovementPath base = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        MovementPath both = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(base.accept(tile(12, 10), true));
        assertTrue(both.accept(tile(12, 10), true));
        advance(base, 0, 300, 20, 10, 0);
        advance(both, 0, 300, 20, 10, 50);
        assertEquals(base.position(), both.position());
        assertTrue(base.traceSnapshot().same(both.traceSnapshot()));
    }

    @Test
    public void partialKnightAuthoritySlowsOnlyAfterTheLogicalConfirmedProgress()
    {
        MovementPath base = previewKnight(), slowed = previewKnight();
        assertTrue(base.accept(tile(11, 10), false));
        assertTrue(slowed.accept(tile(11, 10), false));
        advance(base, 0, 600, 20, 0, 0);
        advance(slowed, 0, 600, 20, 0, 50);
        assertEquals("no slowdown on the confirmed half of the unsplit chord", base.position(), slowed.position());
        advance(base, 600, 800, 20, 0, 0);
        advance(slowed, 600, 800, 20, 0, 50);
        assertTrue(slowed.position().getX() < base.position().getX());
        assertTrue(slowed.position().getX() >= tile(11, 10).getX());
        assertTrue(slowed.clear());
    }

    @Test
    public void staleReversalsAndUnavailableOriginsDoNotBecomeFalseAheadEvidence()
    {
        MovementPath base = preview(true), slowed = preview(true);
        advance(base, 0, 200, 20, 0, 0);
        advance(slowed, 0, 200, 20, 0, 0);
        base = base.retargetWalk(tile(9, 10), true, 200_000_000L);
        slowed = slowed.retargetWalk(tile(9, 10), true, 200_000_000L);
        assertNotNull(base); assertNotNull(slowed);
        assertTrue(base.accept(tile(12, 10), true));
        assertTrue(slowed.accept(tile(12, 10), true));
        advance(base, 200, 300, 20, 0, 0);
        advance(slowed, 200, 300, 20, 0, 50);
        assertEquals(base.position(), slowed.position());
        assertTrue(base.traceSnapshot().same(slowed.traceSnapshot()));
    }

    @Test
    public void aSlowedUnconfirmedPreviewStillExpiresAndRecoversAtBasePace()
    {
        MovementPath path = preview(true);
        advance(path, 0, 880, 20, 0, 50);
        long horizon = path.predictionDeadlineNanos();
        path.advance(900_000_000L, false, 0, 50);
        assertEquals("recovery", path.phase());
        LocalPoint before = path.position();
        path.advance(920_000_000L, false, 0, 50);
        assertEquals(8, before.getX() - path.position().getX());
        assertEquals(horizon, path.predictionDeadlineNanos());
        advance(path, 920, 2500, 20, 0, 50);
        assertTrue(path.finished());
        assertEquals(tile(10, 10), path.position());
    }

    @Test
    public void translationAndRepeatedPreparationDoNotResetAheadPacing()
    {
        MovementPath base = preview(true), shifted = preview(true);
        advance(base, 0, 200, 20, 0, 25);
        advance(shifted, 0, 200, 20, 0, 25);
        LocalPoint before = shifted.position();
        shifted.advance(200_000_000L, false, 0, 50);
        assertEquals(before, shifted.position());
        assertTrue(shifted.resumeScene(-128, -256, tile(9, 8), tile(13, 8), true, 200_000_000L));
        advance(base, 200, 400, 20, 0, 25);
        advance(shifted, 200, 400, 20, 0, 25);
        assertEquals(base.position().getX() - 128, shifted.position().getX());
        assertEquals(base.position().getY() - 256, shifted.position().getY());
    }

    @Test
    public void combatForecastsKeepTheirExistingRangeAndPursuitPace()
    {
        Fixture f = new Fixture(tile(10, 10).getX(), tile(10, 10).getY(), tile(14, 10).getX(), tile(14, 10).getY());
        try
        {
            NpcApproach npc = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_SECOND_OPTION, "Attack"), null);
            MovementPath base = MovementPath.idle(f.start, 0, 1, OPEN), slowed = MovementPath.idle(f.start, 0, 1, OPEN);
            base.armCombat(0); slowed.armCombat(0);
            base = base.followCombat(npc, true, 0, false);
            slowed = slowed.followCombat(npc, true, 0, false);
            assertNotNull(base); assertNotNull(slowed);
            advance(base, 0, 400, 20, 0, 0);
            advance(slowed, 0, 400, 20, 0, 50);
            assertEquals(base.position(), slowed.position());
            assertTrue(base.traceSnapshot().same(slowed.traceSnapshot()));
        }
        finally { f.controller.close(); }
    }

    @Test
    public void liveControllerToggleWorksForSceneAndMinimapWithoutChangingTheBaseSpeed()
    {
        for (boolean minimap : new boolean[] {false, true})
        {
            Fixture f = new Fixture(tile(10, 10).getX(), tile(10, 10).getY(), 7000, 7000);
            try
            {
                f.smoothing = 0; f.slowAhead = true; f.slowAheadPercent = 50;
                if (minimap) { f.destination = tile(14, 10); f.controller.walkClick(null); }
                else { f.controller.walkClick(); f.destination = tile(14, 10); }
                f.frame(0); f.frame(100);
                int first = f.controller.position().getX() - f.start.getX();
                assertTrue(first < 40 && first >= 20);
                f.slowAhead = false;
                int before = f.controller.position().getX(); f.frame(200);
                assertEquals(40, f.controller.position().getX() - before);
                f.slowAhead = true; f.slowAheadPercent = 0;
                before = f.controller.position().getX(); f.frame(300);
                assertEquals(40, f.controller.position().getX() - before);
                assertEquals(1, f.speed, 0);
            }
            finally { f.controller.close(); }
        }
    }

    private static MovementPath preview(boolean run)
    {
        return MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10),
            run, 0, 1, MovementPath.freshDeadline(0), OPEN);
    }

    private static MovementPath previewKnight()
    {
        return MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(12, 11),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
    }

    private static void advance(MovementPath path, int from, int to, int cadence, int boost, int slowdown)
    {
        for (int ms = from + cadence; ms < to; ms += cadence) { path.advance(ms * 1_000_000L, false, boost, slowdown); }
        path.advance(to * 1_000_000L, false, boost, slowdown);
    }
}
