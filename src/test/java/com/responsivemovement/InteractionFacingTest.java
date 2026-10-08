package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real controller, checked open routes and synthetic native timing; no rendered-bone or game-input assertions. */
public class InteractionFacingTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    private static Fixture approach(boolean enabled, boolean object, boolean large)
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(14, 11).getX(), p(14, 11).getY());
        f.faceInteractionsOnArrival = enabled;
        f.nativeOrientation = 1536;
        f.nativePoint = f.start;
        if (object)
        {
            f.object(123, p(14, 11), large ? p(16, 13) : p(14, 11));
            f.flags[14][11] = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
            f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down", 123, 14, 11));
        }
        else { f.click("Talk-to"); }
        f.destination = p(14, 10);
        return f;
    }

    private static void frame(Fixture f, int ms)
    {
        if (ms >= 600) { f.authority = p(14, 10); }
        else if (ms >= 300) { f.authority = p(12, 10); }
        f.frame(ms);
    }

    @Test
    public void npcTurnsOnTheDisplayedArrivalFrameAtAllCadencesBeforeNativeArrival()
    {
        for (int cadence : new int[] {8, 20, 33})
        {
            Fixture f = approach(true, false, false);
            boolean reached = false;
            for (int ms = 0; ms <= 1650; ms += cadence)
            {
                int before = f.controller.orientation();
                frame(f, ms);
                assertTrue("one turn budget", Math.abs(MotionMath.difference(before, f.controller.orientation())) <=
                    Math.ceil(30 * cadence / 16.667) + 1);
                if (f.controller.position().equals(p(14, 10)))
                {
                    if (!reached) { assertNotEquals("turn begins on arrival, not the next native tick", 1536, f.controller.orientation()); }
                    reached = true;
                    assertFalse("do not transfer back to the stale native facing", f.controller.nativeVisible());
                }
                else if (ms >= 400) { assertEquals("keep travel facing before arrival", 1536, f.controller.orientation()); }
            }
            assertTrue(reached);
            assertEquals(1024, f.controller.orientation());
            assertEquals(f.start, f.nativePoint);
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void disabledOptionKeepsTheExistingNativeArrivalWait()
    {
        assertFalse(new ResponsiveMovementConfig() {}.faceInteractionsOnArrival());
        Fixture f = approach(false, false, false);
        for (int ms = 0; ms <= 1650; ms += 20) { frame(f, ms); }
        assertEquals(p(14, 10), f.controller.position());
        assertEquals(1536, f.controller.orientation());
        f.controller.close();
    }

    @Test
    public void singleTileAndRectangularObjectsFaceTheirCapturedGeometry()
    {
        for (boolean large : new boolean[] {false, true})
        {
            Fixture f = approach(true, true, large);
            for (int ms = 0; ms <= 1800; ms += 20) { frame(f, ms); }
            assertEquals(p(14, 10), f.controller.position());
            assertEquals(large ? MotionMath.heading(128, 256) : 1024, f.controller.orientation());
            f.controller.close();
        }
    }

    @Test
    public void nativeFlagWithdrawalAndPrimaryActionDoNotLoseThePendingArrivalTurn()
    {
        Fixture f = approach(true, false, false);
        for (int ms = 0; ms <= 1800; ms += 20)
        {
            if (ms >= 600) { f.destination = null; f.animation = 1234; }
            frame(f, ms);
            assertEquals(ms >= 600 ? 1234 : -1, f.animation);
        }
        assertEquals(1024, f.controller.orientation());
        assertEquals(p(14, 10), f.controller.position());
        f.controller.close();
    }

    @Test
    public void matchingNativeFacingHandsBackOnlyAfterTheDisplayedTurnFinishes()
    {
        Fixture f = approach(true, false, false);
        for (int ms = 0; ms <= 1800; ms += 20)
        {
            if (ms >= 1300) { f.nativePoint = p(14, 10); f.nativeOrientation = 1024; f.destination = null; }
            frame(f, ms);
            if (ms == 1300) { assertFalse(f.controller.nativeVisible()); }
        }
        assertEquals(1024, f.controller.orientation());
        assertTrue(f.controller.nativeVisible());
        f.controller.close();
    }

    @Test
    public void replacementInvalidationToggleAndNativeLocationActionsReleaseFacing()
    {
        for (int change = 0; change < 6; ++change)
        {
            Fixture f = approach(true, false, false);
            for (int ms = 0; ms <= 1320; ms += 20) { frame(f, ms); }
            assertNotEquals(1536, f.controller.orientation());
            int beforeReplacement = f.controller.orientation();
            if (change == 0) { f.controller.walkClick(); }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.npcId++; }
            if (change == 3) { f.faceInteractionsOnArrival = false; }
            if (change == 4) { f.controller.sceneChanged(false); }
            if (change == 5) { f.animation = AnimationID.HUMAN_CASTTELEPORT; }
            f.destination = null; f.nativePoint = f.authority;
            if (change == 0)
            {
                f.frame(1340);
                assertEquals("Walk holds its current facing instead of completing the old target turn",
                    beforeReplacement, f.controller.orientation());
                f.controller.close(); assertTrue(f.objects.isEmpty());
                continue;
            }
            for (int ms = 1340; ms <= 2500; ms += 20) { f.frame(ms); }
            assertEquals("release old target for change " + change, 1536, f.controller.orientation());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void distantPredictionExhaustionDoesNotCountAsArrival()
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(25, 11).getX(), p(25, 11).getY());
        f.faceInteractionsOnArrival = true; f.nativeOrientation = 1536;
        f.click("Talk-to"); f.destination = p(25, 10);
        for (int ms = 0; ms <= 880; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.position().getX() < p(25, 10).getX());
        assertEquals(1536, f.controller.orientation());
        for (int ms = 900; ms <= 2500; ms += 20) { f.frame(ms); }
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void groundItemsAndExplicitFollowDoNotAcquireInteractionFacing()
    {
        Fixture f = approach(true, false, false);
        assertNull(InteractionFacing.capture(f.view,
            f.sceneEvent(MenuAction.GROUND_ITEM_FIRST_OPTION, "Take", 123, 14, 11), null, null, f.now));
        assertNull(InteractionFacing.capture(f.view, f.event(MenuAction.NPC_THIRD_OPTION, "Follow"), null, null, f.now));
        f.controller.close();
    }
}
