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
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Captured moving Walk -> Bank handoffs and ordinary NPC authority/flag timing. */
@RunWith(Parameterized.class)
public class RedClickReplayTest
{
    @Parameterized.Parameters(name = "red click {0}")
    public static Collection<Object[]> cases()
    {
        return Arrays.asList(new Object[][] {{360}, {734}, {1207}, {1647}, {1914}, {2670},
            {4175}, {4457}, {4733}, {5083}});
    }

    private final int target;
    public RedClickReplayTest(int target) { this.target = target; }

    @Test
    public void checkedApproachDoesNotRetraceOrPublishAnIntermediateStop() throws Exception
    {
        List<String[]> events = new ArrayList<>();
        List<String[]> maps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("red-click-1820.txt"), StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line -> !line.startsWith("#")).forEach(line -> {
                String[] fields = line.split(" ");
                (fields[0].equals("M") ? maps : events).add(fields);
            });
        }
        int clickIndex = 0;
        while (!events.get(clickIndex)[0].equals("C") || Integer.parseInt(events.get(clickIndex)[2]) != target) { ++clickIndex; }
        long clicked = Long.parseLong(events.get(clickIndex)[1]);
        long until = clicked + 2_400_000;
        for (int i = clickIndex + 1; i < events.size(); ++i)
        {
            if (events.get(i)[0].equals("C")) { until = Math.min(until, Long.parseLong(events.get(i)[1]) - 1); break; }
        }
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = new Fixture(5568, 6208, 6592, 6336);
            f.speed = 1.2; f.smoothing = 0;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] map : maps)
            {
                String[] values = map[2].split(",");
                for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(map[1])][44 + y] = Integer.parseUnsignedInt(values[y], 16); }
            }
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= until; t += cadence) { times.add(t); }
            for (String[] event : events)
            {
                long t = Long.parseLong(event[1]);
                if (t <= until) { times.add(t); }
            }
            times.add(until);
            int next = 0;
            LocalPoint previous = f.start, redStart = null;
            boolean active = false;
            long stopped = 0, lastTime = 0;
            int reverseDistance = 0;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("S"))
                    {
                        f.authority = point(event, 2);
                        f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6);
                    }
                    else if (event[3].equals("WALK")) { f.controller.walkClick(); }
                    else
                    {
                        f.area = new WorldArea(3200 + Integer.parseInt(event[5]), 3200 + Integer.parseInt(event[6]), 1, 1, 0);
                        f.controller.worldInteraction(f.event(MenuAction.valueOf(event[3]), event[4]));
                        if (Integer.parseInt(event[2]) == target) { active = true; redStart = f.controller.position(); }
                    }
                }
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (active)
                {
                    // Allow a checked corner's small connector, but not the old Walk
                    // forecast running on and then returning hundreds of local units.
                    int dx = actual.getX() - previous.getX(), dy = actual.getY() - previous.getY();
                    int goalX = target == 5083 ? 6976 : target >= 4175 || target == 734 ? 6464 : 6336;
                    int goalY = target < 4175 ? target == 734 ? 6336 : target <= 1207 ? 6208 : 6592 : target == 5083 ? 8384 : 8256;
                    if (dx * (goalX - previous.getX()) + dy * (goalY - previous.getY()) < 0)
                    {
                        reverseDistance += MovementPath.distance(actual, previous);
                    }
                    assertTrue("click " + target + " retraced " + reverseDistance + " units at " + (t - clicked), reverseDistance <= 64);
                    if (actual.equals(previous) && MovementPath.distance(actual, new LocalPoint(goalX, goalY, 0)) > 1)
                    {
                        stopped += t - lastTime;
                        assertTrue("click " + target + " stopped short for " + stopped + "us at " + actual, stopped <= 80_000);
                    }
                    else { stopped = 0; }
                    assertTrue("one pacing budget", MovementPath.distance(actual, previous) <= Math.ceil((t - lastTime) * 0.00048) + 1);
                }
                previous = actual; lastTime = t;
            }
            assertNotNull(redStart);
            assertEquals("eventual server endpoint", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    private static LocalPoint point(String[] fields, int offset)
    {
        return new LocalPoint(Integer.parseInt(fields[offset]), Integer.parseInt(fields[offset + 1]), 0);
    }
}
