package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.responsivemovement.GeApproachReplayTest.tile;
import static org.junit.Assert.*;

/** Talk-to counter parity requires both bank-service evidence and the existing geometry proof. */
public class BankerTalkScopeTest
{
    private static NpcApproach capture(Fixture f, String option)
    {
        return NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_FIRST_OPTION, option), null);
    }

    @Test
    public void talkToBankAndExchangeServicesReusesTheExistingCounterPolicyWithoutNpcIds() throws Exception
    {
        for (String service : new String[] {"Bank", "Exchange"})
        {
            Fixture f = BankerTalkReplayTest.fixture(tile(45, 47));
            f.npcId = 98765;
            f.npcActions = new String[] {null, "Talk-to", " " + service.toLowerCase() + " "};
            NpcApproach approach = capture(f, "<col=ffff00>Talk-to</col>");
            assertTrue(approach.counterBank());
            assertTrue(approach.extendedRangeOption());
            assertFalse(approach.ordinaryOption());
            assertEquals(2, approach.reserveTiles);
            assertEquals(1, f.npcCompositionReads);
            f.controller.close();
        }
    }

    @Test
    public void identicalCounterGeometryDoesNotGiveOrdinaryTalkOrTradeExtendedReach() throws Exception
    {
        for (String[] actions : new String[][] {null, {"Talk-to", "Trade"}, {"Talk-to", "Collect"}})
        {
            Fixture f = BankerTalkReplayTest.fixture(tile(45, 48));
            f.npcActions = actions;
            NpcApproach approach = capture(f, "Talk-to");
            assertFalse(approach.counterBank());
            assertTrue(approach.ordinaryOption());
            assertEquals(1, approach.reserveTiles);
            f.npcActions = new String[] {"Talk-to", "Bank"};
            approach = capture(f, "Trade");
            assertFalse(approach.counterBank());
            assertTrue(approach.ordinaryOption());
            f.controller.close();
        }
    }

    @Test
    public void unsupportedOrAmbiguousServiceGeometryKeepsOrdinaryTalkHandling() throws Exception
    {
        for (int geometry = 0; geometry < 3; ++geometry)
        {
            Fixture f = BankerTalkReplayTest.fixture(tile(45, 48));
            if (geometry == 0) { f.flags[51][49] = 0; }
            if (geometry == 1) { f.flags[51][48] = 0; }
            if (geometry == 2) { f.flags[51][49] |= CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL; }
            NpcApproach approach = capture(f, "Talk-to");
            assertFalse(approach.counterBank());
            assertTrue(approach.ordinaryOption());
            assertEquals(1, approach.reserveTiles);
            f.controller.close();
        }
    }

    @Test
    public void missingCacheEvidenceFailsClosedAndRawCompositionIsASupportedFallback() throws Exception
    {
        Fixture f = BankerTalkReplayTest.fixture(tile(45, 48));
        f.missingNpcComposition = true;
        assertTrue(capture(f, "Talk-to").ordinaryOption());
        f.missingNpcComposition = false; f.untransformedNpc = true;
        assertTrue(capture(f, "Talk-to").counterBank());
        f.controller.close();
    }

    @Test
    public void serviceActionsAreCapturedOnceAndDoNotRenewAnUnconfirmedPrediction() throws Exception
    {
        Fixture f = BankerTalkReplayTest.fixture(tile(45, 47));
        f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to"));
        assertEquals(1, f.npcCompositionReads);
        for (int ms = 0; ms <= 300; ms += 20) { f.frame(ms); }
        assertNotEquals(f.start, f.controller.position());
        f.npcActions = new String[] {"Talk-to", "Trade"};
        for (int ms = 320; ms <= 3500; ms += 20) { f.frame(ms); }
        assertEquals("no new budget or cache-following loop", f.start, f.controller.position());
        assertEquals(1, f.npcCompositionReads);
        f.controller.close();
    }

    @Test
    public void talkCounterStartsStillRespectEligibilityAndAlreadyInRangeClicks() throws Exception
    {
        for (int gate = 0; gate < 5; ++gate)
        {
            LocalPoint start = gate == 4 ? tile(49, 48) : tile(45, 48);
            Fixture f = BankerTalkReplayTest.fixture(start);
            if (gate == 0) { f.starts = false; }
            if (gate == 1) { f.control = true; }
            if (gate == 2) { f.animation = 123; }
            if (gate == 3) { f.spot = true; }
            f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to"));
            for (int ms = 0; ms <= 400; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }
}
