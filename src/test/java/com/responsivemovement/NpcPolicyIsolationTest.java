package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.function.BiPredicate;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Banker extended-range policy must not become the generic NPC policy. */
public class NpcPolicyIsolationTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void onlyBankAndExchangeReceiveTheExtendedReserveAndArrivalExceptions()
    {
        Fixture f = new Fixture(5952, 8384, 6592, 8384);
        for (String option : new String[] {"Talk-to", "Trade", "Collect", "Bank", "<col=ffff00>Exchange</col>"})
        {
            NpcApproach npc = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, option), null);
            boolean extended = option.contains("Bank") || option.contains("Exchange");
            assertEquals(extended ? 2 : 1, npc.reserveTiles);
            assertEquals(extended, npc.extendedRangeOption());
            assertNull("ordinary open-ground geometry is not a counter", npc.counterRun(f.start, OPEN));
        }
        f.controller.close();
    }

    @Test
    public void ordinaryTwoTileTalkAndTradeStartsTravelNormallyToAdjacency()
    {
        for (String option : new String[] {"Talk-to", "Trade"})
        {
            Fixture f = new Fixture(6336, 8384, 6592, 8384);
            f.click(option); f.frame(0);
            f.frame(100);
            assertEquals(new LocalPoint(6356, 8384, 0), f.controller.position());
            f.authority = new LocalPoint(6464, 8384, 0);
            for (int ms = 120; ms <= 1000; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void ordinaryNpcRefinementKeepsItsClockAndDoesNotRenewEitherDeadline()
    {
        MovementPath path = MovementPath.anticipate(p(10, 10), p(10, 10), p(13, 10), true,
            0, 1.2, MovementPath.freshDeadline(0), OPEN, false, p(14, 11), p(14, 11), 1);
        path.advance(350_000_000L);
        long hardDeadline = path.predictionDeadlineNanos();
        MovementPath next = path.retargetApproachDestination(p(13, 11), true, 350_000_000L);
        assertNotNull(next);
        assertEquals(path.position(), next.position());
        assertEquals(hardDeadline, next.predictionDeadlineNanos());
        next.advance(370_000_000L);
        assertTrue("no construction-anchor retrace", next.position().getX() >= path.position().getX());
        assertTrue(next.clear());
        next.advance(901_000_000L);
        assertEquals("original response timeout", "recovery", next.phase());
        for (int ms = 921; ms <= 2201; ms += 20) { next.advance(ms * 1_000_000L); }
        assertEquals("unconfirmed refinement still times out", p(10, 10), next.position());
    }

    @Test
    public void movingNpcClickCannotRearmAHardDeadlineWithoutAuthority()
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(18, 12).getX(), p(18, 12).getY());
        NpcApproach npc = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Trade"), null);
        MovementPath path = MovementPath.anticipate(f.start, f.start, p(18, 10), true, 0, 1.2,
            MovementPath.freshDeadline(0), OPEN);
        path.advance(200_000_000L);
        long hard = path.predictionDeadlineNanos();
        path.replacement(true);
        MovementPath next = path.retargetNpcApproach(npc, null, true, 200_000_000L);
        assertNotNull(next);
        assertEquals(path.position(), next.position());
        assertEquals(hard, next.predictionDeadlineNanos());
        assertNull(next.counterNativeGoal());
        next.advance(300_000_000L);
        next.replacement(true);
        MovementPath repeat = next.retargetNpcApproach(npc, null, true, 300_000_000L);
        assertNotNull(repeat);
        assertEquals(hard, repeat.predictionDeadlineNanos());
        assertNull(repeat.retargetNpcApproach(npc, null, true, hard));
        for (int ms = 320; ms <= 3000; ms += 20) { repeat.advance(ms * 1_000_000L); }
        assertEquals(f.start, repeat.position());
        f.controller.close();
    }

    @Test
    public void movingNpcReplanCannotDropAConfirmedPrefixOrUseAnUnavailableConnector()
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(18, 12).getX(), p(18, 12).getY());
        NpcApproach npc = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Trade"), null);
        MovementPath confirmed = MovementPath.idle(f.start, 0, 1.2, OPEN);
        assertTrue(confirmed.accept(p(12, 10), true));
        confirmed.advance(100_000_000L);
        assertNull(confirmed.retargetNpcApproach(npc, null, true, 100_000_000L));
        boolean[] open = {true};
        MovementPath preview = MovementPath.anticipate(f.start, f.start, p(18, 10), true, 0, 1.2,
            MovementPath.freshDeadline(0), (a, b) -> open[0]);
        preview.advance(100_000_000L);
        LocalPoint fraction = preview.position();
        open[0] = false;
        assertNull(preview.retargetNpcApproach(npc, null, true, 100_000_000L));
        assertEquals(fraction, preview.position());
        f.controller.close();
    }
}
