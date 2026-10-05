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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Recorded shot/effect/input timing; explicit stable effect identity and native selector/clock doubles. */
@RunWith(Parameterized.class)
public class BowVisualReplayTest
{
    @Parameterized.Parameters(name = "21:17 bow visuals {0}")
    public static Collection<Object[]> cases() { return Arrays.asList(new Object[][] {{313}, {841}, {1751}, {2581}, {2972}}); }
    private final int id;
    public BowVisualReplayTest(int id) { this.id = id; }

    @Test
    public void nativeWalkDuringAShotKeepsOneMovementBudgetAndMatchingGaitWithoutChangingThePrimaryClock() throws Exception
    {
        List<String[]> rows;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("bow-2117.txt"), StandardCharsets.UTF_8)))
        {
            rows = reader.lines().filter(line -> !line.startsWith("#")).map(line -> line.split(" ")).collect(Collectors.toList());
        }
        int index = 0;
        while (!rows.get(index)[0].equals("C") || Integer.parseInt(rows.get(index)[1]) != id) { ++index; }
        String[] click = rows.get(index++);
        List<String[]> events = new ArrayList<>();
        while (index < rows.size() && !rows.get(index)[0].equals("C")) { events.add(rows.get(index++)); }
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            List<MovementTrace.Entry> trace = new ArrayList<>();
            Fixture f = new Fixture(Integer.parseInt(click[2]), Integer.parseInt(click[3]), Integer.parseInt(click[4]), Integer.parseInt(click[5]), trace);
            f.ranged("Magic shortbow (i)", 12788, 1); f.smoothing = 0; f.speed = 1.1;
            CombatVisualContinuityTest.selectors(f);
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] map : rows)
            {
                if (!map[0].equals("M")) { continue; }
                String[] values = map[2].split(",");
                for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(map[1])][40 + y] = Integer.parseUnsignedInt(values[y], 16); }
            }
            f.click("Attack"); f.interacting = f.npc; f.frame(0);
            f.animation = Integer.parseInt(click[6]); f.spot = Boolean.parseBoolean(click[7]);
            f.controller.walkClick();
            long end = Long.parseLong(click[8]);
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t < end; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            int next = 0, tick = -1;
            long previousTime = 0;
            net.runelite.api.coords.LocalPoint previous = f.start;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("K"))
                    {
                        if (event[2].equals("Walk")) { f.controller.walkClick(); }
                        else { MidnightContinuityTest.take(f, 526, Integer.parseInt(event[3]), Integer.parseInt(event[4])); }
                    }
                    else
                    {
                        f.authority = point(event, 2); f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6); f.animation = Integer.parseInt(event[8]);
                        boolean active = Boolean.parseBoolean(event[9]);
                        if (active && !f.spot) { f.spotStartCycle = f.cycle; }
                        f.spot = active;
                    }
                }
                if (tick != f.cycle)
                {
                    // Native ticks read the prepared gait selectors. They advance
                    // pose/action frames independently of rendered movement.
                    int selector = f.nativePoint == null || f.nativePoint.equals(f.authority) ? f.player.getIdlePoseAnimation() : f.player.getRunAnimation();
                    f.poses.put("PoseAnimation", selector);
                    f.poses.put("PoseAnimationFrame", (f.player.getPoseAnimationFrame() + 1) % 8);
                    tick = f.cycle;
                }
                int primaryFrame = (f.cycle - 100) % 8;
                f.poses.put("AnimationFrame", primaryFrame);
                int action = f.animation, angle = f.controller.orientation();
                f.controller.update();
                assertEquals(action, f.animation); assertEquals(primaryFrame, (int) f.poses.get("AnimationFrame"));
                assertTrue("one position budget", MovementPath.distance(previous, f.controller.position()) <= Math.ceil((t - previousTime) * 0.00044) + 1);
                assertTrue("one turn budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                if (t >= 70_000 && t <= 150_000) { assertNotEquals("prompt release regardless of shot effect", f.start, f.controller.position()); }
                previous = f.controller.position(); previousTime = t;
            }
            f.controller.close();
            List<MovementTrace.Sample> samples = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
                .map(e -> (MovementTrace.Sample) e).filter(s -> s.cycle > 100).collect(Collectors.toList());
            for (MovementTrace.Sample sample : samples)
            {
                assertEquals("gait reflects the custom queue, including through native actions/effects",
                    sample.moving ? sample.running ? 824 : 819 : 808, sample.pose);
            }
            if (id != 2972) { assertTrue("owned graphic exception is exercised", samples.stream().anyMatch(s -> s.combatEffectCarry)); }
            assertTrue(f.objects.isEmpty());
        }
    }

    private static net.runelite.api.coords.LocalPoint point(String[] values, int offset)
    {
        return CombatVisualContinuityTest.p(Integer.parseInt(values[offset]), Integer.parseInt(values[offset + 1]));
    }
}
