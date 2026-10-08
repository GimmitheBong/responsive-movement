package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Captured final object-click sequence, native points held between samples; no native renderer. */
public class ApproachRefinementContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void recordedRepeatedAnchorRefinementDoesNotDropTheTailOrStopForFourHundredMilliseconds() throws Exception
    {
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = capturedFixture();
            f.nativePoint = p(9170, 4306);
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Use", 26815, 65, 35));
            LocalPoint goal = p(8384, 4416), previous = f.start;
            long last = 0, stationary = 0;
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= 2_800_000; t += cadence) { times.add(t); }
            for (long t : new long[] {4_030, 199_412, 201_266, 322_408, 338_671, 340_535,
                939_869, 1_519_362, 2_119_591, 2_800_000}) { times.add(t); }
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                f.authority = t >= 2_119_591 ? goal : t >= 1_519_362 ? p(8512, 4416) :
                    t >= 939_869 ? p(8768, 4416) : t >= 322_408 ? p(9024, 4416) : f.start;
                f.destination = t < 4_030 ? null : t >= 939_869 || t >= 322_408 && t < 340_535 ? goal : p(8384, 4544);
                if (t == 199_412 || t == 338_671)
                {
                    f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Use", 26815, 65, 35));
                }
                f.nativePoint = t >= 2_119_591 ? p(8760, 4416) : t >= 1_519_362 ? p(8926, 4416) :
                    t >= 939_869 ? p(9199, 4416) : t >= 340_535 ? p(9238, 4374) :
                    t >= 322_408 ? p(9234, 4370) : t >= 199_412 ? p(9210, 4346) : p(9170, 4306);
                int angle = f.controller.orientation(); f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("single frame movement budget", MovementPath.distance(previous, actual) <= Math.ceil((t - last) * 0.00048) + 1);
                assertTrue("single turn budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - last) / 16_667.0) + 1);
                if (t > 50_000 && !previous.equals(goal))
                {
                    stationary = actual.equals(previous) ? stationary + t - last : 0;
                    assertTrue("no stranded confirmed endpoint at " + t + ": " + stationary, stationary <= 40_000);
                }
                assertTrue("no eastward retrace", actual.getX() <= previous.getX());
                previous = actual; last = t;
            }
            assertEquals(goal, f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void nativeRefinementQueuesBehindExactConfirmedFractionsWithoutRenewingTheHardDeadline()
    {
        MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(2624, 1344), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        assertTrue(path.accept(p(1600, 1344), true));
        path.advance(100_000_000L);
        LocalPoint before = path.position(); long deadline = path.predictionDeadlineNanos();
        MovementPath refined = path.retargetApproachDestination(p(2624, 1600), true, 100_000_000L);
        assertNotNull("retain confirmed movement and queue a checked replacement tail", refined);
        assertEquals(before, refined.position()); assertEquals(deadline, refined.predictionDeadlineNanos());
        for (int ms = 120; ms <= 640; ms += 20)
        {
            refined.advance(ms * 1_000_000L);
            assertEquals("finish the confirmed eastbound pair before turning", 1344, refined.position().getY());
        }
        refined.advance(660_000_000L);
        assertTrue(refined.position().getX() > 1600);
        assertTrue(refined.clear());
        refined.advance(1_801_000_000L);
        for (int ms = 1820; ms <= 4000; ms += 20) { refined.advance(ms * 1_000_000L); }
        assertEquals("a refinement without more authority must still time out", p(1600, 1344), refined.position());
    }

    @Test
    public void occupiedConfirmedKnightKeepsItsCheckedChordBeforeTheRefinedTail()
    {
        MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(1728, 1600), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        assertTrue(path.accept(p(1600, 1472), true));
        path.advance(100_000_000L); LocalPoint before = path.position();
        MovementPath next = path.retargetApproachDestination(p(1856, 1728), true, 100_000_000L);
        assertNotNull(next); assertEquals(before, next.position());
        for (int ms = 120; ms <= 640; ms += 20)
        {
            next.advance(ms * 1_000_000L);
            assertEquals(Math.round(1344 + ms * 0.4), next.position().getX());
            assertEquals(Math.round(1344 + ms * 0.2), next.position().getY());
            assertTrue(next.clear());
        }
    }

    @Test
    public void blockedExactItemGoalCannotUseTheObjectAdjacentPolicy()
    {
        LocalPoint blocked = p(2624, 1600);
        for (boolean adjacent : new boolean[] {false, true})
        {
            MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(2624, 1344), true,
                0, 1, MovementPath.freshDeadline(0), (a, b) -> !a.equals(blocked) && !b.equals(blocked));
            assertTrue(path.accept(p(1600, 1344), true)); path.advance(100_000_000L);
            LocalPoint before = path.position();
            MovementPath next = path.retargetApproachDestination(blocked, true, 100_000_000L, adjacent);
            if (adjacent)
            {
                assertNotNull(next); assertEquals(before, next.position()); assertTrue(next.clear());
                assertEquals(128, Math.abs(blocked.getX() - next.clickedDestination().getX()) +
                    Math.abs(blocked.getY() - next.clickedDestination().getY()));
            }
            else { assertNull(next); assertEquals(before, path.position()); }
        }
    }

    @Test
    public void repeatedRefinementsRetainTheOriginalResponseBoundAndConfirmedPrefix()
    {
        MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(2624, 1344), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        assertTrue(path.accept(p(1600, 1344), true));
        long hard = path.predictionDeadlineNanos();
        for (int ms = 100; ms <= 500; ms += 100)
        {
            path.advance(ms * 1_000_000L);
            LocalPoint before = path.position();
            path = path.retargetApproachDestination(p(2624, ms % 200 == 0 ? 1344 : 1600), true, ms * 1_000_000L);
            assertNotNull(path); assertEquals(before, path.position()); assertEquals(hard, path.predictionDeadlineNanos());
        }
        for (int ms = 520; ms <= 890; ms += 20) { path.advance(ms * 1_000_000L); }
        path.advance(900_000_000L);
        assertEquals("original response expiry is not pushed out by refinements", "recovery", path.phase());
    }

    @Test
    public void npcRefinementsRetainTheirCapturedReserveAndSceneTranslation()
    {
        MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(2624, 1344), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true, false, p(2752, 1472), p(2752, 1472), 1);
        assertTrue(path.accept(p(1600, 1344), true)); path.advance(100_000_000L);
        path = path.retargetApproachDestination(p(2624, 1472), true, 100_000_000L);
        assertNotNull(path);
        assertTrue(path.traceSnapshot().fields(0).contains("npcReserveTiles=1 npcMin=2752,1472 npcMax=2752,1472"));
        LocalPoint before = path.position(); long hard = path.predictionDeadlineNanos();
        assertTrue(path.resumeScene(128, -128, p(1728, 1216), p(2752, 1344), true, 110_000_000L));
        assertEquals(p(before.getX() + 128, before.getY() - 128), path.position());
        assertEquals(hard, path.predictionDeadlineNanos()); assertTrue(path.clear());
    }

    @Test
    public void anExpiredRefinementCannotRenewItsResponseThroughTheOccupiedEdgeFallback()
    {
        MovementPath path = MovementPath.anticipate(p(1344, 1344), p(1344, 1344), p(2624, 1344), true,
            0, 1, MovementPath.freshDeadline(0), (a, b) -> true);
        path.advance(800_000_000L); LocalPoint before = path.position();
        assertNull(path.retargetApproachDestination(p(2624, 1600), true, 901_000_000L));
        assertEquals(before, path.position());
        path.advance(901_000_000L);
        assertEquals("recovery", path.phase());
    }

    private static Fixture capturedFixture() throws Exception
    {
        Fixture f = new Fixture(9280, 4416, 7000, 7000);
        f.speed = 1.2; f.smoothing = 60; f.faceInteractionsOnArrival = true;
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            ApproachRefinementContinuityTest.class.getResourceAsStream("object-refinement-1949-map.txt"), StandardCharsets.UTF_8)))
        {
            for (String line; (line = reader.readLine()) != null;)
            {
                if (line.startsWith("#")) { continue; }
                String[] fields = line.split(" "); int x = Integer.parseInt(fields[0]), y = 0;
                for (String token : fields[1].split(","))
                {
                    String[] run = token.split(":"); int count = Integer.parseInt(run[0]), flags = Integer.parseUnsignedInt(run[1], 16);
                    for (int i = 0; i < count; ++i, ++y) { if (y >= 30 && y <= 40) { f.flags[x][y] = flags; } }
                }
            }
        }
        return f;
    }
}
