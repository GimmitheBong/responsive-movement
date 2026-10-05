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
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Both new capture timelines, including their preceding mixed Walk/interaction input. */
@RunWith(Parameterized.class)
public class NpcContinuityReplayTest
{
    @Parameterized.Parameters(name = "{0} click {1}")
    public static Collection<Object[]> cases() throws Exception
    {
        List<Object[]> result = new ArrayList<>();
        for (String session : new String[] {"1901", "1905"})
        {
            for (String[] event : lines("npc-continuity-" + session + ".txt"))
            {
                if (event[0].equals("C") && event[3].startsWith("NPC_")) { result.add(new Object[] {session, Integer.parseInt(event[2])}); }
            }
        }
        return result;
    }

    private final String session;
    private final int target;
    public NpcContinuityReplayTest(String session, int target) { this.session = session; this.target = target; }

    @Test
    public void npcApproachKeepsTravelAndFacingContinuousThroughLateAuthority() throws Exception
    {
        List<String[]> events = lines("npc-continuity-" + session + ".txt");
        int clickIndex = 0;
        while (!events.get(clickIndex)[0].equals("C") || Integer.parseInt(events.get(clickIndex)[2]) != target) { ++clickIndex; }
        long clicked = Long.parseLong(events.get(clickIndex)[1]), until = clicked + 2_400_000;
        LocalPoint goal = null;
        for (int i = clickIndex + 1; i < events.size(); ++i)
        {
            String[] event = events.get(i);
            if (event[0].equals("C")) { until = Math.min(until, Long.parseLong(event[1]) - 1); break; }
            goal = point(event, 2);
        }
        assertNotNull(goal);
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            LocalPoint start = point(events.get(0), 2);
            Fixture f = new Fixture(start.getX(), start.getY(), 6592, 8256);
            f.speed = 1.2; f.smoothing = 0;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] map : lines("red-click-1820.txt"))
            {
                if (!map[0].equals("M")) { continue; }
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
            int next = 0, reverse = 0;
            long stopped = 0, lastTime = 0;
            LocalPoint previous = start;
            boolean active = false, npcProgress = false;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("S"))
                    {
                        LocalPoint endpoint = point(event, 2);
                        // Arrival evidence must not send the body back around a
                        // construction anchor. Earlier updates can legitimately
                        // finish an unfinished confirmed prefix from the old Walk.
                        if (active && endpoint.equals(goal)) { npcProgress = true; }
                        f.authority = endpoint;
                        f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6);
                    }
                    else if (event[3].equals("WALK")) { f.controller.walkClick(); }
                    else
                    {
                        f.area = new WorldArea(3200 + Integer.parseInt(event[5]), 3200 + Integer.parseInt(event[6]), 1, 1, 0);
                        f.controller.worldInteraction(f.event(MenuAction.valueOf(event[3]), event[4]));
                        if (Integer.parseInt(event[2]) == target) { active = true; }
                    }
                }
                int beforeAngle = f.controller.orientation();
                f.controller.update();
                LocalPoint actual = f.controller.position();
                if (active)
                {
                    if (actual.equals(previous) && MovementPath.distance(actual, goal) > 1)
                    {
                        stopped += t - lastTime;
                        assertTrue("intermediate stop for " + stopped + "us at " + actual, stopped <= 80_000);
                    }
                    else { stopped = 0; }
                    if (npcProgress && (actual.getX() - previous.getX()) * (goal.getX() - previous.getX()) +
                        (actual.getY() - previous.getY()) * (goal.getY() - previous.getY()) < 0)
                    {
                        reverse += MovementPath.distance(actual, previous);
                    }
                    assertTrue("arrival retraced " + reverse + " units at " + (t - clicked), reverse <= 16);
                    assertTrue("single travel budget", MovementPath.distance(actual, previous) <= Math.ceil((t - lastTime) * 0.00048) + 1);
                    assertTrue("single capped turn budget", Math.abs(MotionMath.difference(beforeAngle, f.controller.orientation())) <=
                        Math.ceil(30 * (t - lastTime) / 16_667.0) + 1);
                }
                previous = actual; lastTime = t;
            }
            // Some rapid replacements occur before the visible player can finish.
            // Longer isolated approaches must converge to the captured real endpoint.
            if (until - clicked >= 2_000_000) { assertEquals(goal, f.controller.position()); }
            f.controller.close();
        }
    }

    private static List<String[]> lines(String name) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            NpcContinuityReplayTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)))
        {
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isEmpty()).map(line -> line.split(" ")).collect(Collectors.toList());
        }
    }
    private static LocalPoint point(String[] fields, int offset)
    {
        return new LocalPoint(Integer.parseInt(fields[offset]), Integer.parseInt(fields[offset + 1]), 0);
    }
}
