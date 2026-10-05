package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementTraceContextTest
{
    private static final ResponsiveMovementConfig RECORDING = new ResponsiveMovementConfig() {
        @Override public boolean recordTrace() { return true; }
    };

    @Test
    public void disabledTracingNeverReadsClientOrCollisionContext()
    {
        Client forbidden = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, args) -> { throw new AssertionError("Unexpected diagnostic read: " + method); });
        MovementTrace trace = new MovementTrace(() -> 0, () -> 0, batch -> { fail("unexpected write"); return false; });
        trace.click(forbidden, new ResponsiveMovementConfig() {}, 0, "WALK", null, null, null);
        trace.close();
    }

    @Test
    public void collisionAndNpcEvidenceRemainExactAfterLiveStateChangesAndWorkerFormatting()
    {
        Fixture f = new Fixture(5952, 8128, 6464, 8384);
        f.flags[47][63] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        f.flags[47][64] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
        f.flags[48][64] = 0x80000000;
        int[][] expected = Arrays.stream(f.flags).map(int[]::clone).toArray(int[][]::new);
        MovementTraceContext context = new MovementTraceContext(10, 2, 30, f.client, RECORDING, 1,
            "NPC_THIRD_OPTION", f.event(MenuAction.NPC_THIRD_OPTION, "Bank\n\"test\""), f.start, null);
        f.flags[47][63] = 0;
        f.flags[48] = null;
        f.npcId = 999;
        f.area = new WorldArea(1, 1, 2, 2, 1);
        String text = CompletableFuture.supplyAsync(context::line).join();
        assertTrue(text.contains("schema=2"));
        assertTrue(text.contains("npcId=1634 npcIndex=25398 npcName=\"Banker\""));
        assertTrue(text.contains("option=\"Bank \\\"test\\\"\""));
        assertTrue(text.contains("npcWorldX=3250 npcWorldY=3265 npcPlane=0 npcWidth=1 npcHeight=1"));
        assertTrue(text.contains("collisionComplete=true"));
        int[][] restored = decodeCollision(text, 104);
        for (int x = 0; x < 104; ++x) { assertArrayEquals("collision column " + x, expected[x], restored[x]); }
        assertTrue(text.endsWith("[RESPONSIVE-MOVEMENT-COLLISION-END] session=10 clickSeq=2" + System.lineSeparator()));
        f.controller.close();
    }

    @Test
    public void missingRaggedAndOversizedMapsAreExplicitAndBounded()
    {
        Fixture f = new Fixture(5952, 8128, 6464, 8384);
        for (int mode = 0; mode < 3; ++mode)
        {
            int[][] flags = mode == 0 ? null : mode == 1 ? new int[][] {null, new int[2]} : new int[200][200];
            Client client = clientWithMap(f, flags, mode == 2 ? 200 : 104);
            MovementTraceContext context = new MovementTraceContext(10, mode, 30, client, RECORDING, 1,
                "WALK", null, f.start, null);
            String text = context.line();
            assertTrue(text.contains("collisionComplete=false"));
            if (mode == 0) { assertTrue(text.contains("collisionWidth=0")); }
            if (mode == 1)
            {
                assertTrue(text.contains("x=0 yStart=0 flagsRle=missing"));
                assertTrue(text.contains("x=1 yStart=0 flagsRle=2:0"));
            }
            if (mode == 2)
            {
                assertTrue(text.contains("collisionWidth=128 collisionHeightLimit=128"));
                assertEquals(128, decodeCollision(text, 128)[127].length);
                assertFalse(text.contains(" x=128 "));
            }
        }
        f.controller.close();
    }

    @Test
    public void realControllerLogsClicksBetweenFramesAndPreservesMovement()
    {
        List<MovementTrace.Entry> entries = new ArrayList<>();
        Fixture logged = new Fixture(5952, 8128, 6848, 8128, entries);
        Fixture baseline = new Fixture(5952, 8128, 6848, 8128);
        logged.click("Bank"); baseline.click("Bank");
        // Two clicks in the same client cycle must both be retained, even without a render between them.
        logged.click("Exchange"); baseline.click("Exchange");
        for (int ms = 20; ms <= 900; ms += 20)
        {
            if (ms == 480)
            {
                logged.authority = baseline.authority = new LocalPoint(6208, 8128, 0);
                logged.destination = baseline.destination = new LocalPoint(6720, 8128, 0);
            }
            logged.frame(ms); baseline.frame(ms);
            assertEquals(baseline.controller.position(), logged.controller.position());
            assertEquals(baseline.controller.orientation(), logged.controller.orientation());
        }
        logged.controller.close(); baseline.controller.close();
        assertEquals(2, entries.stream().filter(e -> e instanceof MovementTraceContext).count());
        MovementTrace.Entry bank = entries.get(1), exchange = entries.get(2);
        assertTrue(bank.line().contains("option=\"Bank\""));
        assertTrue(exchange.line().contains("option=\"Exchange\""));
        assertEquals(bank.sequence + 1, exchange.sequence);
        assertTrue(entries.get(3).line().contains(" clickSeq=" + exchange.sequence + " routeSeq="));
        assertTrue(entries.get(3).line().contains("logical=5952,8128;6080,8128"));
        assertTrue(entries.get(3).line().contains("effectiveSpeed=1.0"));
        assertTrue(entries.get(3).line().contains("deadlineUs=920000")); // First preparation is 20 ms after recording starts.
    }

    @Test
    public void routeSnapshotSurvivesReplacementAndPublishesAgainAtBatchBoundary()
    {
        AtomicLong clock = new AtomicLong();
        List<MovementTrace.Entry> entries = new ArrayList<>();
        MovementTrace trace = new MovementTrace(clock::get, () -> 10, batch -> { entries.addAll(batch); return true; });
        LocalPoint start = new LocalPoint(5952, 5952, 0), goal = new LocalPoint(6464, 6080, 0);
        MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> true);
        trace.enabled(true);
        record(trace, path, 1); record(trace, path, 1); record(trace, path, 2);
        clock.set(1_000_000_000L);
        trace.poll();
        Fixture f = new Fixture(5952, 5952, 6464, 6080);
        trace.click(f.client, RECORDING, 1, "WALK", null, start, null);
        record(trace, path, 3); // Every batch has a self-contained route even after rotation/backpressure.
        trace.close();
        f.controller.close();
        assertEquals(4, entries.size());
        String before = entries.get(0).line();
        assertTrue(before.contains("[RESPONSIVE-MOVEMENT-ROUTE]"));
        assertFalse(entries.get(1).line().contains("[RESPONSIVE-MOVEMENT-ROUTE]"));
        assertTrue(entries.get(3).line().contains("[RESPONSIVE-MOVEMENT-ROUTE]"));
        path.advance(100_000_000L); path.cancel(); path.rebase(128, 256, 200_000_000L);
        assertEquals(before, CompletableFuture.supplyAsync(entries.get(0)::line).join());
    }

    @Test
    public void rapidClicksHaveBoundedBatchesAndDroppedContextHasASequenceGap()
    {
        Fixture f = new Fixture(5952, 8128, 6464, 8384);
        List<MovementTrace.Entry> entries = new ArrayList<>();
        int[] batches = {0};
        MovementTrace trace = new MovementTrace(() -> 0, () -> 10, batch -> {
            assertTrue(batch.size() <= 8);
            if (++batches[0] == 1) { return false; }
            entries.addAll(batch); return true;
        });
        for (int i = 0; i < 17; ++i) { trace.click(f.client, RECORDING, 1, "MINIMAP", null, f.start, f.destination); }
        trace.enabled(false);
        assertEquals(3, batches[0]);
        assertEquals(9, entries.size());
        assertEquals(8, entries.get(0).sequence);
        assertEquals(16, entries.get(8).sequence);
        assertTrue(entries.get(8).line().contains("inputBoundary=\"post-interface\""));
        f.controller.close();
    }

    private static void record(MovementTrace trace, MovementPath path, int cycle)
    {
        trace.record(cycle, 1, path.position(), path.confirmed(), path.confirmed(), path, 0, -1, 0, -1, false,
            path.clickedDestination(), 1, "started", false, "WALK", -1, false, false, -1, false);
    }

    private static Client clientWithMap(Fixture f, int[][] flags, int size)
    {
        CollisionData collision = (CollisionData) Proxy.newProxyInstance(CollisionData.class.getClassLoader(),
            new Class<?>[] {CollisionData.class}, (object, method, args) -> {
                assertEquals("getFlags", method.getName()); return flags;
            });
        WorldView view = (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[] {WorldView.class},
            (object, method, args) -> {
                if (method.getName().equals("getSizeX") || method.getName().equals("getSizeY")) { return size; }
                if (method.getName().equals("getCollisionMaps")) { return new CollisionData[] {collision}; }
                return f.viewCall(method.getName(), args);
            });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (object, method, args) -> method.getName().equals("getWorldView") ? view : f.playerCall(method.getName(), args));
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[] {Client.class},
            (object, method, args) -> method.getName().equals("getLocalPlayer") ? player : f.clientCall(method.getName(), args));
    }

    private static int[][] decodeCollision(String text, int width)
    {
        int[][] result = new int[width][];
        for (String line : text.split("\\R"))
        {
            if (!line.startsWith("[RESPONSIVE-MOVEMENT-COLLISION]")) { continue; }
            int x = Integer.parseInt(line.substring(line.indexOf(" x=") + 3, line.indexOf(" yStart=")));
            List<Integer> column = new ArrayList<>();
            for (String run : line.substring(line.indexOf("flagsRle=") + 9).split(","))
            {
                String[] token = run.split(":");
                int count = Integer.parseInt(token[0]), value = Integer.parseUnsignedInt(token[1], 16);
                for (int i = 0; i < count; ++i) { column.add(value); }
            }
            result[x] = column.stream().mapToInt(Integer::intValue).toArray();
        }
        return result;
    }
}
