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
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Recorded click/authority timing plus explicitly synthetic native model/pose doubles. */
public class CircleContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;

    @Test
    public void recordedSpamClickCircleKeepsOneBudgetAndArrivesAtTheLastActualEndpoint() throws Exception
    {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("circle-2143.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(s -> !s.startsWith("#")).forEach(s -> rows.add(s.split(" ")));
        }
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = new Fixture(6848, 7616, 8000, 8000);
            f.speed = 1.08; f.turnSpeed = 25; f.smoothing = 0;
            List<String[]> events = new ArrayList<>();
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] row : rows)
            {
                if (!row[0].equals("M")) { events.add(row); continue; }
                String[] flags = row[2].split(",");
                for (int y = 0; y < flags.length; ++y)
                {
                    f.flags[Integer.parseInt(row[1])][56 + y] = Integer.parseUnsignedInt(flags[y], 16);
                }
            }
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= 23_000_000; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            long previousTime = 0;
            LocalPoint previous = f.start;
            int next = 0;
            LocalPoint gridPrevious = f.start;
            double vx = 0, vy = 0, energy = 0;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("K")) { f.controller.walkClick(); }
                    else
                    {
                        f.authority = p(Integer.parseInt(event[2]), Integer.parseInt(event[3]));
                        f.destination = Integer.parseInt(event[4]) < 0 ? null : p(Integer.parseInt(event[4]), Integer.parseInt(event[5]));
                        f.nativePoint = p(Integer.parseInt(event[6]), Integer.parseInt(event[7]));
                    }
                }
                int angle = f.controller.orientation();
                f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("one frame movement budget at " + t, MovementPath.distance(previous, actual) <=
                    Math.ceil((t - previousTime) * 0.00044) + 1);
                assertTrue("one capped turn budget at " + t, Math.abs(MotionMath.difference(angle, f.controller.orientation())) <=
                    Math.ceil(25 * (t - previousTime) / 16_667.0) + 1);
                previous = actual; previousTime = t;
                if (t % cadence == 0)
                {
                    double nextX = (actual.getX() - gridPrevious.getX()) * 1000.0 / cadence;
                    double nextY = (actual.getY() - gridPrevious.getY()) * 1000.0 / cadence;
                    if (t >= 7_000_000 && t <= 17_900_000)
                    {
                        double kick = Math.hypot(nextX - vx, nextY - vy);
                        energy += kick * kick;
                    }
                    vx = nextX; vy = nextY; gridPrevious = actual;
                }
            }
            assertEquals(p(6592, 7616), f.controller.position());
            // Compare at the native 20-ms grid: the uncorrected route contributes
            // 6.0925; this correction contributes 4.83 with the same recorded input.
            // This measures aggregate discontinuity, not the renderer or every turn.
            if (cadence == 20_000) { assertTrue("reduce captured aggregate velocity kicks", energy < 5.5); }
            f.controller.close();
        }
    }

    private static MovementPath eastward()
    {
        MovementPath path = MovementPath.anticipate(p(6848, 8000), p(6848, 8000), p(7232, 8000),
            true, 0, 1.1, MovementPath.freshDeadline(0), OPEN);
        for (int ms = 20; ms <= 200; ms += 20) { path.advance(ms * 1_000_000L); }
        return path;
    }

    @Test
    public void capturedEastToSoutheastJoinKeepsItsIncomingTangentInsteadOfKickingSideways()
    {
        // The 21:43:46.137 join changes eastbound travel to a southeast knight.
        MovementPath old = eastward();
        LocalPoint before = old.position();
        MovementPath next = old.retargetWalk(p(7232, 7744), true, 200_000_000L);
        assertNotNull(next); assertEquals(before, next.position());
        assertEquals(old.predictionDeadlineNanos(), next.predictionDeadlineNanos());
        next.advance(210_000_000L);
        assertTrue(next.position().getX() > before.getX());
        assertTrue("first 10 ms blends into the turn, not the whole new chord heading",
            Math.abs(next.position().getY() - before.getY()) <= 1);
        assertTrue(next.clear());
    }

    @Test
    public void curvedJoinPacingMatchesAcrossCadencesAndCollisionRecoveryStillFinishes()
    {
        LocalPoint reference = null;
        for (int cadence : new int[] {8, 20, 33})
        {
            MovementPath next = fractionalKnight().retargetWalk(p(7104, 7872), true, 200_000_000L);
            long deadline = next.predictionDeadlineNanos();
            for (int ms = 200 + cadence; ms < 280; ms += cadence) { next.advance(ms * 1_000_000L); }
            next.advance(280_000_000L);
            assertTrue("the cadence comparison exercises an active curve", next.traceSnapshot().fields(0).matches(".* joins=\\[[0-9].*"));
            if (reference == null) { reference = next.position(); }
            assertEquals("arc-length pacing is independent of frame splitting", reference, next.position());
            assertTrue(next.clear()); assertEquals(deadline, next.predictionDeadlineNanos());
            LocalPoint before = next.position();
            assertTrue(next.resumeScene(-128, -128, p(6720,7872), p(6976,7744), true, 280_000_000L));
            assertEquals(p(before.getX() - 128, before.getY() - 128), next.position());
            assertEquals(deadline, next.predictionDeadlineNanos());
            for (int ms = 300; ms <= 3000; ms += 20)
            {
                before = next.position(); next.advance(ms * 1_000_000L);
                assertTrue(MovementPath.distance(before, next.position()) <= 10); assertTrue(next.clear());
            }
            assertEquals(p(6720, 7872), next.position()); assertTrue(next.finished());
        }
    }

    private static MovementPath fractionalKnight()
    {
        MovementPath path = MovementPath.anticipate(p(6848,8000), p(6848,8000), p(7104,8128),
            true, 0, 1.1, MovementPath.freshDeadline(0), OPEN);
        for (int ms = 20; ms <= 200; ms += 20) { path.advance(ms * 1_000_000L); }
        return path;
    }

    @Test
    public void smallFacingArrivalsEaseWithoutExceedingTheCapOrDependingOnFrameSplitting()
    {
        MovementFacing whole = new MovementFacing(), split = new MovementFacing();
        whole.reset(1500); split.reset(1500); whole.movement(1, 0); split.movement(1, 0);
        whole.advance(0, false, 40);
        for (int i = 0; i < 20; ++i) { split.advance(0, false, 2); }
        assertEquals(whole.angle(), split.angle(), 0.000001);
        assertTrue("a near target decelerates rather than abruptly stopping", whole.angle() < 1536);
        assertTrue(whole.angle() > 1500);
        for (int i = 0; i < 100; ++i) { split.advance(0, false, 4); }
        assertEquals(1536, split.angle(), 0);
    }

    @Test
    public void curvedJoinsRejectClosingCollisionAndDoNotRenewRapidClickDeadlines()
    {
        boolean[] clear = {true};
        MovementPath path = MovementPath.anticipate(p(6848, 8000), p(6848, 8000), p(7104, 8128),
            true, 0, 1.1, MovementPath.freshDeadline(0), (a, b) -> clear[0]);
        for (int ms = 20; ms <= 200; ms += 20) { path.advance(ms * 1_000_000L); }
        long hard = path.predictionDeadlineNanos();
        path = path.retargetWalk(p(7104, 7872), true, 200_000_000L);
        path.advance(220_000_000L);
        assertTrue(path.clear());
        assertTrue(path.traceSnapshot().fields(0).matches(".* joins=\\[[0-9].*"));
        LocalPoint before = path.position();
        MovementPath repeated = path.retargetWalk(p(7104, 7872), true, 220_000_000L);
        assertNotNull(repeated); assertEquals(before, repeated.position());
        assertEquals(hard, repeated.predictionDeadlineNanos());
        clear[0] = false;
        assertFalse(path.clear());
        assertNull(path.retargetWalk(p(6976, 7744), true, 240_000_000L));
        assertEquals(before, path.position());
    }

    @Test
    public void anUnprovedKnightOrderingKeepsItsCheckedCornerInsteadOfCurving()
    {
        // Block only one reverse ordering: the straight-first route itself is legal.
        BiPredicate<LocalPoint, LocalPoint> collision = (a, b) ->
            !(a.equals(p(6976, 7872)) && b.equals(p(6848, 8000)));
        MovementPath path = MovementPath.anticipate(p(6848, 8000), p(6848, 8000), p(7232, 8000),
            true, 0, 1.1, MovementPath.freshDeadline(0), collision);
        for (int ms = 20; ms <= 200; ms += 20) { path.advance(ms * 1_000_000L); }
        MovementPath next = path.retargetWalk(p(7232, 7744), true, 200_000_000L);
        assertNotNull(next); next.advance(220_000_000L);
        assertEquals(8000, next.position().getY());
        assertTrue(next.clear());
        assertFalse(next.traceSnapshot().fields(0).matches(".* joins=\\[[0-9].*"));
    }

    @Test
    public void fractionalCurvesRemainInsideTheirProvenCorridorsAndSurviveASecondClick()
    {
        int curved = 0;
        for (int[] target : new int[][] {{7104,7872},{7232,8000},{6976,8384},{6848,8256},{7360,8256}})
        {
            MovementPath path = fractionalKnight();
            long deadline = path.predictionDeadlineNanos();
            path = path.retargetWalk(p(target[0],target[1]), true, 200_000_000L);
            assertNotNull(path); path.advance(220_000_000L);
            String fields = path.traceSnapshot().fields(0);
            if (fields.matches(".* joins=\\[[0-9].*"))
            {
                ++curved;
                // All four cubic control points must stay
                // in the original knight's proven parallelogram, not its full box.
                String[] controls = fields.substring(fields.indexOf(" joins=[") + 8, fields.indexOf(']', fields.indexOf(" joins=["))).split(",");
                for (int i = 0; i < 8; i += 2)
                {
                    double x = Double.parseDouble(controls[i]) - 6848, y = Double.parseDouble(controls[i + 1]) - 8000;
                    assertTrue(x >= -0.000001 && x <= 256.000001 && y >= -0.000001 && y <= 128.000001);
                    assertTrue(x - y >= -0.000001 && x - y <= 128.000001);
                }
            }
            assertTrue(path.clear());
            LocalPoint before = path.position();
            MovementPath replacement = path.retargetWalk(p(target[0],target[1]), true, 220_000_000L);
            assertNotNull(replacement); assertEquals(before, replacement.position());
            assertEquals(deadline, replacement.predictionDeadlineNanos());
            for (int ms = 240; ms <= 600; ms += 20)
            {
                before = replacement.position(); replacement.advance(ms * 1_000_000L);
                assertTrue(MovementPath.distance(before, replacement.position()) <= 10);
                assertTrue(replacement.clear());
            }
        }
        assertTrue("exercise fractional curves as well as straight connector fallback", curved > 0);
    }
}
