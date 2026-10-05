package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.Arrays;
import java.util.function.BiPredicate;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static com.responsivemovement.GeApproachReplayTest.*;
import static org.junit.Assert.*;

public class CounterRunTest
{
    @Test
    public void counterGeometryWorksTranslatedRotatedAndMirroredWithoutNpcIds() throws Exception
    {
        Fixture source = fixture(tile(63, 52));
        for (int rotation = 0; rotation < 4; ++rotation)
        {
            for (boolean mirror : new boolean[] {false, true})
            {
                LocalPoint start = transform(tile(63, 52), rotation, mirror), npc = tile(52, 52);
                Fixture f = new Fixture(start.getX(), start.getY(), npc.getX(), npc.getY());
                f.npcId = 10000 + rotation; // Geometry and option, not an NPC-definition table.
                for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
                for (int x = 50; x < 72; ++x)
                {
                    for (int y = 48; y < 64; ++y)
                    {
                        LocalPoint at = transform(tile(x, y), rotation, mirror);
                        f.flags[at.getSceneX()][at.getSceneY()] = rotateFlags(source.flags[x][y], rotation, mirror);
                    }
                }
                NpcApproach approach = capture(f, "Exchange");
                assertTrue(approach.counterBank());
                NpcApproach.CounterRun plan = approach.counterRun(start, collision(f));
                assertNotNull(plan);
                assertEquals(transform(tile(58, 57), rotation, mirror), plan.nativeGoal);
                assertEquals(transform(tile(58, 56), rotation, mirror), plan.steps.get(plan.steps.size() - 1));
                assertEquals(6, plan.steps.size());
                f.controller.close();
            }
        }
        source.controller.close();
    }

    @Test
    public void unsupportedOptionsFootprintsAndAmbiguousSidesKeepTheirExistingPolicy() throws Exception
    {
        Fixture f = fixture(tile(63, 52));
        assertTrue(capture(f, "Bank").counterBank());
        assertFalse(capture(f, "Talk-to").counterBank());
        assertFalse(capture(f, "Attack").counterBank());
        f.area = new WorldArea(3259, 3257, 2, 2, 0);
        assertFalse(capture(f, "Bank").counterBank());
        f.area = new WorldArea(3259, 3257, 1, 1, 0);
        f.flags[59][56] = 0; // Second accessible side: no evidence for the one-sided counter policy.
        assertFalse(capture(f, "Bank").counterBank());
        f.flags[59][56] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        f.flags[59][57] |= CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL;
        assertFalse(capture(f, "Bank").counterBank());
        f.controller.close();
    }

    @Test
    public void alreadyInReachAndUnavailableOrOneWayRoutesCannotCreateAStart() throws Exception
    {
        Fixture f = fixture(tile(60, 55));
        NpcApproach approach = capture(f, "Bank");
        assertNull(approach.counterRun(f.start, collision(f)));
        f.click("Bank"); f.frame(0); f.frame(400);
        assertEquals(f.start, f.controller.position());
        assertNull(approach.counterRun(tile(63, 52), (a, b) -> false));
        assertNull(approach.counterRun(tile(63, 52), (a, b) -> b.getX() < a.getX()));
        f.controller.close();
    }

