package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit Follow input and native engagement doubles; no simulated game input. */
public class FollowContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }
    private static Fixture fixture(int x, int y, int targetX, int targetY)
    {
        Fixture f = new Fixture(x, y, targetX, targetY);
        f.originalWhenAligned = false; f.frame(0);
        return f;
    }
    private static void follow(Fixture f, boolean npc)
    {
        f.controller.worldInteraction(f.event(npc ? MenuAction.NPC_THIRD_OPTION : MenuAction.PLAYER_THIRD_OPTION, "Follow"));
        f.interacting = npc ? f.npc : f.followedPlayer;
    }

    @Test
    public void recordedFollowAlternatingEndpointsRetainNativeMotionRatherThanRunIdlePulses()
    {
        // 20:43:18.079 (+11), session 1791193390639, click 373. Native engagement
        // is an explicit double: older samples do not record player target state.
        for (boolean npc : new boolean[] {false, true})
        {
            for (int cadence : new int[] {8, 20, 33})
            {
                Fixture f = fixture(6208, 8000, 5952, 8384);
                f.speed = 1.1; f.smoothing = 0;
                follow(f, npc);
                f.frame(0);
                assertTrue("aligned explicit Follow hands off before movement", f.controller.nativeVisible());
                for (int ms = cadence; ms <= 10000; ms += cadence)
                {
                    if (ms >= 200)
                    {
                        int leg = Math.max(0, (ms - 799) / 600);
                        f.authority = ms < 799 ? p(5952, 8256) : p(5952, leg % 2 == 0 ? 8384 : 8256);
                        // Native fractions copied from representative reversal samples.
                        f.nativePoint = ms < 799 ? p(6008, 8199) : p(5952, leg % 2 == 0 ? 8298 : 8373);
                    }
                    f.frame(ms);
                    assertTrue("keep native ownership during moving Follow", f.controller.nativeVisible());
                    assertEquals(f.nativePoint == null ? f.start : f.nativePoint, f.controller.position());
                }
                f.controller.close(); assertTrue(f.objects.isEmpty());
            }
        }
    }

    @Test
    public void movingFollowPreservesFractionAndConfirmedPrefixUntilAlignedHandoff()
    {
        for (boolean npc : new boolean[] {false, true})
        {
            Fixture f = fixture(6208, 8000, 5952, 8384);
            f.authority = p(6208, 8256); f.frame(20); f.frame(120);
            LocalPoint before = f.controller.position();
            follow(f, npc); f.frame(140);
            assertFalse(f.controller.nativeVisible());
            assertEquals(6208, f.controller.position().getX());
            assertTrue(f.controller.position().getY() >= before.getY());
            assertTrue(MovementPath.distance(before, f.controller.position()) <= 8);
            // Native actor settles at the same confirmed endpoint; facing also agrees.
            f.nativePoint = f.authority; f.nativeOrientation = 1024;
            for (int ms = 160; ms <= 900; ms += 20) { f.frame(ms); }
            assertEquals(f.authority, f.controller.position());
            assertTrue(f.controller.nativeVisible());
            f.authority = p(6336, 8256); f.nativePoint = p(6212, 8256); f.frame(920);
            assertEquals(f.nativePoint, f.controller.position());
            assertTrue(f.controller.nativeVisible());
            f.controller.close();
        }
    }

    @Test
    public void walkAndOtherInteractionReleaseNativeFollowImmediately()
    {
        for (boolean walk : new boolean[] {false, true})
        {
            Fixture f = fixture(6208, 8000, 6848, 8000);
            follow(f, false); f.frame(0); assertTrue(f.controller.nativeVisible());
            f.at(20);
            if (walk) { f.controller.walkClick(); }
            else { f.click("Talk-to"); }
            f.destination = p(6464, 8000);
            for (int ms = 40; ms <= 200; ms += 20) { f.frame(ms); }
            assertFalse("new input owns presentation even with a stale native target", f.controller.nativeVisible());
            assertTrue(f.controller.position().getX() > f.start.getX());
            f.controller.close();
        }
    }

    @Test
    public void followDoesNotInventNpcApproachWithoutEngagementOrDestination()
    {
        Fixture f = fixture(6208, 8000, 6848, 8000);
        f.click("Follow");
        for (int ms = 0; ms <= 2000; ms += 20)
        {
            f.frame(ms); assertEquals(f.start, f.controller.position());
        }
        f.interacting = f.npc; f.frame(2020);
        assertFalse("expired Follow cannot reclaim presentation", f.controller.nativeVisible());
        f.controller.close();
    }

    @Test
    public void followOwnershipEndsOnLostEngagementTargetAndSceneChanges()
    {
        for (int change = 0; change < 5; ++change)
        {
            Fixture f = fixture(6208, 8000, 6336, 8000);
            follow(f, true); f.frame(0); assertTrue(f.controller.nativeVisible());
            if (change == 0) { f.interacting = null; }
            if (change == 1) { f.present = false; }
            if (change == 2) { f.npcId++; }
            if (change == 3) { f.npcDead = true; }
            if (change == 4) { f.controller.sceneChanged(false); }
            f.frame(20); f.frame(40);
            assertFalse("Follow owner retired: " + change, f.controller.nativeVisible());
            f.controller.close(); assertTrue(f.objects.isEmpty());
        }
    }

    @Test
    public void attackTradeTalkToAndObjectOptionsDoNotAcquireFollowPresentation()
    {
        for (String option : new String[] {"Attack", "Trade", "Talk-to", "Bank"})
        {
            Fixture f = fixture(6208, 8000, 6848, 8000);
            f.interacting = f.npc; f.click(option); f.frame(0); f.frame(100);
            assertFalse(option, f.controller.nativeVisible()); f.controller.close();
        }
        Fixture f = fixture(6208, 8000, 6848, 8000);
        f.controller.worldInteraction(f.sceneEvent(MenuAction.GAME_OBJECT_FIRST_OPTION, "Follow", 99, 48, 62));
        f.interacting = f.npc; f.frame(20);
        assertFalse(f.controller.nativeVisible()); f.controller.close();
    }

    @Test
    public void lateNativeEngagementCanHandOffAtTheFirstStepWithoutAnApproachSnap()
    {
        Fixture f = fixture(6208, 8000, 5952, 8384);
        f.controller.worldInteraction(f.event(MenuAction.PLAYER_THIRD_OPTION, "Follow"));
        for (int ms = 0; ms < 200; ms += 20)
        {
            f.frame(ms); assertFalse(f.controller.nativeVisible()); assertEquals(f.start, f.controller.position());
        }
        f.interacting = f.followedPlayer; f.authority = p(5952, 8256);
        f.frame(200);
        assertTrue(f.controller.nativeVisible());
        assertEquals("native movement begins at the same displayed position", f.start, f.controller.position());
        f.nativePoint = p(6199, 8008); f.frame(220);
        assertEquals(f.nativePoint, f.controller.position()); f.controller.close();
    }

    @Test
    public void repeatedFollowRetainsNativeFractionAndDoesNotRenewRejectedClickLifetime()
    {
        Fixture f = fixture(6208, 8000, 5952, 8384);
        follow(f, false); f.frame(0);
        f.authority = p(5952, 8256); f.nativePoint = p(6199, 8008); f.frame(200);
        follow(f, false); f.frame(220);
        assertTrue(f.controller.nativeVisible()); assertEquals(f.nativePoint, f.controller.position());
        f.controller.close();

        f = fixture(6208, 8000, 6848, 8000);
        f.click("Follow"); f.frame(1700); f.click("Follow"); f.frame(1800);
        f.interacting = f.npc; f.frame(1820);
        assertFalse("same pending Follow retains its original lifetime", f.controller.nativeVisible());
        assertEquals(f.start, f.controller.position()); f.controller.close();
    }

    @Test
    public void nativeHandoffWaitsForPositionAndCappedFacingEvenWhenAlignedOptionIsOff()
    {
        Fixture f = fixture(6208, 8000, 6336, 8000);
        f.nativeOrientation = 512;
        follow(f, false); f.frame(20);
        assertFalse("no sudden native turn", f.controller.nativeVisible());
        assertTrue(Math.abs(MotionMath.difference(0, f.controller.orientation())) <= 36);
        for (int ms = 40; ms <= 400; ms += 20) { f.frame(ms); }
        assertTrue(f.controller.nativeVisible()); f.controller.close();

        f = fixture(6208, 8000, 6336, 8000);
        follow(f, false); f.nativePoint = p(6200, 8000); f.frame(20);
        assertFalse("no position tolerance snap", f.controller.nativeVisible());
        assertEquals(f.start, f.controller.position());
        f.nativePoint = f.start; f.frame(40);
        assertTrue(f.controller.nativeVisible()); f.controller.close();
    }

    @Test
    public void walkFromMovingNativeFollowAdoptsTheNativeFractionThroughTheCheckedPath()
    {
        Fixture f = fixture(6208, 8000, 6848, 8000);
        follow(f, false); f.frame(0);
        f.authority = p(6464, 8000); f.nativePoint = p(6252, 8000); f.frame(100);
        LocalPoint before = f.controller.position();
        f.controller.walkClick(); f.destination = p(6720, 8000); f.frame(120);
        assertFalse(f.controller.nativeVisible());
        assertEquals("native adoption spends no additional first-frame clock", before, f.controller.position());
        f.frame(140);
        assertTrue(f.controller.position().getX() > before.getX());
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 8);
        f.controller.close();
    }

    @Test
    public void despawnedPlayersAndDifferentNativeTargetsCannotRetainFollow()
    {
        for (boolean despawn : new boolean[] {false, true})
        {
            Fixture f = fixture(6208, 8000, 6336, 8000);
            follow(f, false); f.frame(0);
            if (despawn) { f.present = false; } else { f.interacting = f.npc; }
            f.frame(20); assertFalse(f.controller.nativeVisible()); f.controller.close();
        }
    }

    @Test
    public void nativeFollowRestoresSelectorsAndHidesCustomBodyAndEffects()
    {
        Fixture f = fixture(6208, 8000, 6336, 8000);
        String[] keys = {"IdlePoseAnimation", "WalkAnimation", "RunAnimation", "IdleRotateLeft", "IdleRotateRight",
            "WalkRotateLeft", "WalkRotateRight", "WalkRotate180"};
        for (int i = 0; i < keys.length; ++i) { f.poses.put(keys[i], 800 + i); }
        f.frame(20);
        follow(f, false); f.frame(40);
        assertTrue(f.controller.nativeVisible());
        for (int i = 0; i < keys.length; ++i) { assertEquals(800 + i, (int) f.poses.get(keys[i])); }
        assertTrue(f.objects.stream().allMatch(object -> object.getModel() == null));
        int builds = f.modelBuilds;
        f.spot = true; f.animation = 123;
        f.authority = p(6336, 8000); f.nativePoint = p(6212, 8000); f.frame(60);
        assertTrue(f.controller.nativeVisible()); assertEquals(f.nativePoint, f.controller.position());
        assertEquals("native Follow does not prepare a second posed body", builds, f.modelBuilds);
        assertEquals(123, f.animation);
        f.controller.close(); assertTrue(f.objects.isEmpty());
    }
}
