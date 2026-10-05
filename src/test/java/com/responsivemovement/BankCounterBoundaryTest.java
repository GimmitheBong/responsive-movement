package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static com.responsivemovement.GeApproachReplayTest.collision;
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** The 13:10 corner clicks perform no native movement; do not invent an approach and timeout return. */
public class BankCounterBoundaryTest
{
    @Test
    public void capturedBankAndServiceTalkCornersRemainStationaryThroughoutEachRecording() throws Exception
    {
        for (String line : data())
        {
            if (!line.startsWith("C ")) { continue; }
            String[] fields = line.split(" ");
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                LocalPoint at = tile(Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
                Fixture f = fixture(at);
                f.controller.worldInteraction(f.event("Talk-to".equals(fields[2]) ? MenuAction.NPC_FIRST_OPTION :
                    MenuAction.NPC_THIRD_OPTION, fields[2]));
                long end = Long.parseLong(fields[5]);
                for (long t = 0; t <= end; t += cadence)
                {
                    frame(f, t);
                    assertEquals("click " + fields[1] + " at " + t + "us", at, f.controller.position());
                    assertEquals(at, f.authority);
                    assertNull(f.destination);
                }
                f.controller.close();
            }
        }
    }

    @Test
    public void capturedNeighbourOutsideTheBoundaryStillCompletesItsCheckedPairAtRunPace() throws Exception
    {
        for (String line : data())
        {
            if (!line.startsWith("P ")) { continue; }
            String[] fields = line.split(" ");
            LocalPoint start = tile(Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
            LocalPoint goal = tile(Integer.parseInt(fields[6]), Integer.parseInt(fields[7]));
            long authorityAge = Long.parseLong(fields[5]);
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = fixture(start);
                f.nativePoint = new LocalPoint(6296, 8216, 0); // Recorded native fraction at click 1116.
                f.click(fields[2]);
                TreeSet<Long> times = new TreeSet<>();
                for (long t = 0; t <= 1_800_000; t += cadence) { times.add(t); }
                times.add(authorityAge);
                for (long t : times)
                {
                    if (t >= authorityAge) { f.authority = goal; }
                    frame(f, t);
                    double fraction = Math.min(1, t * 0.00044 / 256);
                    LocalPoint expected = new LocalPoint((int) Math.round(start.getX() + 256 * fraction),
                        (int) Math.round(start.getY() + 256 * fraction), 0);
                    assertTrue("normal pace at " + t + "us", MovementPath.distance(expected, f.controller.position()) <= 1);
                }
                assertEquals(goal, f.controller.position());
                f.controller.close();
            }
        }
    }

