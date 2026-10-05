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
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** Recorded player/flag/action/collision timing with explicitly synthetic moving-target evidence. */
@RunWith(Parameterized.class)
public class CombatCaptureReplayTest
{
    @Parameterized.Parameters(name = "combat timing {0}")
    public static Collection<Object[]> cases() { return Arrays.asList(new Object[][] {{915}, {2650}, {2829}}); }
    private final int id;
    public CombatCaptureReplayTest(int id) { this.id = id; }

    @Test
    public void targetStepsDoNotDiscardTheApproachOrCreateIntermediateStagingPauses() throws Exception
    {
        List<String[]> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("combat-2034.txt"), StandardCharsets.UTF_8)))
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
            Fixture f = new Fixture(Integer.parseInt(click[2]), Integer.parseInt(click[3]),
                tile(Integer.parseInt(click[4]), Integer.parseInt(click[5])).getX(), tile(Integer.parseInt(click[4]), Integer.parseInt(click[5])).getY());
            f.weaponId = 26484; f.weaponName = "Captured adjacent melee weapon"; f.speed = 1.3;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] line : lines)
            {
                if (!line[0].equals("M")) { continue; }
                String[] flags = line[2].split(",");
                for (int y = 0; y < flags.length; ++y) { f.flags[Integer.parseInt(line[1])][38 + y] = Integer.parseUnsignedInt(flags[y], 16); }
            }
            f.interacting = f.npc; // Explicit engagement double: the old trace has no native interacting field.
            f.controller.worldInteraction(f.event(MenuAction.NPC_SECOND_OPTION, "Attack"));
            long end = Long.parseLong(events.get(events.size() - 1)[1]) + 1_000_000;
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= end; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            int next = 0; long stopped = 0, previousTime = 0;
            LocalPoint previous = f.start;
            for (long t : times)
            {
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    if (event[0].equals("T"))
                    {
                        f.area = new WorldArea(3200 + Integer.parseInt(event[2]), 3200 + Integer.parseInt(event[3]), 1, 1, 0);
                    }
                    else
                    {
                        f.authority = point(event, 2);
                        f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6); f.animation = Integer.parseInt(event[8]);
                    }
                }
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                int action = f.animation, angle = f.controller.orientation();
                f.controller.update();
                LocalPoint actual = f.controller.position();
                int tx = (f.area.getX() - 3200) * 128 + 64, ty = (f.area.getY() - 3200) * 128 + 64;
                boolean adjacent = Math.abs(tx - actual.getX()) + Math.abs(ty - actual.getY()) == 128;
                if (actual.equals(previous) && !adjacent)
                {
                    stopped += t - previousTime;
                    assertTrue("stopped short of target for " + stopped + "us at " + actual, stopped <= 80_000);
                }
                else { stopped = 0; }
                assertEquals("native action timing is untouched", action, f.animation);
                assertTrue("one positional budget", MovementPath.distance(previous, actual) <= Math.ceil((t - previousTime) * 0.00052) + 1);
                assertTrue("one turn budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                previous = actual; previousTime = t;
            }
            assertEquals("confirmed captured final endpoint", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    private static LocalPoint point(String[] fields, int offset)
    {
        return new LocalPoint(Integer.parseInt(fields[offset]), Integer.parseInt(fields[offset + 1]), 0);
    }
}
