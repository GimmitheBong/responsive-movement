package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Narrow five-tile cardinal NPC run completion; collision fixtures are synthetic. */
public class BankArrivalContinuityTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void mirroredAndRotatedRunsContinueAtNormalPaceThroughTheStagingTile()
    {
        for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        {
            LocalPoint start = p(20, 20), npc = p(20 + direction[0] * 5, 20 + direction[1] * 5);
            LocalPoint goal = p(20 + direction[0] * 4, 20 + direction[1] * 4);
            LocalPoint first = p(20 + direction[0] * 2, 20 + direction[1] * 2);
            MovementPath path = preview(start, npc, goal);
            for (int ms = 20; ms <= 300; ms += 20) { path.advance(ms * 1_000_000L); }
            assertTrue(path.accept(first, true));
            long deadline = path.predictionDeadlineNanos();
            LocalPoint before = path.position();
            assertTrue(path.releaseNpcArrival(goal, start, true, 300_000_000L));
            assertEquals(before, path.position());
            assertEquals(deadline, path.predictionDeadlineNanos());
            path.advance(320_000_000L);
            assertEquals(8, MovementPath.distance(before, path.position()));
            for (int ms = 340; ms <= 1000; ms += 20)
            {
                before = path.position();
                path.advance(ms * 1_000_000L);
                assertEquals("no easing or idle at the staged tile", 8, MovementPath.distance(before, path.position()));
            }
            assertTrue(path.moving());
            assertEquals(400, MovementPath.distance(start, path.position()));
            assertEquals(goal, path.npcArrivalGoal());
            before = path.position();
            assertTrue(path.accept(goal, true));
            path.advance(1020_000_000L);
            assertEquals("confirmation resumes normal run rate", 8, MovementPath.distance(before, path.position()));
            for (int ms = 1040; ms <= 1800; ms += 20) { path.advance(ms * 1_000_000L); }
            assertEquals(goal, path.position());
            assertTrue(path.finished());
        }
    }

    @Test
    public void serverDisagreementCancellationAndTimeoutStillReconcileToAuthority()
    {
        for (int ending = 0; ending < 3; ++ending)
        {
            MovementPath path = preview(p(10, 10), p(15, 10), p(14, 10));
            path.advance(100_000_000L);
            assertTrue(path.accept(p(12, 10), true));
            assertTrue(path.releaseNpcArrival(p(14, 10), p(10, 10), true, 100_000_000L));
            for (int ms = 120; ms <= 800; ms += 20) { path.advance(ms * 1_000_000L); }
            if (ending == 0) { assertTrue(path.accept(p(13, 10), true)); }
            if (ending == 1) { path.cancel(); }
            // ending 2 uses the original response timeout after matching progress.
            for (int ms = 820; ms <= 2600; ms += 20) { path.advance(ms * 1_000_000L); }
            assertEquals(ending == 0 ? p(13, 10) : p(12, 10), path.position());
            assertTrue(path.finished());
        }
    }

    @Test
    public void activationRejectsDifferentDistancesWalkingOffsetsAndOneTileConfirmations()
    {
        MovementPath longer = preview(p(10, 10), p(16, 10), p(15, 10));
        assertTrue(longer.accept(p(12, 10), true));
        assertFalse(longer.releaseNpcArrival(p(15, 10), p(10, 10), true, 0));

        MovementPath path = preview(p(10, 10), p(15, 10), p(14, 10));
        assertTrue(path.accept(p(12, 10), true));
        assertFalse(path.releaseNpcArrival(p(14, 10), p(10, 10), false, 0));
        assertFalse(path.releaseNpcArrival(null, p(10, 10), true, 0));
        assertFalse(path.releaseNpcArrival(p(13, 10), p(10, 10), true, 0));

        MovementPath offset = preview(p(10, 10), p(15, 11), p(14, 10));
        assertTrue(offset.accept(p(12, 10), true));
        assertFalse(offset.releaseNpcArrival(p(14, 10), p(10, 10), true, 0));

        MovementPath walked = preview(p(10, 10), p(15, 10), p(14, 10));
        assertTrue(walked.accept(p(11, 10), false));
        assertTrue(walked.accept(p(12, 10), false));
        assertFalse(walked.releaseNpcArrival(p(14, 10), p(11, 10), true, 0));
    }

    @Test
    public void finalRunPairUsesOneFrameIndependentBudgetAcrossTheConfirmedPrefix()
    {
        MovementPath fine = preview(p(10, 10), p(15, 10), p(14, 10));
        MovementPath coarse = preview(p(10, 10), p(15, 10), p(14, 10));
        for (int ms = 20; ms <= 400; ms += 20) { fine.advance(ms * 1_000_000L); }
        for (int ms = 100; ms <= 400; ms += 100) { coarse.advance(ms * 1_000_000L); }
        for (MovementPath path : new MovementPath[] {fine, coarse})
        {
            assertTrue(path.accept(p(12, 10), true));
            assertTrue(path.releaseNpcArrival(p(14, 10), p(10, 10), true, 400_000_000L));
        }
        for (int ms = 420; ms <= 1000; ms += 20) { fine.advance(ms * 1_000_000L); }
        for (int ms = 500; ms <= 1000; ms += 100) { coarse.advance(ms * 1_000_000L); }
        assertEquals(fine.position(), coarse.position());
        assertTrue(fine.moving());
        assertTrue(coarse.clear());
    }

    @Test
    public void finalRunPairMustStillBeReversibleAndCollisionClearAtRelease()
    {
        boolean[] clear = {true};
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) -> clear[0] ||
            !(a.equals(p(13, 10)) && b.equals(p(14, 10)) || a.equals(p(14, 10)) && b.equals(p(13, 10)));
        MovementPath path = MovementPath.anticipate(p(10, 10), p(10, 10), p(14, 10), true, 0, 1,
            MovementPath.freshDeadline(0), collision, false, p(15, 10), p(15, 10));
        assertTrue(path.accept(p(12, 10), true));
        clear[0] = false;
        assertTrue("old staging route remains clear", path.clear());
        assertFalse(path.releaseNpcArrival(p(14, 10), p(10, 10), true, 0));
        assertNull(path.npcArrivalGoal());
    }

    @Test
    public void expiredForecastOrInsufficientDistanceBudgetCannotReleaseThePair()
    {
        MovementPath expired = preview(p(10, 10), p(15, 10), p(14, 10));
        expired.advance(300_000_000L);
        assertTrue(expired.accept(p(12, 10), true));
        assertFalse(expired.releaseNpcArrival(p(14, 10), p(10, 10), true, 1201_000_000L));
        MovementPath slow = preview(p(10, 10), p(15, 10), p(14, 10));
        assertTrue(slow.accept(p(12, 10), true));
        slow.speed(0.1);
        assertFalse(slow.releaseNpcArrival(p(14, 10), p(10, 10), true, 0));
    }

    @Test
    public void releasedGoalTranslatesWithThePathAndNoLoadingMovementIsSpent()
    {
        MovementPath path = preview(p(10, 10), p(15, 10), p(14, 10));
        path.advance(100_000_000L);
        assertTrue(path.accept(p(12, 10), true));
        assertTrue(path.releaseNpcArrival(p(14, 10), p(10, 10), true, 100_000_000L));
        LocalPoint before = path.position();
        LocalPoint goal = new LocalPoint(p(14, 10).getX() + 1024, p(14, 10).getY() + 5120, 0);
        assertTrue(path.resumeScene(1024, 5120,
            new LocalPoint(p(12, 10).getX() + 1024, p(12, 10).getY() + 5120, 0), goal, true, 200_000_000L));
        assertEquals(goal, path.npcArrivalGoal());
        path.advance(200_000_000L);
        assertEquals(new LocalPoint(before.getX() + 1024, before.getY() + 5120, 0), path.position());
        assertTrue(path.clear());
    }

    private static MovementPath preview(LocalPoint start, LocalPoint npc, LocalPoint goal)
    {
        MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1,
            MovementPath.freshDeadline(0), OPEN, false, npc, npc);
        assertNotNull(path);
        return path;
    }
}
