package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Confirmed catch-up pacing under the shared clock; no native renderer/game input. */
public class CatchUpPacingTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void modestDefaultBoostReducesWalkingAndRunningDebtWithoutOvershooting()
    {
        for (boolean run : new boolean[] {false, true})
        {
            MovementPath base = confirmed(run), boosted = confirmed(run);
            base.advance(100_000_000L);
            boosted.advance(100_000_000L, false, 10);
            int travelled = boosted.position().getX() - tile(10, 10).getX();
            assertTrue(boosted.position().getX() > base.position().getX());
            assertTrue(travelled <= (run ? 44 : 22));
            for (int ms = 120; ms <= 1400; ms += 20)
            {
                boosted.advance(ms * 1_000_000L, false, 10);
                assertTrue(boosted.position().getX() <= boosted.confirmed().getX());
            }
            assertTrue(boosted.finished());
        }
    }

    @Test
    public void boostTapersAsTheDisplayApproachesAuthority()
    {
        MovementPath path = confirmed(false);
        path.advance(100_000_000L, false, 50);
        int first = path.position().getX() - tile(10, 10).getX();
        advance(path, 100, 400, 20, false, 50);
        int before = path.position().getX();
        path.advance(500_000_000L, false, 50);
        int last = path.position().getX() - before;
        assertTrue(first > last);
        assertTrue("the taper retains at least base walking pace", last >= 20);
    }

    @Test
    public void strengthIsBoundedAndZeroRestoresBasePacing()
    {
        int previous = 0;
        for (int percent : new int[] {-10, 0, 5, 10, 25, 50, 100})
        {
            MovementPath path = confirmed(true);
            path.advance(100_000_000L, false, percent);
            int travelled = path.position().getX() - tile(10, 10).getX();
            assertTrue(travelled >= previous);
            if (percent <= 0) { assertEquals(40, travelled); }
            if (percent >= 50) { assertEquals(60, travelled); }
            previous = travelled;
        }
    }

    @Test
    public void queuedCornersAndKnightsRemainCadenceIndependent()
    {
        LocalPoint expected = null;
        for (int cadence : new int[] {8, 20, 33, 100})
        {
            MovementPath path = confirmed(true);
            assertTrue(path.accept(tile(13, 12), true));
            assertTrue(path.accept(tile(15, 13), true));
            advance(path, 0, 1100, cadence, false, 25);
            if (expected == null) { expected = path.position(); }
            assertEquals(expected, path.position());
            assertTrue(path.clear());
            assertTrue(path.position().getX() < path.confirmed().getX());
        }
    }

    @Test
    public void aConfirmedPrefixReturnsToBaseSpeedAtTheSpeculativeBoundaryInTheSameFrame()
    {
        MovementPath path = confirmed(false);
        assertTrue(path.anticipateContinuation(tile(13, 10), false, 0));
        long deadline = path.predictionDeadlineNanos();
        advance(path, 0, 600, 20, false, 50);
        assertTrue(path.position().getX() > tile(11, 10).getX());
        double arrivalMillis = 128 / (0.2 * 0.5) * Math.log1p(0.5);
        assertEquals("only leftover frame time advances the preview at base pace",
            tile(11, 10).getX() + Math.round((600 - arrivalMillis) * 0.2), path.position().getX());
        assertEquals("speculation is retained under its original horizon", deadline, path.predictionDeadlineNanos());
        int before = path.position().getX();
        path.advance(700_000_000L, false, 50);
        assertEquals(20, path.position().getX() - before);
        assertEquals(tile(11, 10), path.confirmed());
    }

    @Test
    public void previewAheadOfAuthorityAndFreshReversalKeepBasePacingAndBounds()
    {
        MovementPath base = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        MovementPath boosted = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(14, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        base.advance(100_000_000L);
        boosted.advance(100_000_000L, false, 50);
        assertEquals(base.position(), boosted.position());
        assertTrue(base.traceSnapshot().same(boosted.traceSnapshot()));
        base = base.retargetWalk(tile(9, 10), true, 100_000_000L);
        boosted = boosted.retargetWalk(tile(9, 10), true, 100_000_000L);
        assertNotNull(base); assertNotNull(boosted);
        base.advance(200_000_000L);
        boosted.advance(200_000_000L, false, 50);
        assertEquals(base.position(), boosted.position());
        assertTrue(base.traceSnapshot().same(boosted.traceSnapshot()));
    }

    @Test
    public void timeoutRecoveryDoesNotAcquireCatchUpAcceleration()
    {
        MovementPath base = preview(), boosted = preview();
        for (int ms = 20; ms <= 1600; ms += 20)
        {
            base.advance(ms * 1_000_000L);
            boosted.advance(ms * 1_000_000L, false, 50);
            assertEquals(base.position(), boosted.position());
        }
        assertTrue(boosted.finished());
    }

    @Test
    public void continuousRunRetainsItsSmallFinalReserveAndResumesAtActualArrival()
    {
        for (int cadence : new int[] {8, 20, 33})
        {
            MovementPath path = confirmed(true);
            advance(path, 0, 600, cadence, true, 50);
            assertTrue(path.position().getX() < path.confirmed().getX());
            assertTrue(path.position().getX() > tile(11, 10).getX());
            advance(path, 600, 800, cadence, false, 50);
            assertTrue(path.finished());
        }
    }

    @Test
    public void rebaseAndRepeatedPreparationKeepOneClockAndExactFraction()
    {
        MovementPath base = confirmed(true), shifted = confirmed(true);
        base.advance(100_000_000L, false, 25);
        shifted.advance(100_000_000L, false, 25);
        LocalPoint before = shifted.position();
        shifted.advance(100_000_000L, false, 50);
        assertEquals(before, shifted.position());
        shifted.rebase(-128, -256, 100_000_000L);
        base.advance(200_000_000L, false, 25);
        shifted.advance(200_000_000L, false, 25);
        assertEquals(base.position().getX() - 128, shifted.position().getX());
        assertEquals(base.position().getY() - 256, shifted.position().getY());
    }

    @Test
    public void controllerAppliesLiveToggleAndStrengthWithoutResettingPosition()
    {
        Fixture f = new Fixture(1344, 1344, 7000, 7000);
        try
        {
            f.starts = false;
            f.catchUp = true; f.catchUpPercent = 10;
            f.authority = tile(12, 10);
            f.frame(100);
            assertEquals(1388, f.controller.position().getX());
            f.catchUp = false;
            f.frame(200);
            assertEquals(1428, f.controller.position().getX());
            f.catchUp = true; f.catchUpPercent = 25;
            f.frame(300);
            assertEquals(1478, f.controller.position().getX());
            f.catchUpPercent = 0;
            f.frame(400);
            assertEquals(1518, f.controller.position().getX());
        }
        finally { f.controller.close(); }
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void defaultsOfferAnEnabledModestBoost()
    {
        ResponsiveMovementConfig config = new ResponsiveMovementConfig() {};
        assertTrue(config.catchUp());
        assertEquals(10, config.catchUpPercent());
    }

    @Test
    public void partiallyConfirmedKnightDoesNotAccelerateAnUnconfirmedChordEndpoint()
    {
        MovementPath base = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(12, 11),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        MovementPath boosted = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(12, 11),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
        assertTrue(base.accept(tile(11, 10), false));
        assertTrue(boosted.accept(tile(11, 10), false));
        base.advance(100_000_000L);
        boosted.advance(100_000_000L, false, 50);
        assertEquals(base.position(), boosted.position());
        assertTrue(base.traceSnapshot().same(boosted.traceSnapshot()));
    }

    @Test
    public void catchUpRetainsCollisionCheckedCornersAndRejectsAClosingEdge()
    {
        boolean[] open = {true};
        LocalPoint origin = tile(10, 10), middle = tile(11, 10), goal = tile(12, 11);
        MovementPath path = MovementPath.idle(origin, 0, 1, (a, b) -> open[0] &&
            !(a.equals(origin) && b.equals(tile(11, 11))));
        assertTrue(path.accept(goal, true));
        path.advance(100_000_000L, false, 50);
        assertEquals(origin.getY(), path.position().getY());
        assertTrue(path.position().getX() < middle.getX());
        assertTrue(path.clear());
        open[0] = false;
        assertFalse("boost cannot make blocked geometry legal", path.clear());
    }

    @Test
    public void slowMeleePursuitKeepsItsExistingPaceEvenWithConfirmedDebt()
    {
        Fixture f = new Fixture(tile(14, 10).getX(), tile(14, 10).getY(), tile(16, 10).getX(), tile(16, 10).getY());
        try
        {
            NpcApproach npc = NpcApproach.capture(f.client, f.view,
                f.event(net.runelite.api.MenuAction.NPC_SECOND_OPTION, "Attack"), null);
            MovementPath base = MovementPath.idle(f.start, 0, 1, OPEN);
            MovementPath boosted = MovementPath.idle(f.start, 0, 1, OPEN);
            base.armCombat(0); boosted.armCombat(0);
            base = base.followCombat(npc, true, 0, true);
            boosted = boosted.followCombat(npc, true, 0, true);
            assertNotNull(base); assertNotNull(boosted);
            assertTrue(base.accept(tile(15, 10), true));
            assertTrue(boosted.accept(tile(15, 10), true));
            for (int ms = 20; ms <= 600; ms += 20)
            {
                base.advance(ms * 1_000_000L);
                boosted.advance(ms * 1_000_000L, false, 50);
                assertEquals(base.position(), boosted.position());
                assertTrue(boosted.combatTrailing());
            }
        }
        finally { f.controller.close(); }
    }

    @Test
    public void aConfirmedLocalCurveUsesTheSameBoostedArcBudgetAcrossCadences()
    {
        LocalPoint expected = null;
        for (int cadence : new int[] {8, 20, 33})
        {
            MovementPath path = MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(12, 11),
                true, 0, 1, MovementPath.freshDeadline(0), OPEN);
            advance(path, 0, 200, 20, false, 0);
            path = path.retargetWalk(tile(12, 9), true, 200_000_000L);
            assertNotNull(path);
            path.advance(220_000_000L);
            assertTrue("exercise an active local curve", path.traceSnapshot().fields(0).matches(".* joins=\\[[0-9].*"));
            assertTrue(path.accept(tile(12, 9), true));
            advance(path, 220, 280, cadence, false, 25);
            if (expected == null) { expected = path.position(); }
            assertEquals(expected, path.position());
            assertTrue(path.clear());
            advance(path, 280, 1000, cadence, false, 25);
            assertTrue(path.finished());
            assertEquals(tile(12, 9), path.position());
        }
    }

    @Test
    public void catchUpStrengthScalesFromTheConfiguredWalkOrRunRate()
    {
        for (double speed : new double[] {0.1, 0.5, 1, 1.5, 2})
        {
            MovementPath path = MovementPath.idle(tile(10, 10), 0, speed, OPEN);
            assertTrue(path.accept(tile(12, 10), true));
            path.advance(100_000_000L, false, 10);
            assertEquals(Math.round(40 * speed * 1.1), path.position().getX() - tile(10, 10).getX());
            LocalPoint before = path.position();
            path.speed(0.5);
            assertEquals(before, path.position());
            path.advance(200_000_000L, false, 10);
            assertEquals(22, path.position().getX() - before.getX());
        }
    }

    @Test
    public void repeatedNativeRunPairsDoNotAccumulateTheOldTrueTileLag()
    {
        MovementPath base = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        MovementPath boosted = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        for (int ms = 0; ms <= 12_000; ms += 20)
        {
            if (ms % 600 == 0)
            {
                LocalPoint endpoint = tile(12 + ms / 300, 10);
                assertTrue(base.accept(endpoint, true));
                assertTrue(boosted.accept(endpoint, true));
            }
            base.advance(ms * 1_000_000L, true);
            boosted.advance(ms * 1_000_000L, true, 10);
            assertTrue(boosted.position().getX() <= boosted.confirmed().getX());
        }
        int oldLag = base.confirmed().getX() - base.position().getX();
        int newLag = boosted.confirmed().getX() - boosted.position().getX();
        assertTrue("the old rate accumulated more than four tiles of debt", oldLag > 512);
        assertTrue("a modest boost contains lag within a run pair plus the retained 100-ms reserve: " + newLag,
            newLag <= 256 + 40);
        assertTrue(newLag < oldLag);
    }

    private static MovementPath confirmed(boolean run)
    {
        MovementPath path = MovementPath.idle(tile(10, 10), 0, 1, OPEN);
        assertTrue(path.accept(tile(run ? 12 : 11, 10), run));
        return path;
    }

    private static MovementPath preview()
    {
        return MovementPath.anticipate(tile(10, 10), tile(10, 10), tile(12, 10),
            true, 0, 1, MovementPath.freshDeadline(0), OPEN);
    }

    private static void advance(MovementPath path, int from, int to, int cadence, boolean continuingRun, int percent)
    {
        for (int ms = from + cadence; ms < to; ms += cadence) { path.advance(ms * 1_000_000L, continuingRun, percent); }
        path.advance(to * 1_000_000L, continuingRun, percent);
    }
}
