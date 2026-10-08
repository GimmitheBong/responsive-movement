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
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.*;

/** 2026-10-08 01:32 capture geometry/timing; API doubles do not execute the native renderer. */
public class EarlyMorningContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void recordedNorthernTreeApproachesDoNotOvershootSouthAndReverse() throws Exception
    {
        // Clicks 371 and 1279: preparation and first/final authority ages in us.
        long[][] captures = {{5952, 5_293, 626_798, 1_225_129},
            {6080, 5_374, 864_012, 1_284_413}};
        LocalPoint goal = p(5568, 5952);
        for (long[] capture : captures)
        {
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = fixture((int) capture[0], 6080);
                f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 10822, 41, 43));
                LocalPoint previous = f.start;
                long previousTime = 0;
                for (long t : timeline(cadence, 2_500_000, capture[1], capture[2], capture[3]))
                {
                    at(f, t);
                    if (t >= capture[3]) { f.authority = goal; f.destination = null; }
                    else if (t >= capture[2])
                    {
                        f.authority = p((int) capture[0] - 256, 6080); f.destination = goal;
                    }
                    else { f.destination = t >= capture[1] ? p(5312, 5568) : null; }
                    // Native fractions are held between these retained endpoints.
                    f.controller.update();
                    LocalPoint actual = f.controller.position();
                    assertTrue("no excursion south of the actual approach at " + t, actual.getY() >= goal.getY());
                    assertTrue("no northward return after an invented southern leg", actual.getY() <= previous.getY());
                    assertTrue("no eastward retrace", actual.getX() <= previous.getX());
                    budget(previous, actual, previousTime, t);
                    previous = actual; previousTime = t;
                }
                assertEquals(goal, f.controller.position());
                f.controller.close(); assertTrue(f.objects.isEmpty());
            }
        }
    }

    @Test
    public void recordedWalkSurvivesDelayedActionWithoutTheOneSecondIntermediateStop() throws Exception
    {
        // Click 1388 at 01:32:59.136. Action 10071 arrives at 59.479;
        // matching authority arrives at 01:33:00.060, 00.676 and 01.280.
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = fixture(5568, 5952);
            f.nativePoint = p(5612, 5996);
            f.controller.walkClick();
            LocalPoint goal = p(6336, 6080), previous = f.start;
            long previousTime = 0, stationary = 0;
            for (long t : timeline(cadence, 2_700_000, 3_738, 19_720, 60_000, 342_358, 923_459, 1_539_775, 2_143_648))
            {
                at(f, t);
                f.animation = t >= 342_358 && t < 923_459 ? 10071 : -1;
                if (t >= 2_143_648) { f.authority = goal; f.destination = null; f.nativePoint = p(5976, 5952); }
                else if (t >= 1_539_775) { f.authority = p(6080, 5952); f.destination = goal; f.nativePoint = p(5735, 5952); }
                else if (t >= 923_459) { f.authority = p(5824, 5952); f.destination = goal; f.nativePoint = f.start; }
                else
                {
                    f.destination = t >= 342_358 || t < 19_720 ? null : goal;
                    if (t >= 342_358) { f.nativePoint = f.start; }
                }
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (t > 100_000 && !previous.equals(goal))
                {
                    stationary = actual.equals(previous) ? stationary + t - previousTime : 0;
                    assertTrue("no stranded intermediate forecast at " + t + ": " + stationary, stationary <= 60_000);
                }
                assertTrue("no action-induced reversal", actual.getX() >= previous.getX());
                budget(previous, actual, previousTime, t);
                previous = actual; previousTime = t;
            }
            assertEquals(goal, f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void delayedActionsDuringObservationAndSmoothingKeepSceneMinimapAndWalkingParity() throws Exception
    {
        for (boolean minimap : new boolean[] {false, true})
        {
            for (boolean run : new boolean[] {false, true})
            {
                Fixture f = fixture(5568, 5952); f.run = run;
                if (minimap) { f.destination = p(6208, 5952); f.controller.walkClick(null); }
                else { f.controller.walkClick(); f.destination = p(6208, 5952); }
                f.frame(20); f.animation = 4321; f.frame(40);
                assertEquals("smoothing still owns release time", f.start, f.controller.position());
                f.frame(60); f.frame(80);
                assertTrue("delayed action cannot drop the observed Walk", f.controller.position().getX() > f.start.getX());
                assertEquals("preserve the real primary action", 4321, f.animation);
                f.controller.close(); assertTrue(f.objects.isEmpty());
            }
        }
    }

    @Test
    public void anActionWithoutANewDestinationCannotInventAStart() throws Exception
    {
        for (boolean unchanged : new boolean[] {false, true})
        {
            Fixture f = fixture(5568, 5952);
            if (unchanged) { f.destination = p(6208, 5952); }
            f.controller.walkClick(); f.animation = 4321;
            for (int ms = 20; ms <= 1500; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void delayedActionPermissionCannotRenewPredictionOnRepeatedClicks() throws Exception
    {
        Fixture f = fixture(5568, 5952); f.smoothing = 0;
        f.controller.walkClick(); f.destination = p(6336, 6080);
        f.frame(0); f.frame(100); f.animation = 4321;
        for (int ms = 120; ms <= 4000; ms += 20)
        {
            if (ms == 300 || ms == 500 || ms == 700)
            {
                f.at(ms); f.controller.walkClick();
                f.destination = ms == 500 ? p(6208, 5952) : p(6336, 6080);
            }
            f.frame(ms);
        }
        assertEquals("no authority means bounded recovery to the real origin", f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void worldInputEffectsNativeLocationAndSceneStillRetireWalkOwnership() throws Exception
    {
        for (int change = 0; change < 5; ++change)
        {
            Fixture f = fixture(5568, 5952); f.smoothing = 0;
            f.controller.walkClick(); f.destination = p(6336, 6080);
            f.frame(0); f.frame(100);
            if (change == 0) { f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 10822, 41, 43)); }
            if (change == 1) { f.spot = true; }
            if (change == 2) { f.animation = AnimationID.HUMAN_CASTTELEPORT; }
            if (change == 3) { f.controller.sceneChanged(false); }
            if (change == 4) { f.starts = false; }
            f.destination = null;
            if (change != 2) { f.animation = 4321; }
            for (int ms = 120; ms <= 4000; ms += 20) { f.frame(ms); }
            assertEquals("retired owner cannot continue the old Walk", f.start, f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void aNewWalkDuringAnExistingNonCombatActionStillRequiresNativeMovement() throws Exception
    {
        Fixture f = fixture(5568, 5952); f.animation = 4321;
        f.controller.walkClick(); f.destination = p(6208, 5952);
        for (int ms = 0; ms <= 400; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        f.authority = p(5824, 5952); f.animation = -1;
        for (int ms = 420; ms <= 1100; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() > f.start.getX());
        assertTrue("no speculation beyond the checked authority", f.controller.position().getX() <= f.authority.getX());
        f.destination = null; f.nativePoint = f.authority; f.frame(1120);
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    static Fixture fixture(int x, int y) throws Exception
    {
        Fixture f = new Fixture(x, y, 7000, 7000);
        f.speed = 1.2; f.smoothing = 60;
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            EarlyMorningContinuityTest.class.getResourceAsStream("early-0132-map.txt"), StandardCharsets.UTF_8)))
        {
            for (String line; (line = reader.readLine()) != null;)
            {
                if (line.startsWith("#")) { continue; }
                String[] fields = line.split(" "), flags = fields[1].split(",");
                for (int i = 0; i < flags.length; ++i) { f.flags[Integer.parseInt(fields[0])][40 + i] = Integer.parseUnsignedInt(flags[i], 16); }
            }
        }
        f.object(10822, p(5312, 5568), p(5568, 5824));
        return f;
    }

    private static void at(Fixture f, long micros)
    {
        f.now = 1_000_000_000L + micros * 1000;
        f.cycle = 100 + (int) (micros / 20_000);
    }

    private static TreeSet<Long> timeline(long cadence, long end, long... changes)
    {
        TreeSet<Long> times = new TreeSet<>();
        for (long t = 0; t <= end; t += cadence) { times.add(t); }
        for (long t : changes) { times.add(t); }
        times.add(end);
        return times;
    }

    private static void budget(LocalPoint before, LocalPoint after, long previous, long now)
    {
        assertTrue("one movement budget", MovementPath.distance(before, after) <= Math.ceil((now - previous) * 0.00048) + 1);
    }
}
