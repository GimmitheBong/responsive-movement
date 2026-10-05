package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Boundaries for the captured stale-tick and adjacent-side continuity fixes. */
public class NpcContinuityGuardTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static NpcApproach capture(Fixture f, String option)
    {
        return NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, option), null);
    }

    @Test
    public void diagonalStartsChooseACheckedCardinalSideInEveryQuadrant()
    {
        LocalPoint npc = p(20, 20);
        for (int dx : new int[] {-1, 1})
        {
            for (int dy : new int[] {-1, 1})
            {
                LocalPoint start = p(20 + dx, 20 + dy), goal = p(20, 20 + dy);
                assertEquals(goal, NpcApproach.adjacentGoal(start, npc, npc, OPEN));
                assertNull(NpcApproach.adjacentGoal(start, npc, npc, (a, b) -> false));
                assertNull(NpcApproach.adjacentGoal(start, npc, npc, (a, b) -> b.getX() < a.getX()));
            }
        }
        assertEquals("on-footprint tie follows cardinal search order", p(19, 20), NpcApproach.adjacentGoal(npc, npc, npc, OPEN));
        assertNull(NpcApproach.adjacentGoal(p(19, 20), npc, npc, OPEN));
        assertNull(NpcApproach.adjacentGoal(new LocalPoint(npc.getX(), npc.getY(), 1), npc, npc, OPEN));
    }

    @Test
    public void ordinaryOneStepInsideTheRingStillUsesCollisionAndTheOriginalTimeout()
    {
        LocalPoint start = p(21, 19), npc = p(20, 20), goal = p(20, 19);
        MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN, false, npc, npc, 1);
        assertNotNull(path);
        for (int ms = 20; ms <= 600; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(goal, path.position());
        assertEquals(start, path.confirmed());
        path.advance(901_000_000L);
        assertEquals("recovery", path.phase());
        for (int ms = 921; ms <= 1801; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(start, path.position());
        assertNull(MovementPath.anticipate(start, start, goal, true, 0, 1.2,
            MovementPath.freshDeadline(0), (a, b) -> false, false, npc, npc, 1));
        assertNull("Bank/Exchange reservation is not relaxed", MovementPath.anticipate(start, start, goal, true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN, false, npc, npc, 2));
    }

    @Test
    public void distantLateWalkFlagsAreNotOrdinaryNpcApproachEvidence()
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(15, 12).getX(), p(15, 12).getY());
        NpcApproach ordinary = capture(f, "Trade");
        assertFalse(ordinary.newlyPublished(p(10, 6)));
        assertTrue(ordinary.newlyPublished(p(14, 12)));
        assertTrue(capture(f, "Bank").newlyPublished(p(10, 6)));
        assertFalse("adjacent melee also rejects an unrelated old Walk flag", capture(f, "Attack").newlyPublished(p(10, 6)));
        f.ranged("Magic shortbow", 861, 1);
        assertTrue("ranged approach policy remains native/range based", capture(f, "Attack").newlyPublished(p(10, 6)));
        f.controller.close();
    }

    @Test
    public void aNewNpcClickBesideAuthorityRetiresTheOldUnconfirmedWalkImmediately()
    {
        Fixture f = new Fixture(p(14, 12).getX(), p(14, 12).getY(), p(15, 12).getX(), p(15, 12).getY());
        MovementPath path = MovementPath.anticipate(f.start, f.start, p(20, 12), true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN);
        path.advance(200_000_000L);
        LocalPoint before = path.position();
        long hard = path.predictionDeadlineNanos();
        path.replacement(true);
        MovementPath next = path.retargetNpcApproach(capture(f, "Talk-to"), null, true, 200_000_000L);
        assertSame(path, next);
        assertEquals(before, next.position());
        assertEquals(hard, next.predictionDeadlineNanos());
        next.advance(220_000_000L);
        assertTrue("do not keep following the superseded Walk", next.position().getX() < before.getX());
        for (int ms = 240; ms <= 1000; ms += 20) { next.advance(ms * 1_000_000L); }
        assertEquals(f.start, next.position());
        f.controller.close();
    }

    @Test
    public void oppositeAndPerpendicularOldTicksUpdateAuthorityWithoutRenewingNpcPrediction()
    {
        for (LocalPoint target : new LocalPoint[] {p(15, 10), p(15, 12)})
        {
            Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), target.getX(), target.getY());
            MovementPath path = MovementPath.anticipate(f.start, f.start, p(10, 4), true, 0, 1.2,
                MovementPath.freshDeadline(0), OPEN);
            path.advance(100_000_000L); path.replacement(true);
            MovementPath next = path.retargetNpcApproach(capture(f, "Talk-to"), null, true, 100_000_000L);
            assertNotNull(next);
            LocalPoint fraction = next.position();
            long hard = next.predictionDeadlineNanos();
            assertTrue(next.accept(p(10, 8), true));
            assertEquals("preview", next.phase());
            assertEquals(p(10, 8), next.confirmed());
            assertEquals(fraction, next.position());
            assertEquals(hard, next.predictionDeadlineNanos());
            next.advance(1001_000_000L);
            assertEquals("response timeout is not rearmed", "recovery", next.phase());
            for (int ms = 1021; ms <= 3501; ms += 20) { next.advance(ms * 1_000_000L); }
            assertEquals(p(10, 8), next.position());
            f.controller.close();
        }
    }

    @Test
    public void newForwardNpcAuthorityCanReanchorOnlyWithACheckedConnector()
    {
        for (boolean blocked : new boolean[] {false, true})
        {
            Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(15, 12).getX(), p(15, 12).getY());
            boolean[] clear = {true};
            BiPredicate<LocalPoint, LocalPoint> collision = (a, b) -> clear[0];
            MovementPath path = MovementPath.anticipate(f.start, f.start, p(14, 12), true, 0, 1.2,
                MovementPath.freshDeadline(0), collision, false, p(15, 12), p(15, 12), 1);
            path.advance(200_000_000L);
            LocalPoint fraction = path.position();
            clear[0] = !blocked;
            MovementPath next = path.alignNpcAuthority(p(12, 11), capture(f, "Talk-to"), null, true, 200_000_000L);
            if (blocked) { assertNull(next); }
            else
            {
                assertNotNull(next);
                assertEquals(fraction, next.position());
                assertEquals(p(12, 11), next.confirmed());
                assertTrue(next.predictionDeadlineNanos() > path.predictionDeadlineNanos());
                assertTrue(next.clear());
                next.advance(220_000_000L);
                assertTrue(MovementPath.distance(fraction, next.position()) <= 10);
            }
            f.controller.close();
        }
    }
}
