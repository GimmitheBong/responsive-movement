package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Recorded endpoints from session 1790595935156; collision fixtures are synthetic. */
public class CapturedRouteStabilityTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void oldTickCannotOverwriteTheNewClickDuringSettling()
    {
        // 21:45:53.744: new east target; .767: old west destination is
        // republished with an old server step, before the 50 ms window ends.
        LocalPoint start = p(6720, 6464), west = p(6336, 6464), east = p(6976, 6464);
        MovementPath path = MovementPath.anticipate(start, start, west, true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 560);
        MovementInput input = new MovementInput();
        input.click(null, nanos(560), 1, 0, 50);
        assertNull(input.destination(east, nanos(580), 1, 0, west));
        assertTrue(path.accept(p(6464, 6464), true));
        assertNull(input.destination(west, nanos(600), 1, 0, west));
        path.advance(nanos(600));
        LocalPoint released = input.destination(west, nanos(620), 1, 0, west);
        assertEquals("the clicked east destination must survive the old server publication", east, released);
        assertTrue(input.takeReplacement());
        MovementPath next = path.retargetWalk(released, true, nanos(620));
        assertNotNull(next);
        int x = next.position().getX();
        next.advance(nanos(620));
        assertTrue(next.position().getX() > x);
    }

    @Test
    public void diagonalRetargetCanUseACheckedSideCornerInsteadOfRetracing()
    {
        // 21:47:12.127: occupied final diagonal 5952,7360 -> 6080,7488,
        // new destination 6080,7232. Old choice runs back to 5952,7360.
        MovementPath path = diagonal(OPEN);
        LocalPoint before = path.position();
        long deadline = path.predictionDeadlineNanos();
        MovementPath next = path.retargetWalk(p(6080, 7232), true, nanos(490));
        assertNotNull(next);
        assertEquals(before, next.position());
        assertEquals(deadline, next.predictionDeadlineNanos());
        next.advance(nanos(510));
        assertTrue("stay on the destination side of the checked square", next.position().getX() > before.getX());
        assertTrue(next.position().getY() < before.getY());
        assertTrue(next.clear());
    }

    @Test
    public void aForwardCardinalClickDoesNotBackstepToExploitANewKnight()
    {
        // 21:48:00.284: westbound at ~5920,7232; target 5824,6976.
        // Going back east just to begin a new knight is unnecessary: the
        // forward corner remains collision-clear and still leads to the target.
        LocalPoint start = p(6080, 7232);
        MovementPath path = MovementPath.anticipate(start, start, p(5696, 7232), true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN);
        advance(path, 0, 330);
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(p(5824, 6976), true, nanos(330));
        assertNotNull(next);
        assertEquals(before, next.position());
        next.advance(nanos(350));
        assertTrue("no eastward backstep to the new chord's start", next.position().getX() < before.getX());
        assertEquals(before.getY(), next.position().getY());
        assertTrue(next.clear());
        advance(next, 350, 700);
        assertTrue(next.position().getY() < before.getY());
    }

    @Test
    public void equalTimeKnightConnectionsChooseLessVisibleRetracing()
    {
        // 21:48:01.888, 21:50:38.406 and 21:51:46.323. Each old
        // candidate goes back through the chord's start instead of its nearer
        // side anchor, even though both have the same max-axis travel length.
        int[][] cases = {
            {6080, 6848, 6208, 7104, 6208, 6720, 450},
            {5952, 6976, 6080, 6720, 6080, 7104, 480},
            {5824, 7104, 5952, 7360, 5952, 6976, 460}};
        for (int[] c : cases)
        {
            MovementPath path = confirmed(p(c[0], c[1]), p(c[2], c[3]), c[6], OPEN);
            LocalPoint before = path.position(), target = p(c[4], c[5]);
            MovementPath next = path.retargetWalk(target, true, nanos(c[6]));
            assertNotNull(next);
            assertEquals(before, next.position());
            next.advance(nanos(c[6] + 20));
            assertTrue("avoid retracing toward old chord origin for " + target,
                next.position().getX() >= before.getX());
            assertTrue(MovementPath.distance(before, next.position()) <= 10);
            assertTrue(next.clear());
        }
    }

    @Test
    public void correctedSideRouteAcceptsProgressAndDoesNotTimeOutJustBeforeArrival()
    {
        // 21:50:38.805 authority reaches 6080,6976; the old detour omits
        // that point, then times out at .39.307, just before final confirmation.
        MovementPath path = confirmed(p(5952, 6976), p(6080, 6720), 480, OPEN);
        path = path.retargetWalk(p(6080, 7104), true, nanos(480));
        assertNotNull(path);
        for (int ms = 490; ms <= 1600; ms += 10)
        {
            if (ms == 880) { assertTrue(path.accept(p(6080, 6976), true)); }
            if (ms == 1480) { assertTrue(path.accept(p(6080, 7104), true)); }
            int previousY = path.position().getY();
            path.advance(nanos(ms));
            assertTrue("no arrival turn-back at " + ms, path.position().getY() >= previousY);
            assertNotEquals("matching progress must be recognized", "recovery", path.phase());
        }
        assertEquals(p(6080, 7104), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void sideCornerRequiresBothDiagonalsAndEveryPerimeterEdgeToBeClear()
    {
        LocalPoint a = p(5952, 7360), b = p(6080, 7488), side = p(6080, 7360), other = p(5952, 7488);
        LocalPoint[][] blockedEdges = {{a, side}, {side, b}, {a, other}, {other, b}, {side, other}};
        for (LocalPoint[] blocked : blockedEdges)
        {
            // Deliberately one-way: reverse-side collision must also prevent the shortcut.
            BiPredicate<LocalPoint, LocalPoint> wall = (from, to) ->
                !(from.equals(blocked[1]) && to.equals(blocked[0]));
            MovementPath path = diagonal(wall);
            LocalPoint before = path.position();
            MovementPath next = path.retargetWalk(p(6080, 7232), true, nanos(490));
            assertNotNull("the original legal connector still exists", next);
            next.advance(nanos(510));
            assertTrue("never take the unproven side connector", next.position().getX() <= before.getX());
            assertTrue(next.clear());
        }
    }

    @Test
    public void aPartlyBlockedNewKnightCannotAbsorbTheOccupiedConnector()
    {
        BiPredicate<LocalPoint, LocalPoint> wall = (a, b) ->
            !(a.equals(p(5952, 7232)) && b.equals(p(5824, 7104)));
        LocalPoint start = p(6080, 7232);
        MovementPath path = MovementPath.anticipate(start, start, p(5696, 7232), true, 0, 1.2,
            MovementPath.freshDeadline(0), wall);
        advance(path, 0, 330);
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(p(5824, 6976), true, nanos(330));
        assertNotNull(next);
        next.advance(nanos(350));
        assertEquals("stay on the occupied edge until a legal corner", before.getY(), next.position().getY());
        assertTrue(next.clear());
    }

    @Test
    public void forwardPreferenceDoesNotHideANecessaryCollisionCheckedBacktrack()
    {
        LocalPoint start = p(6080, 7232), occupiedStart = p(5952, 7232), corner = p(5824, 7232);
        // The occupied edge is reversible, but the forward corner has no exit
        // except back through that edge. The only onward route starts behind us.
        BiPredicate<LocalPoint, LocalPoint> wall = (a, b) ->
            (!a.equals(corner) || b.equals(occupiedStart)) &&
            (!b.equals(corner) || a.equals(occupiedStart));
        MovementPath path = confirmed(start, corner, 330, wall);
        LocalPoint before = path.position();
        MovementPath next = path.retargetWalk(p(5824, 6976), true, nanos(330));
        assertNotNull(next);
        assertEquals(before, next.position());
        next.advance(nanos(350));
        assertTrue("a real blocked corner still requires the checked reverse edge", next.position().getX() > before.getX());
        assertTrue(next.clear());
    }

    @Test
    public void sideConnectorKeepsTimeoutRecoveryAndSceneRebaseContinuous()
    {
        for (boolean rebase : new boolean[] {false, true})
        {
            MovementPath path = diagonal(OPEN);
            long deadline = path.predictionDeadlineNanos();
            path = path.retargetWalk(p(6080, 7232), true, nanos(490));
            assertNotNull(path);
            path.advance(nanos(510));
            LocalPoint before = path.position();
            if (rebase)
            {
                path.rebase(-128, -128, nanos(510));
                assertEquals(p(before.getX() - 128, before.getY() - 128), path.position());
            }
            assertEquals(deadline, path.predictionDeadlineNanos());
            for (int ms = 520; ms <= 4000; ms += 10)
            {
                before = path.position();
                path.advance(nanos(ms));
                assertTrue(MovementPath.distance(before, path.position()) <= 5);
                assertTrue(path.clear());
            }
            assertEquals(rebase ? p(5952, 7360) : p(6080, 7488), path.position());
            assertTrue(path.finished());
        }
    }

    @Test
    public void aSideCorridorThatClosesIsRejectedBeforeMoreTravel()
    {
        boolean[] blocked = {false};
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) -> !blocked[0] ||
            !(a.equals(p(6080, 7360)) && b.equals(p(5952, 7360)));
        MovementPath path = diagonal(collision).retargetWalk(p(6080, 7232), true, nanos(490));
        assertNotNull(path);
        path.advance(nanos(510));
        assertTrue(path.clear());
        LocalPoint before = path.position();
        blocked[0] = true;
        assertFalse(path.clear());
        assertNull(path.retargetWalk(p(5952, 7232), true, nanos(520)));
        assertEquals(before, path.position());
    }

    private static MovementPath diagonal(BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return confirmed(p(5824, 7232), p(6080, 7488), 490, collision);
    }

    private static MovementPath confirmed(LocalPoint start, LocalPoint end, int millis,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        MovementPath path = MovementPath.idle(start, 0, 1.2, collision);
        assertTrue(path.accept(end, true));
        advance(path, 0, millis);
        return path;
    }

    private static long nanos(int millis) { return millis * 1_000_000L; }
    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(nanos(ms)); }
    }
}