    @Test
    public void startGatesStillApplyAtTheCounter() throws Exception
    {
        for (int gate = 0; gate < 4; ++gate)
        {
            Fixture f = fixture(tile(63, 52));
            if (gate == 0) { f.starts = false; }
            if (gate == 1) { f.control = true; }
            if (gate == 2) { f.animation = 123; }
            if (gate == 3) { f.spot = true; }
            f.click("Bank"); f.frame(0); f.frame(200);
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void timeoutRetainsTheFiniteBudgetAndRecoversOnCheckedEdges() throws Exception
    {
        Fixture f = fixture(tile(63, 52));
        NpcApproach approach = capture(f, "Bank");
        MovementPath path = start(f, approach);
        long hard = path.predictionDeadlineNanos();
        for (int ms = 20; ms <= 4000; ms += 20)
        {
            path.advance(ms * 1_000_000L);
            assertTrue(path.clear());
            assertEquals(hard, path.predictionDeadlineNanos());
            assertTrue(MovementPath.distance(path.position(), f.start) <= 5 * 128);
        }
        assertEquals(f.start, path.position());
        assertNull(path.counterNativeGoal());
        assertTrue(path.finished());
        f.controller.close();
    }

    @Test
    public void nativeFlagWithdrawalCannotReturnToTheOldOriginBeforeFirstAuthority() throws Exception
    {
        Fixture f = fixture(tile(62, 53));
        f.click("Bank"); f.frame(0);
        f.destination = tile(58, 57); f.frame(20);
        for (int ms = 40; ms <= 480; ms += 20)
        {
            if (ms == 200) { f.destination = null; }
            LocalPoint before = f.controller.position();
            f.frame(ms);
            assertTrue(f.controller.position().getX() < before.getX());
            assertEquals(f.start.getY(), f.controller.position().getY());
        }
        f.authority = tile(60, 53); f.frame(500);
        f.authority = tile(58, 55);
        for (int ms = 520; ms <= 1800; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void changedTargetRunActionOrNativeGoalRetiresTheForecast() throws Exception
    {
        for (int change = 0; change < 7; ++change)
        {
            Fixture f = fixture(tile(63, 52));
            f.click("Bank"); f.frame(0); f.frame(100);
            assertNotEquals(f.start, f.controller.position());
            if (change == 0) { f.run = false; }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.npcId++; }
            if (change == 3) { f.animation = 123; }
            if (change == 4) { f.destination = tile(63, 50); }
            if (change == 5) { f.area = new WorldArea(3260, 3257, 1, 1, 0); }
            if (change == 6) { f.spot = true; }
            for (int ms = 120; ms <= 2200; ms += 20) { f.frame(ms); }
            assertEquals("change " + change, f.start, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void anUnexpectedSingleStepUsesAuthorityAndDoesNotRearmTheCounterPlan() throws Exception
    {
        Fixture f = fixture(tile(63, 52));
        f.click("Bank"); f.frame(0); f.frame(100);
        f.authority = tile(62, 52);
        for (int ms = 120; ms <= 2400; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void uncheckedKnightAlternativeKeepsTheLegalCorner() throws Exception
    {
        Fixture f = fixture(tile(61, 53));
        NpcApproach approach = capture(f, "Bank");
        BiPredicate<LocalPoint, LocalPoint> checked = collision(f);
        BiPredicate<LocalPoint, LocalPoint> cornerOnly = (a, b) -> checked.test(a, b) &&
            !(a.equals(f.start) && b.equals(tile(60, 54))) && !(b.equals(f.start) && a.equals(tile(60, 54)));
        NpcApproach.CounterRun plan = approach.counterRun(f.start, cornerOnly);
        assertNotNull(plan);
        MovementPath path = MovementPath.anticipateCounterRun(f.start, f.start, plan, 0, 1.1,
            MovementPath.freshDeadline(0), cornerOnly, approach.min, approach.max);
        assertNotNull(path);
        path.advance(100_000_000L);
        assertEquals(f.start.getY(), path.position().getY());
        assertEquals(f.start.getX() - 44, path.position().getX());
        assertTrue(path.clear());
        f.controller.close();
    }

    @Test
    public void queuedCounterPredictionPreservesAnUnfinishedConfirmedPrefix() throws Exception
    {
        Fixture f = fixture(tile(63, 50));
        MovementPath path = MovementPath.idle(f.start, 0, 1.1, collision(f));
        assertTrue(path.accept(tile(63, 52), true));
        path.advance(100_000_000L);
        LocalPoint fraction = path.position();
        NpcApproach approach = capture(f, "Bank");
        assertTrue(path.anticipateCounterContinuation(approach.counterRun(path.confirmed(), collision(f)),
            100_000_000L, approach.min, approach.max));
        assertEquals(fraction, path.position());
        path.advance(200_000_000L);
        assertEquals(f.start.getX(), path.position().getX());
        assertEquals(f.start.getY() + 88, path.position().getY());
        path.cancel();
        for (int ms = 220; ms <= 1000; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(tile(63, 52), path.position());
        f.controller.close();
    }

    private static NpcApproach capture(Fixture f, String option)
    {
        return NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, option), null);
    }

    private static MovementPath start(Fixture f, NpcApproach approach)
    {
        return MovementPath.anticipateCounterRun(f.start, f.start, approach.counterRun(f.start, collision(f)),
            0, 1.1, MovementPath.freshDeadline(0), collision(f), approach.min, approach.max);
    }

    private static LocalPoint transform(LocalPoint point, int rotation, boolean mirror)
    {
        int[] delta = vector(point.getSceneX() - 59, point.getSceneY() - 57, rotation, mirror);
        return tile(52 + delta[0], 52 + delta[1]);
    }

    private static int[] vector(int x, int y, int rotation, boolean mirror)
    {
        if (mirror) { x = -x; }
        for (int i = 0; i < rotation; ++i) { int previous = x; x = -y; y = previous; }
        return new int[] {x, y};
    }

    private static int rotateFlags(int flags, int rotation, boolean mirror)
    {
        int[][] directions = {{-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}};
        int result = flags & ~(255 | 255 << 9);
        for (int bit = 0; bit < 8; ++bit)
        {
            int[] target = vector(directions[bit][0], directions[bit][1], rotation, mirror);
            for (int to = 0; to < 8; ++to)
            {
                if (!Arrays.equals(target, directions[to])) { continue; }
                if ((flags & 1 << bit) != 0) { result |= 1 << to; }
                if ((flags & 1 << (bit + 9)) != 0) { result |= 1 << (to + 9); }
            }
        }
        return result;
    }
}
