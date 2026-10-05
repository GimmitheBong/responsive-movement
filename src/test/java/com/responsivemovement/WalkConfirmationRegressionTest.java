package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fresh visual routes do not inherit confirmation just because their new goal is the old true tile. */
public class WalkConfirmationRegressionTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;

    @Test public void recorded0041LastReversalDoesNotAppendAnOldTickAsReturnDebt() throws Exception { replay("0041"); }
    @Test public void recorded0046LastReversalDoesNotOscillateAfterInputEnds() throws Exception { replay("0046"); }

    private static void replay(String id) throws Exception
    {
        List<String[]> rows;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            WalkConfirmationRegressionTest.class.getResourceAsStream("yellow-0041-0046.txt"), StandardCharsets.UTF_8)))
        {
            rows = reader.lines().filter(line -> !line.startsWith("#")).map(line -> line.split(" ")).collect(Collectors.toList());
        }
        int index = 0;
        while (!rows.get(index)[0].equals("C") || !rows.get(index)[1].equals(id)) { ++index; }
        String[] click = rows.get(index++);
        List<String[]> map = new ArrayList<>(), events = new ArrayList<>();
        while (index < rows.size() && !rows.get(index)[0].equals("C"))
        {
            String[] row = rows.get(index++);
            if (row[0].equals("M")) { map.add(row); }
            else { events.add(row); }
        }
        long lastClick = events.stream().filter(e -> e[0].equals("K")).mapToLong(e -> Long.parseLong(e[1])).max().getAsLong();
        LocalPoint goal = point(events.get(events.size() - 1), 2);
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = new Fixture(Integer.parseInt(click[4]), Integer.parseInt(click[5]), 7000, 7000);
            f.speed = 1.1; f.smoothing = 0;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] column : map)
            {
                String[] values = column[2].split(",");
                for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(column[1])][50 + y] = Integer.parseUnsignedInt(values[y], 16); }
            }
            f.controller.walkClick();
            TreeSet<Long> times = new TreeSet<>();
            long end = Long.parseLong(events.get(events.size() - 1)[1]);
            for (long t = 0; t <= end; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            LocalPoint previous = f.start;
            long previousTime = 0;
            int next = 0;
            boolean arrived = false;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("K")) { f.controller.walkClick(); }
                    else
                    {
                        f.authority = point(event, 2); f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6);
                    }
                }
                int angle = f.controller.orientation(); f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("one movement budget", MovementPath.distance(previous, actual) <= Math.ceil((t - previousTime) * 0.00044) + 1);
                assertTrue("one capped turn budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                if (t > lastClick + 70_000)
                {
                    int direction = id.equals("0041") ? 1 : -1;
                    assertTrue("no old-tick detour after final click at " + t, direction * (actual.getX() - previous.getX()) >= 0);
                    if (arrived) { assertEquals("no return debt after reaching latest goal", goal, actual); }
                    arrived |= actual.equals(goal);
                }
                previous = actual; previousTime = t;
            }
            assertTrue(arrived); assertEquals(goal, f.controller.position());
            f.controller.close();
        }
    }

    private static MovementPath routeToAlreadyAuthoritativeGoal(int dx, int dy)
    {
        LocalPoint origin = p(6592, 6720), goal = p(6592 + dx, 6720 + dy);
        MovementPath path = MovementPath.idle(origin, 0, 1.1, OPEN);
        assertTrue(path.accept(goal, true)); path.advance(100_000_000L);
        MovementPath next = path.retargetWalk(goal, true, 100_000_000L);
        assertNotNull(next); return next;
    }

    @Test
    public void freshItineraryToTheCurrentTrueTileWaitsForNewAuthorityInEveryStraightDirection()
    {
        for (int[] direction : new int[][] {{128,0},{256,0},{-256,0},{0,256},{0,-256},
            {256,256},{-256,256},{256,-256},{-256,-256},{256,128},{-128,256}})
        {
            MovementPath path = routeToAlreadyAuthoritativeGoal(direction[0], direction[1]);
            LocalPoint fraction = path.position();
            assertEquals("newly built visual legs are pending, not inherited confirmation", "preview", path.phase());
            LocalPoint goal = path.clickedDestination();
            LocalPoint oldTick = p(goal.getX() - direction[0], goal.getY() - direction[1]);
            long hard = path.predictionDeadlineNanos();
            assertTrue(path.accept(oldTick, true));
            assertEquals(oldTick, path.confirmed()); assertEquals(fraction, path.position());
            assertEquals("stale progress does not renew the hard deadline", hard, path.predictionDeadlineNanos());
            path.advance(120_000_000L);
            assertTrue(direction[0] * (path.position().getX() - fraction.getX()) + direction[1] * (path.position().getY() - fraction.getY()) > 0);
            assertTrue(path.accept(goal, true));
            for (int ms = 140; ms <= 1400; ms += 20) { path.advance(ms * 1_000_000L); }
            assertEquals(goal, path.position()); assertTrue(path.finished());
        }
    }

    @Test
    public void unconfirmedAlreadyTrueGoalStillRecoversToChangedAuthorityOnTimeout()
    {
        MovementPath path = routeToAlreadyAuthoritativeGoal(256, 0);
        assertTrue(path.accept(p(6592,6720), true));
        for (int ms = 120; ms <= 2200; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(p(6592,6720), path.position());
    }

    @Test
    public void repeatedFreshReplansCannotRenewTheBoundOrInventAuthorityProgress()
    {
        MovementPath path = routeToAlreadyAuthoritativeGoal(256,0);
        long hard = path.predictionDeadlineNanos();
        for (int ms = 120; ms <= 300; ms += 20)
        {
            path.advance(ms * 1_000_000L);
            MovementPath next = path.retargetWalk(path.clickedDestination(), true, ms * 1_000_000L);
            assertNotNull(next); path = next;
            assertEquals(hard, path.predictionDeadlineNanos());
        }
    }

    @Test
    public void ordinaryAuthorityUpdatesKeepRealConfirmedDebtWithoutAFreshClick()
    {
        MovementPath path = MovementPath.idle(p(6592,6720), 0, 1.1, OPEN);
        assertTrue(path.accept(p(6848,6720), true)); path.advance(100_000_000L);
        assertTrue(path.accept(p(6592,6720), true));
        boolean reachedForward = false;
        for (int ms = 120; ms <= 1500; ms += 20)
        {
            path.advance(ms * 1_000_000L); reachedForward |= path.position().getX() >= 6848 - 9;
        }
        assertTrue("ordinary server-route debt is not discarded by this fresh-click rule", reachedForward);
        assertEquals(p(6592,6720), path.position());
    }

    @Test
    public void closedFractionalConnectorsCannotBeUsedToRetargetAnAlreadyTrueGoal()
    {
        boolean[] clear = {true};
        MovementPath path = MovementPath.idle(p(6592,6720), 0, 1.1, (a,b) -> clear[0]);
        assertTrue(path.accept(p(6848,6720), true)); path.advance(100_000_000L);
        LocalPoint before = path.position(); clear[0] = false;
        assertNull(path.retargetWalk(p(6848,6720), true, 100_000_000L)); assertEquals(before, path.position());
    }

    private static LocalPoint point(String[] row, int offset) { return p(Integer.parseInt(row[offset]), Integer.parseInt(row[offset+1])); }
}
