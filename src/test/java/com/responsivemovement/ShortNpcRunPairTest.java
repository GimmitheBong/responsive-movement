package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Three-tile Bank/Exchange starts; only explicit synthetic collision fixtures are used. */
public class ShortNpcRunPairTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void cardinalKnightAndTwoTileOffsetStartsHaveCheckedNormalSpeedGoalsInAllDirections()
    {
        for (int[] axis : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
        {
            for (int minor : new int[] {-2, -1, 0, 1, 2})
            {
                LocalPoint start = p(20, 20);
                LocalPoint npc = p(20 + axis[0] * 3 + axis[1] * minor, 20 + axis[1] * 3 + axis[0] * minor);
                LocalPoint goal = NpcApproach.shortRunGoal(start, npc, npc, OPEN);
                int side = Math.abs(minor) == 1 ? minor : 0;
                assertEquals(p(20 + axis[0] * 2 + axis[1] * side, 20 + axis[1] * 2 + axis[0] * side), goal);
                MovementPath path = pair(start, npc, goal, 1, OPEN);
                assertNotNull(path);
                assertEquals(goal, path.npcArrivalGoal());
                for (int ms = 20; ms <= 620; ms += 20)
                {
                    LocalPoint before = path.position();
                    path.advance(ms * 1_000_000L);
                    if (ms <= 640) { assertEquals("ordinary run pace, no staging easing", 8, MovementPath.distance(before, path.position())); }
                }
                path.advance(640_000_000L);
                assertEquals(goal, path.position());
                assertEquals("still awaiting real confirmation", "preview", path.phase());
                assertTrue(path.accept(goal, true));
                assertTrue(path.finished());
            }
        }
    }

    @Test
    public void counterBlocksTheAdjacentSideAndSelectsTheCheckedStraightPair()
    {
        LocalPoint start = p(20, 20), npc = p(21, 23), blocked = p(21, 22);
        BiPredicate<LocalPoint, LocalPoint> counter = (a, b) -> !a.equals(blocked) && !b.equals(blocked);
        LocalPoint goal = NpcApproach.shortRunGoal(start, npc, npc, counter);
        assertEquals(p(20, 22), goal);
        MovementPath path = pair(start, npc, goal, 1, counter);
        assertNotNull(path);
        for (int ms = 20; ms <= 640; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(goal, path.position());
        assertTrue(path.clear());
    }

    @Test
    public void oneClearKnightOrderingKeepsItsLogicalCornerInsteadOfAnUncheckedChord()
    {
        LocalPoint start = p(20, 20), npc = p(23, 21), goal = p(22, 21);
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) ->
            !(a.equals(start) && b.equals(p(21, 21)) || b.equals(start) && a.equals(p(21, 21)));
        assertEquals(goal, NpcApproach.shortRunGoal(start, npc, npc, collision));
        MovementPath path = pair(start, npc, goal, 1, collision);
        assertNotNull(path);
        assertEquals(2, path.queuedLegs());
        path.advance(100_000_000L);
        assertEquals(start.getY(), path.position().getY());
        assertTrue(path.clear());
    }

    @Test
    public void blockedOrIrreversibleStartsAndUnsupportedGeometryCannotUseTheException()
    {
        LocalPoint start = p(20, 20), npc = p(23, 20);
        assertNull(NpcApproach.shortRunGoal(start, npc, npc, (a, b) -> false));
        assertNull(NpcApproach.shortRunGoal(start, npc, npc, (a, b) -> b.getX() > a.getX()));
        assertNull(NpcApproach.shortRunGoal(start, p(22, 20), p(22, 20), OPEN));
        assertNull(NpcApproach.shortRunGoal(start, p(24, 20), p(24, 20), OPEN));
        assertNull(NpcApproach.shortRunGoal(start, p(23, 23), p(23, 23), OPEN));
        assertNull(NpcApproach.shortRunGoal(start, npc, p(24, 20), OPEN));
        assertNull(MovementPath.anticipateNpcRunPair(start, start, p(22, 20), false, 0, 1,
            MovementPath.freshDeadline(0), OPEN, npc, npc));
        assertNull(pair(start, npc, p(22, 20), 0.1, OPEN));
    }

    @Test
    public void completedShortPairStillExpiresOrRetiresOnReplacementWithoutRenewingItsBudget()
    {
        for (boolean replace : new boolean[] {false, true})
        {
            MovementPath path = pair(p(20, 20), p(23, 20), p(22, 20), 1, OPEN);
            long deadline = path.predictionDeadlineNanos();
            for (int ms = 20; ms <= 680; ms += 20) { path.advance(ms * 1_000_000L); }
            assertEquals(p(22, 20), path.position());
            assertEquals(deadline, path.predictionDeadlineNanos());
            if (replace) { path.cancel(); }
            for (int ms = 700; ms <= 2200; ms += 20) { path.advance(ms * 1_000_000L); }
            assertEquals(p(20, 20), path.position());
            assertTrue(path.finished());
            assertNull(path.npcArrivalGoal());
        }
    }

    @Test
    public void bothFrameCadenceAndMatchingAuthorityKeepTheSameContinuousRun()
    {
        MovementPath fine = pair(p(20, 20), p(23, 21), p(22, 21), 1, OPEN);
        MovementPath coarse = pair(p(20, 20), p(23, 21), p(22, 21), 1, OPEN);
        for (int ms = 20; ms <= 400; ms += 20) { fine.advance(ms * 1_000_000L); }
        for (int ms = 100; ms <= 400; ms += 100) { coarse.advance(ms * 1_000_000L); }
        assertEquals(fine.position(), coarse.position());
        assertTrue(fine.accept(p(22, 21), true));
        assertTrue(coarse.accept(p(22, 21), true));
        for (int ms = 420; ms <= 800; ms += 20) { fine.advance(ms * 1_000_000L); }
        for (int ms = 500; ms <= 800; ms += 100) { coarse.advance(ms * 1_000_000L); }
        assertEquals(p(22, 21), fine.position());
        assertEquals(fine.position(), coarse.position());
    }

    private static MovementPath pair(LocalPoint start, LocalPoint npc, LocalPoint goal, double speed,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return MovementPath.anticipateNpcRunPair(start, start, goal, true, 0, speed,
            MovementPath.freshDeadline(0), collision, npc, npc);
    }
}