    @Test
    public void bankExchangeAndServiceTalkCornerGateIsGeometricAcrossCounterOrientations()
    {
        int[][] sides = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] open : sides)
        {
            for (int dx : new int[] {-2, 2})
            {
                for (int dy : new int[] {-2, 2})
                {
                    for (String option : new String[] {"Bank", "Exchange", "Talk-to"})
                    {
                        LocalPoint start = tile(52 + dx, 52 + dy), npc = tile(52, 52);
                        Fixture f = new Fixture(start.getX(), start.getY(), npc.getX(), npc.getY());
                        f.npcId = 98765;
                        f.npcActions = new String[] {"Talk-to", "Exchange"};
                        f.flags[52][52] = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR;
                        for (int[] side : sides)
                        {
                            if (!Arrays.equals(open, side))
                            {
                                f.flags[52 + side[0]][52 + side[1]] = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR;
                            }
                        }
                        NpcApproach target = capture(f, option);
                        assertTrue(target.atCounterStartBoundary(start));
                        assertNull(target.counterRun(start, collision(f)));
                        assertFalse(target.atCounterStartBoundary(tile(52 + dx * 2, 52 + dy * 2)));
                        f.click(option);
                        for (int ms = 0; ms <= 1400; ms += 20) { f.frame(ms); assertEquals(start, f.controller.position()); }
                        f.controller.close();
                    }
                }
            }
        }
    }

    @Test
    public void ordinaryTalkTradeAndAttackRetainTheirAdjacentApproachAtTheSameCorner() throws Exception
    {
        for (String option : new String[] {"Talk-to", "Trade", "Attack"})
        {
            Fixture f = fixture(tile(49, 63));
            f.npcActions = new String[] {"Talk-to", "Trade"};
            NpcApproach target = capture(f, option);
            assertFalse(target.atCounterStartBoundary(f.start));
            assertFalse(target.counterBank());
            assertEquals(1, target.reserveTiles);
            f.click(option); f.frame(0); f.frame(100);
            assertNotEquals("ordinary " + option + " must still approach", f.start, f.controller.position());
            f.controller.close();
        }
        // Trade on a banker is also ordinary: the service's different option
        // cannot inherit the new Bank/Exchange/Talk-to start boundary.
        Fixture f = fixture(tile(49, 63));
        assertFalse(capture(f, "Trade").atCounterStartBoundary(f.start));
        f.click("Trade"); f.frame(0); f.frame(100);
        assertNotEquals(f.start, f.controller.position());
        f.missingNpcComposition = true;
        assertFalse(capture(f, "Talk-to").atCounterStartBoundary(f.start));
        f.area = new WorldArea(3251, 3265, 2, 2, 0);
        assertFalse(capture(f, "Bank").atCounterStartBoundary(f.start));
        f.controller.close();
    }

    @Test
    public void aCloserFlagCannotManufactureMovementButNewServerAuthorityStillMovesNormally() throws Exception
    {
        Fixture f = fixture(tile(49, 63));
        f.click("Bank"); f.frame(0);
        f.destination = tile(50, 65);
        for (int ms = 20; ms <= 280; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        f.authority = tile(50, 65); // Actual checked native run is still accepted.
        f.destination = null;
        for (int ms = 300; ms <= 1000; ms += 20)
        {
            f.frame(ms);
            double fraction = Math.min(1, (ms - 280) * 0.44 / 256);
            LocalPoint expected = new LocalPoint((int) Math.round(f.start.getX() + 128 * fraction),
                (int) Math.round(f.start.getY() + 256 * fraction), 0);
            assertTrue("confirmed pace at " + ms, MovementPath.distance(expected, f.controller.position()) <= 1);
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void freshBoundaryClickDropsOnlyTheSpeculativeTailAndFinishesConfirmedMovement() throws Exception
    {
        Fixture f = fixture(tile(47, 63));
        f.controller.walkClick(); f.destination = tile(54, 63); f.frame(0);
        for (int ms = 20; ms <= 100; ms += 20) { f.frame(ms); }
        f.authority = tile(49, 63); f.frame(120);
        LocalPoint before = f.controller.position();
        assertTrue(before.getX() < f.authority.getX());
        f.click("Bank"); f.destination = null;
        for (int ms = 140; ms <= 1800; ms += 20)
        {
            f.frame(ms);
            LocalPoint at = f.controller.position();
            assertTrue("keep confirmed progress", at.getX() >= before.getX());
            assertTrue("no old speculative tail", at.getX() <= f.authority.getX());
            assertEquals(f.authority.getY(), at.getY());
            before = at;
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void replacingAnAheadOfAuthorityWalkPreviewRecoversWithoutStartingAnotherBankTrip() throws Exception
    {
        Fixture f = fixture(tile(49, 63));
        f.controller.walkClick(); f.destination = tile(49, 61); f.frame(0);
        for (int ms = 20; ms <= 200; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        assertTrue(before.getY() < f.authority.getY());
        f.click("Bank"); f.destination = null;
        for (int ms = 220; ms <= 2000; ms += 20)
        {
            f.frame(ms);
            LocalPoint at = f.controller.position();
            assertTrue(at.getY() >= before.getY());
            assertTrue("never preview past the in-range authority", at.getY() <= f.authority.getY());
            assertEquals(f.authority.getX(), at.getX());
            before = at;
        }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void aContinuingCounterRunKeepsItsPairAlignedStopWhenItPassesTheCorner() throws Exception
    {
        Fixture f = fixture(tile(49, 61));
        f.click("Bank"); f.frame(0);
        for (int ms = 20; ms <= 1600; ms += 20)
        {
            if (ms == 200) { f.authority = tile(49, 63); f.destination = tile(50, 65); }
            if (ms == 860) { f.authority = tile(50, 65); f.destination = null; }
            f.frame(ms);
            double distance = ms * 0.44;
            double tail = Math.max(0, Math.min(1, (distance - 256) / 256));
            LocalPoint expected = new LocalPoint((int) Math.round(f.start.getX() + 128 * tail),
                (int) Math.round(f.start.getY() + Math.min(256, distance) + 256 * tail), 0);
            assertTrue("retain the original whole-pair stop at " + ms,
                MovementPath.distance(expected, f.controller.position()) <= 1);
        }
        assertEquals(tile(50, 65), f.controller.position());
        f.controller.close();
    }

    private static NpcApproach capture(Fixture f, String option)
    {
        return NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_FIRST_OPTION, option), null);
    }

    static Fixture fixture(LocalPoint start) throws Exception
    {
        Fixture f = new Fixture(start.getX(), start.getY(), tile(51, 65).getX(), tile(51, 65).getY());
        f.npcId = 1633; f.npcActions = new String[] {"Talk-to", null, "Bank"};
        f.speed = 1.1; f.smoothing = 0; f.nativePoint = start;
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (String line : data())
        {
            if (!line.startsWith("M ")) { continue; }
            String[] fields = line.split(" ");
            int x = Integer.parseInt(fields[1]), y = 0;
            for (String run : fields[2].split(","))
            {
                String[] values = run.split(":");
                int length = Integer.parseInt(values[0]), flag = Integer.parseUnsignedInt(values[1], 16);
                for (int end = y + length; y < end; ++y) { f.flags[x][y] = flag; }
            }
            assertEquals("complete copied column " + x, 104, y);
        }
        return f;
    }

    private static void frame(Fixture f, long micros)
    {
        f.now = 1_000_000_000L + micros * 1000;
        f.cycle = 100 + (int) (micros / 20_000);
        f.controller.update();
    }

    private static List<String> data() throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            BankCounterBoundaryTest.class.getResourceAsStream("banker-corner-1310.txt"), StandardCharsets.UTF_8)))
        {
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isEmpty()).collect(Collectors.toList());
        }
    }
}
