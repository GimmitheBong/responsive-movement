package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact native pickup destinations, using the two reported collision/timing captures. */
public class GroundItemDestinationTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    private static final class Capture
    {
        final String[] click;
        final List<String[]> events = new ArrayList<>(), map = new ArrayList<>();

        Capture(int id) throws Exception
        {
            List<String[]> rows;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                GroundItemDestinationTest.class.getResourceAsStream("pickup-2033-2035.txt"), StandardCharsets.UTF_8)))
            {
                rows = reader.lines().filter(line -> !line.startsWith("#")).map(line -> line.split(" ")).collect(Collectors.toList());
            }
            int index = 0;
            while (!rows.get(index)[0].equals("C") || Integer.parseInt(rows.get(index)[1]) != id) { ++index; }
            click = rows.get(index++);
            while (index < rows.size() && !rows.get(index)[0].equals("C"))
            {
                String[] row = rows.get(index++);
                if (row[0].equals("M")) { map.add(row); }
                else { events.add(row); }
            }
        }

        LocalPoint goal() { return point(click, 6); }
        Fixture fixture()
        {
            Fixture f = new Fixture(Integer.parseInt(click[4]), Integer.parseInt(click[5]), 8768, 6336);
            f.speed = 1.1; f.smoothing = 0;
            for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            for (String[] column : map)
            {
                String[] values = column[2].split(",");
                for (int y = 0; y < values.length; ++y) { f.flags[Integer.parseInt(column[1])][43 + y] = Integer.parseUnsignedInt(values[y], 16); }
            }
            return f;
        }
    }

    @Test
    public void recorded2033PickupDoesNotWaitThenReturnFromItsAdjacentForecast() throws Exception { replay(2033); }

    @Test
    public void recorded2035PickupDoesNotStopBesideTheItemAndRestartOnFinalAuthority() throws Exception { replay(2035); }

    private static void replay(int id) throws Exception
    {
        Capture c = new Capture(id);
        for (long cadence : new long[] {8_333, 20_000, 33_333})
        {
            Fixture f = c.fixture();
            f.destination = Integer.parseInt(c.click[8]) < 0 ? null : point(c.click, 8);
            take(f, c.goal());
            TreeSet<Long> times = new TreeSet<>();
            long end = Long.parseLong(c.events.get(c.events.size() - 1)[1]);
            for (long t = 0; t <= end; t += cadence) { times.add(t); }
            for (String[] event : c.events) { times.add(Long.parseLong(event[1])); }
            int next = 0;
            LocalPoint previous = f.start;
            long previousTime = 0;
            for (long t : times)
            {
                at(f, t);
                while (next < c.events.size() && Long.parseLong(c.events.get(next)[1]) <= t)
                {
                    String[] event = c.events.get(next++);
                    if (event[0].equals("K")) { take(f, c.goal()); }
                    else
                    {
                        f.authority = point(event, 2);
                        f.destination = Integer.parseInt(event[4]) < 0 ? null : point(event, 4);
                        f.nativePoint = point(event, 6); f.animation = Integer.parseInt(event[8]);
                    }
                }
                int angle = f.controller.orientation(); f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("one movement budget", MovementPath.distance(previous, actual) <= Math.ceil((t - previousTime) * 0.00044) + 1);
                assertTrue("one facing budget", Math.abs(MotionMath.difference(angle, f.controller.orientation())) <= Math.ceil(30 * (t - previousTime) / 16_667.0) + 1);
                assertTrue("no timeout backstep", actual.getX() <= previous.getX());
                if (t > 2 * cadence && !previous.equals(c.goal()) && t - previousTime >= cadence - 1)
                {
                    assertNotEquals("no stationary intermediate staging tile at " + t, previous, actual);
                }
                if (previous.equals(c.goal())) { assertEquals("hold the exact native destination", previous, actual); }
                previous = actual; previousTime = t;
            }
            assertEquals(c.goal(), f.controller.position());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void exactTilePolicyAppliesOnlyToOrdinaryGroundItemOptions() throws Exception
    {
        Capture c = new Capture(2033);
        MenuAction[] actions = {MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION,
            MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION, MenuAction.GROUND_ITEM_FIFTH_OPTION,
            MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, MenuAction.ITEM_USE_ON_GROUND_ITEM};
        for (MenuAction action : actions)
        {
            Fixture f = c.fixture();
            f.controller.worldInteraction(f.sceneEvent(action, "Action", 21326, 66, 48)); f.destination = c.goal();
            for (int ms = 0; ms <= 800; ms += 20) { f.frame(ms); }
            boolean groundOption = action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.GROUND_ITEM_SECOND_OPTION ||
                action == MenuAction.GROUND_ITEM_THIRD_OPTION || action == MenuAction.GROUND_ITEM_FOURTH_OPTION || action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
            assertEquals("native endpoint policy scope: " + action, groundOption ? c.goal() : p(8640, 6208), f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void anInaccessibleExactItemTileCannotBecomeAnAdjacentPreview() throws Exception
    {
        Capture c = new Capture(2033);
        Fixture f = c.fixture(); f.flags[66][48] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        take(f, c.goal()); f.destination = c.goal();
        for (int ms = 0; ms <= 600; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        f.flags[66][48] = 0; f.authority = c.goal(); f.destination = null;
        for (int ms = 620; ms <= 1800; ms += 20) { f.frame(ms); }
        assertEquals("new checked authority is still accepted normally", c.goal(), f.controller.position());
        f.controller.close();
    }

    @Test
    public void clickedItemCoordinatesDoNotReplaceMissingOrUnchangedNativeEvidence() throws Exception
    {
        Capture c = new Capture(2033);
        for (boolean unchanged : new boolean[] {false, true})
        {
            Fixture f = c.fixture(); f.destination = unchanged ? c.goal() : null;
            take(f, c.goal());
            for (int ms = 0; ms <= 500; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void movingPickupUsesTheExactEndpointThroughTheExistingFractionalJoin() throws Exception
    {
        Capture c = new Capture(2033);
        Fixture f = c.fixture(); f.ranged("Magic shortbow (i)", 12788, 1);
        f.click("Attack"); f.interacting = f.npc; f.frame(0);
        f.controller.walkClick(); f.destination = p(9024, 6080); f.frame(0); f.frame(100);
        LocalPoint before = f.controller.position(); assertTrue(before.getX() > f.start.getX());
        take(f, c.goal()); f.destination = c.goal();
        for (int ms = 120; ms <= 1200; ms += 20)
        {
            if (ms == 400) { f.authority = p(8640, 6208); }
            if (ms == 700) { f.authority = c.goal(); f.destination = null; }
            f.frame(ms);
            assertTrue("checked fractional handoff has no snap", MovementPath.distance(before, f.controller.position()) <= 9);
            if (ms >= 200 && !before.equals(c.goal())) { assertNotEquals("no adjacent staging pause", before, f.controller.position()); }
            if (ms >= 900) { assertEquals(c.goal(), f.controller.position()); }
            before = f.controller.position();
        }
        f.controller.close();
    }

    @Test
    public void queuedPickupPreservesTheConfirmedPrefixAndForecastsTheFinalItemTile() throws Exception
    {
        Capture c = new Capture(2033);
        Fixture f = c.fixture(); f.authority = p(8896, 6080); f.frame(20); f.frame(100);
        take(f, c.goal()); f.destination = c.goal();
        LocalPoint previous = f.controller.position();
        for (int ms = 120; ms <= 1800; ms += 20)
        {
            if (ms == 800) { f.authority = p(8640, 6208); }
            if (ms == 1400) { f.authority = c.goal(); f.destination = null; }
            f.frame(ms);
            LocalPoint actual = f.controller.position();
            assertTrue(MovementPath.distance(previous, actual) <= 9);
            if (ms <= 500) { assertTrue("confirmed eastbound debt survives", actual.getX() >= previous.getX()); assertEquals(6080, actual.getY()); }
            if (!previous.equals(c.goal())) { assertNotEquals("no stop at an adjacent forecast", previous, actual); }
            previous = actual;
        }
        assertEquals(c.goal(), f.controller.position()); f.controller.close();
    }

    private static void take(Fixture f, LocalPoint goal) { MidnightContinuityTest.take(f, 21326, goal.getSceneX(), goal.getSceneY()); }
    private static LocalPoint point(String[] values, int offset) { return p(Integer.parseInt(values[offset]), Integer.parseInt(values[offset + 1])); }
    private static void at(Fixture f, long micros) { f.now = 1_000_000_000L + micros * 1000; f.cycle = 100 + (int) (micros / 20_000); }
}
