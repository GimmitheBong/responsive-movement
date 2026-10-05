package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** Recorded live-target/engagement timelines with explicit owned-hit and drawn-centre doubles. */
@RunWith(Parameterized.class)
public class CombatPacingReplayTest
{
    @Parameterized.Parameters(name = "combat lock/pacing {0}")
    public static Collection<Object[]> cases() { return Arrays.asList(new Object[][] {{499}, {755}, {1246}, {1470}, {2234}}); }
    private final int id;
    public CombatPacingReplayTest(int id) { this.id = id; }

    @Test
    public void reachedTargetStaysLockedAndPursuitDoesNotFinishBeforeTheRecordedNativeHit() throws Exception
    {
        List<String[]> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("combat-2224.txt"), StandardCharsets.UTF_8)))
        {
            lines = reader.lines().filter(line -> !line.startsWith("#")).map(line -> line.split(" ")).collect(Collectors.toList());
        }
        int index = 0;
        while (!lines.get(index)[0].equals("C") || Integer.parseInt(lines.get(index)[1]) != id) { ++index; }
        String[] click = lines.get(index++);
        List<String[]> events = new ArrayList<>();
        while (index < lines.size() && !lines.get(index)[0].equals("C")) { events.add(lines.get(index++)); }
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            List<MovementTrace.Entry> trace = new ArrayList<>();
            Fixture f = new Fixture(Integer.parseInt(click[2]), Integer.parseInt(click[3]),
                tile(Integer.parseInt(click[4]), Integer.parseInt(click[5])).getX(), tile(Integer.parseInt(click[4]), Integer.parseInt(click[5])).getY(), trace);
            f.weaponId = 26484; f.weaponName = "Captured adjacent melee weapon"; f.speed = 1.3;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] map : lines)
            {
                if (!map[0].equals("M")) { continue; }
                String[] values = map[2].split(",");
                for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(map[1])][40 + y] = Integer.parseUnsignedInt(values[y], 16); }
            }
            f.click("Attack");
            long end = Long.parseLong(events.get(events.size() - 1)[1]) + 1_000_000;
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= end; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            int next = 0, previousAction = -1;
            LocalPoint previous = f.start;
            long previousTime = 0;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    f.authority = point(event, 2);
                    f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                    f.nativePoint = point(event, 6);
                    boolean retained = Integer.parseInt(event[8]) >= 0;
                    if (retained)
                    {
                        LocalPoint npc = point(event, 8);
                        f.area = new WorldArea(3200 + npc.getSceneX(), 3200 + npc.getSceneY(), 1, 1, 0);
                    }
                    f.interacting = Boolean.parseBoolean(event[11]) ? f.npc : null;
                    f.animation = Integer.parseInt(event[10]);
                    if (retained && f.animation == 1658 && previousAction != 1658)
                    {
                        // Actual hitsplats were not recorded: this is an explicit
                        // event double aligned to the recorded native swing start.
                        f.controller.combatHit(f.npc, CombatFacingPacingTest.hit(true));
                    }
                    previousAction = f.animation;
                }
                int animation = f.animation, angle = f.controller.orientation();
                f.controller.update();
                LocalPoint actual = f.controller.position();
                assertEquals(animation, f.animation);
                assertTrue("one position budget", MovementPath.distance(previous, actual) <= Math.ceil((t - previousTime) * 0.00052) + 1);
                assertTrue("one facing budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                previous = actual; previousTime = t;
            }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
            List<MovementTrace.Sample> samples = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
                .map(e -> (MovementTrace.Sample) e).collect(Collectors.toList());
            assertTrue(samples.stream().anyMatch(s -> s.combatLocked));
            for (MovementTrace.Sample sample : samples)
            {
                if (sample.combatLocked) { assertFalse("gaze cannot hand off to native idle", sample.nativeRender); }
                if (sample.combatTrailing) { assertTrue("trailing retains walking motion until confirmation", sample.moving); }
            }
            if (id == 755 || id == 2234)
            {
                assertTrue("multi-tile pursuit uses slower trailing mode", samples.stream().anyMatch(s -> s.combatTrailing));
                assertTrue("real hit settles the checked debt", samples.stream().anyMatch(s -> s.combatHit && s.startDecision.equals("settled-combat-hit")));
            }
        }
    }

    private static LocalPoint point(String[] values, int offset)
    {
        return new LocalPoint(Integer.parseInt(values[offset]), Integer.parseInt(values[offset + 1]), 0);
    }
}
