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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Captured click/authority/action timing and collision; explicit native engagement and held NPC doubles. */
@RunWith(Parameterized.class)
public class CombatInputReplayTest
{
    @Parameterized.Parameters(name = "18:10 combat input {0}")
    public static Collection<Object[]> cases()
    {
        return Arrays.asList(new Object[][] {{356}, {685}, {818}, {1058}, {1676}, {2140}, {2466}, {2494}, {2620}, {3348}});
    }
    private final int id;
    public CombatInputReplayTest(int id) { this.id = id; }

    @Test
    public void capturedInputRespondsContinuouslyAndRetainsOrReleasesTheCorrectFacingOwner() throws Exception
    {
        List<String[]> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("combat-1810.txt"), StandardCharsets.UTF_8)))
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
            Fixture f = new Fixture(Integer.parseInt(click[3]), Integer.parseInt(click[4]), Integer.parseInt(click[5]), Integer.parseInt(click[6]));
            f.speed = 1.1; f.smoothing = 0;
            boolean walk = click[2].equals("Walk");
            if (Integer.parseInt(click[7]) == 12788) { f.ranged("Magic shortbow (i)", 12788, 1); }
            else { f.weaponId = 26484; f.weaponName = "Captured adjacent melee weapon"; }
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] map : lines)
            {
                if (!map[0].equals("M")) { continue; }
                String[] cells = map[2].split(",");
                for (int y = 0; y < cells.length; ++y) { f.flags[Integer.parseInt(map[1])][35 + y] = Integer.parseUnsignedInt(cells[y], 16); }
            }
            f.click("Attack");
            if (walk)
            {
                f.interacting = f.npc; f.frame(0); f.animation = Integer.parseInt(click[8]);
                f.controller.walkClick();
            }
            long end = Long.parseLong(click[9]);
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t < end; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            int next = 0;
            LocalPoint previous = f.start, firstDestination = null;
            long previousTime = 0;
            boolean began = false;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] event = events.get(next++);
                    f.authority = point(event, 2);
                    f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                    if (firstDestination == null) { firstDestination = f.destination; }
                    f.nativePoint = point(event, 6); f.animation = Integer.parseInt(event[8]);
                    // Older ranged samples do not retain native engagement. The
                    // first real native shot is this explicit engagement double.
                    if (!walk && f.animation != -1) { f.interacting = f.npc; }
                }
                int action = f.animation, angle = f.controller.orientation();
                f.controller.update();
                LocalPoint actual = f.controller.position();
                assertEquals("native animation is never changed", action, f.animation);
                assertTrue("single movement budget", MovementPath.distance(previous, actual) <= Math.ceil((t - previousTime) * 0.00044) + 1);
                assertTrue("single turn budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                if (walk && firstDestination != null || id == 2140 || id == 3348)
                {
                    if (t > 60_000) { assertNotEquals("prompt start, even inside native combat poses", f.start, actual); }
                    if (began && !previous.equals(walk ? firstDestination : f.authority) &&
                        !(actual.equals(f.authority) && f.animation != -1))
                    {
                        // Fractional frames shorter than one local unit may round
                        // to the same point; a full replay frame must keep moving.
                        if (t - previousTime >= cadence - 1) { assertNotEquals("no intermediate stop/restart", previous, actual); }
                    }
                    began |= !actual.equals(f.start);
                }
                else if (t < Long.parseLong(events.get(1)[1])) { assertEquals("in-range Attack invents no trip", f.start, actual); }
                previous = actual; previousTime = t;
            }
            if (!walk)
            {
                assertEquals(f.authority, f.controller.position());
                if (end > 1_000_000)
                {
                    LocalPoint target = new LocalPoint(Integer.parseInt(click[5]), Integer.parseInt(click[6]), 0);
                    assertEquals("target survives shot and native idle", MotionMath.heading(target.getX() - f.authority.getX(),
                        target.getY() - f.authority.getY()), f.controller.orientation());
                }
            }
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    private static LocalPoint point(String[] values, int offset)
    {
        return new LocalPoint(Integer.parseInt(values[offset]), Integer.parseInt(values[offset + 1]), 0);
    }
}
