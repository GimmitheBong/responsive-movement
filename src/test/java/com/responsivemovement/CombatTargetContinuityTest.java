package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit input ownership, native Ranging cache styles and shot-boundary continuity. */
public class CombatTargetContinuityTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static Fixture bow(int x, int y, int nx, int ny)
    {
        Fixture f = new Fixture(p(x, y).getX(), p(x, y).getY(), p(nx, ny).getX(), p(nx, ny).getY());
        f.ranged("Magic shortbow (i)", 12788, 1); f.smoothing = 0;
        return f;
    }

    @Test
    public void nativeRangingStructsIdentifyAccurateRapidAndLongrangeWithoutTreatingThemAsMelee()
    {
        Fixture f = bow(10, 10, 22, 10);
        for (int style : new int[] {0, 1, 2})
        {
            f.attackStyle = style;
            CombatApproach profile = CombatApproach.capture(f.client);
            assertFalse(profile.adjacentMelee);
            assertEquals(style == 2 ? 9 : 7, profile.reserveTiles);
        }
        f.controller.close();
    }

    @Test
    public void aStationaryBowClickOwnsFacingBeforeTheTickAndAcrossNativeShotsAndTargetSteps()
    {
        Fixture f = bow(10, 10, 12, 10);
        f.nativeOrientation = 1024;
        f.click("Attack");
        for (int ms = 0; ms <= 500; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
        assertEquals("face east before any native shot/engagement", 1536, f.controller.orientation());
        f.interacting = f.npc; f.animation = 426;
        f.area = new WorldArea(3212, 3211, 1, 1, 0);
        f.npcNativePoint = new LocalPoint(p(12, 11).getX() - 40, p(12, 11).getY(), 0);
        for (int ms = 520; ms <= 1400; ms += 20)
        {
            if (ms == 900) { f.animation = -1; }
            f.frame(ms); assertEquals(f.start, f.controller.position());
            assertFalse("native idle must not steal target facing", f.controller.nativeVisible());
        }
        assertEquals(MotionMath.heading(216, 128), f.controller.orientation());
        f.controller.close();
    }

    @Test
    public void aPendingMeleeClickAlreadyBesideItsTargetTurnsBeforeNativeEngagement()
    {
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(11, 10).getX(), p(11, 10).getY());
        f.click("Attack");
        for (int ms = 0; ms <= 400; ms += 20) { f.frame(ms); }
        assertEquals(1536, f.controller.orientation());
        assertEquals(f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void bowApproachKeepsNormalRunPairsToTheFiringBoundaryWithoutIntermediateIdle()
    {
        Fixture f = bow(10, 10, 22, 10);
        f.speed = 1.1; f.click("Attack"); f.frame(0);
        for (int ms = 20; ms <= 2200; ms += 20)
        {
            if (ms == 200) { f.authority = p(12, 10); f.destination = p(21, 10); f.interacting = f.npc; }
            if (ms == 800) { f.authority = p(14, 10); }
            if (ms == 1400) { f.authority = p(16, 10); f.destination = null; f.animation = 426; }
            LocalPoint before = f.controller.position(); f.frame(ms);
            assertEquals(Math.min(p(16, 10).getX(), Math.round(f.start.getX() + ms * 0.44)), f.controller.position().getX());
            assertTrue(MovementPath.distance(before, f.controller.position()) <= 9);
        }
        assertEquals(p(16, 10), f.controller.position());
        assertEquals(1536, f.controller.orientation());
        assertEquals(426, f.animation);
        f.controller.close();
    }

    @Test
    public void walkDuringAnOwnedMeleePoseStartsAtTheNativePublicationAndKeepsItsForecastThroughDamage()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(11, 10).getX(), p(11, 10).getY(), trace);
        f.speed = 1.1; f.smoothing = 0; f.interacting = f.npc;
        f.click("Attack"); f.frame(0); f.frame(400);
        f.animation = 1658; f.nativeOrientation = 1536;
        f.at(400); f.controller.walkClick(); f.destination = p(5, 10);
        f.frame(420); f.frame(440);
        assertTrue("Walk must start during the pose", f.controller.position().getX() < f.start.getX());
        for (int ms = 460; ms <= 2400; ms += 20)
        {
            if (ms == 600) { f.authority = p(8, 10); }
            if (ms == 800) { f.animation = 4177; }
            if (ms == 1200) { f.authority = p(6, 10); }
            if (ms == 1600) { f.animation = -1; }
            if (ms == 1800) { f.authority = p(5, 10); f.destination = null; f.nativePoint = f.authority; }
            LocalPoint before = f.controller.position(); f.frame(ms);
            if (before.getX() > p(5, 10).getX()) { assertTrue("no combat stop/restart", f.controller.position().getX() < before.getX()); }
            assertTrue(MovementPath.distance(before, f.controller.position()) <= 9);
        }
        assertEquals(512, f.controller.orientation());
        f.controller.close();
        assertFalse(trace.stream().filter(e -> e instanceof MovementTrace.Sample).map(e -> (MovementTrace.Sample) e)
            .anyMatch(s -> s.cycle > 120 && s.combatLocked));
    }

    @Test
    public void walkDuringABowShotReleasesStationaryFacingEvenWithAStaleNativeTarget()
    {
        Fixture f = bow(10, 10, 12, 10);
        f.click("Attack"); f.interacting = f.npc;
        for (int ms = 0; ms <= 500; ms += 20) { f.frame(ms); }
        f.animation = 426; f.nativeOrientation = 1536;
        f.controller.walkClick(); f.destination = p(7, 10);
        for (int ms = 520; ms <= 2400; ms += 20)
        {
            if (ms == 700) { f.authority = p(8, 10); }
            if (ms == 1300) { f.authority = p(7, 10); f.destination = null; f.nativePoint = f.authority; }
            if (ms == 1800) { f.animation = -1; }
            f.frame(ms);
        }
        assertEquals(p(7, 10), f.controller.position());
        assertEquals("a stale NPC reference/shot cannot reclaim facing", 512, f.controller.orientation());
        f.controller.close();
    }

    @Test
    public void anUnengagedTargetTimesOutAndReplacementInteractionOrDeathRetiresFacing()
    {
        for (int change = 0; change < 4; ++change)
        {
            Fixture f = bow(10, 10, 12, 10);
            f.click("Attack"); f.frame(0); f.frame(400);
            if (change == 1) { f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to")); }
            if (change == 2) { f.npcDead = true; }
            if (change == 3) { f.attackStyle = 2; }
            for (int ms = 420; ms <= 3000; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            assertEquals("native idle resumes after invalidation", 0, f.controller.orientation());
            f.controller.close();
        }
    }

    @Test
    public void onlyAFreshNativeAutoRetaliationEngagementCanReclaimFacingAfterWalk()
    {
        for (boolean auto : new boolean[] {false, true})
        {
            Fixture f = bow(10, 10, 12, 10);
            f.autoRetaliate = auto; f.click("Attack"); f.interacting = f.npc;
            f.frame(0); f.frame(400); f.animation = 426; f.nativeOrientation = 1536;
            f.controller.walkClick(); f.destination = p(7, 10);
            for (int ms = 420; ms <= 1800; ms += 20)
            {
                if (ms == 600) { f.authority = p(8, 10); }
                if (ms == 1200) { f.authority = p(7, 10); f.destination = null; f.nativePoint = f.authority; }
                f.frame(ms);
            }
            assertEquals("auto setting alone does not renew a stale target", 512, f.controller.orientation());
            f.interacting = null; f.animation = -1; f.frame(1820);
            f.interacting = f.npc; f.animation = 426;
            for (int ms = 1840; ms <= 2500; ms += 20) { f.frame(ms); }
            assertEquals(auto ? 1536 : 512, f.controller.orientation());
            f.controller.close();
        }
    }

    @Test
    public void nativeAttackEngagementWithoutAPriorPluginCaptureCanStillBeWalkedAwayFrom()
    {
        Fixture f = bow(10, 10, 12, 10);
        f.npcActions = new String[] {"Talk-to", "Attack"};
        f.interacting = f.npc; f.animation = 426;
        f.controller.walkClick(); f.destination = p(7, 10);
        f.frame(20); f.frame(40);
        assertTrue(f.controller.position().getX() < f.start.getX());
        f.controller.close();
    }

    @Test
    public void nativeAutoRetaliationDuringCatchUpRetiresTheWalkForecastWithoutDiscardingConfirmedDebt()
    {
        Fixture f = bow(10, 10, 12, 10);
        f.autoRetaliate = true; f.click("Attack"); f.interacting = f.npc; f.frame(0); f.frame(400);
        f.animation = 426; f.controller.walkClick(); f.destination = p(5, 10);
        f.frame(420); f.frame(440);
        f.authority = p(8, 10); f.interacting = null; f.animation = -1; f.frame(460);
        LocalPoint before = f.controller.position();
        f.interacting = f.npc; f.animation = 426; f.nativeOrientation = 1536; f.destination = null;
        f.frame(480);
        assertTrue("retain the real checked westbound debt", f.controller.position().getX() < before.getX());
        assertTrue(MovementPath.distance(before, f.controller.position()) <= 8);
        for (int ms = 500; ms <= 2200; ms += 20)
        {
            f.frame(ms);
            assertTrue("the old unconfirmed Walk cannot continue past authority", f.controller.position().getX() >= f.authority.getX());
        }
        assertEquals(f.authority, f.controller.position());
        assertEquals("fresh native auto-engagement can reclaim facing", 1536, f.controller.orientation());
        assertEquals(426, f.animation);
        f.controller.close();
    }

    @Test
    public void combatWalkDoesNotRelaxControlUnrelatedEffectsDisabledStartsOrNativeLocationActions()
    {
        for (int gate = 0; gate < 4; ++gate)
        {
            Fixture f = bow(10, 10, 12, 10);
            f.click("Attack"); f.interacting = f.npc; f.frame(0); f.frame(200);
            f.animation = 426;
            if (gate == 0) { f.control = true; }
            if (gate == 1)
            {
                f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to"));
                f.spot = true; f.animation = net.runelite.api.gameval.AnimationID.HUMAN_UNARMEDBLOCK;
            }
            if (gate == 2) { f.starts = false; }
            if (gate == 3) { f.animation = net.runelite.api.gameval.AnimationID.HUMAN_CASTTELEPORT; }
            f.controller.walkClick(); f.destination = p(7, 10);
            for (int ms = 220; ms <= 400; ms += 20) { f.frame(ms); assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void rangedBoundariesRespectProjectileWallsWalkingLongrangeAndNativeAuthority()
    {
        for (int gate = 0; gate < 4; ++gate)
        {
            Fixture f = bow(10, 10, 18, 10);
            if (gate == 0) { f.attackStyle = 2; }
            if (gate == 1) { f.run = false; }
            if (gate == 2) { f.flags[11][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL | CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL; }
            if (gate == 3) { f.weaponName = "Unlisted bow"; }
            f.click("Attack"); f.frame(0); f.frame(100);
            if (gate == 1) { assertEquals(f.start.getX() + 20, f.controller.position().getX()); }
            else { assertEquals(f.start, f.controller.position()); }
            f.controller.close();
        }
    }

    @Test
    public void knownRangedTargetStepsUseBoundedNormalPursuitAndNeverMeleeTrailingPace()
    {
        Fixture f = bow(10, 10, 17, 10);
        f.click("Attack"); f.interacting = f.npc; f.frame(0); f.frame(400);
        f.area = new WorldArea(3218, 3210, 1, 1, 0);
        f.frame(420);
        assertEquals("a run follows observed range loss at normal pace", f.start.getX() + 8, f.controller.position().getX());
        for (int ms = 440; ms <= 3000; ms += 20)
        {
            if (ms == 600) { f.area = new WorldArea(3219, 3210, 1, 1, 0); }
            if (ms == 1000) { f.area = new WorldArea(3220, 3210, 1, 1, 0); }
            f.frame(ms);
        }
        assertEquals("target motion alone cannot replenish prediction", f.start, f.controller.position());
        f.controller.close();
    }

    @Test
    public void conservativeProfilesRetainTheirOuterPrefixAndStationaryFacingWithoutAFollowReserve()
    {
        for (boolean halberd : new boolean[] {false, true})
        {
            Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(22, 10).getX(), p(22, 10).getY());
            if (halberd) { f.weaponId = 1; f.weaponName = "Crystal halberd"; }
            else { f.ranged("Unlisted bow", 2, 1); }
            f.click("Attack"); f.frame(0); f.frame(100);
            assertTrue("existing conservative outer approach still starts", f.controller.position().getX() > f.start.getX());
            f.area = new WorldArea(3223, 3210, 1, 1, 0);
            for (int ms = 120; ms <= 1600; ms += 20) { f.frame(ms); }
            assertEquals("these profiles do not gain moving-target speculation", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void minimapCombatEscapeUsesTheSameSmoothingLatchAndNativePosePolicy()
    {
        for (int smoothing : new int[] {0, 50, 60})
        {
            Fixture f = bow(10, 10, 12, 10);
            f.smoothing = smoothing; f.click("Attack"); f.interacting = f.npc; f.frame(0); f.frame(400);
            f.animation = 426;
            LocalPoint goal = p(7, 10); f.destination = goal;
            f.controller.walkClick(null); f.destination = null;
            for (int ms = 420; ms <= 520; ms += 20)
            {
                f.frame(ms);
                if (ms < 400 + smoothing) { assertEquals(f.start, f.controller.position()); }
            }
            assertTrue(f.controller.position().getX() < f.start.getX());
            f.controller.sceneChanged(false); f.nativePoint = f.authority; f.destination = null;
            f.frame(540);
            assertEquals("lifecycle retires the escape", f.authority, f.controller.position());
            f.controller.close();
        }
    }

    @Test
    public void aClosingProjectileCorridorRetiresOnlyTheForecastAndKeepsItsCheckedConfirmedPrefix()
    {
        Fixture f = bow(10, 10, 22, 10);
        f.click("Attack"); f.frame(0); f.frame(100);
        f.authority = p(12, 10); f.interacting = f.npc; f.frame(120);
        f.flags[19][10] = CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL;
        LocalPoint before = f.controller.position(); f.frame(140);
        assertTrue("closing sight does not discard confirmed movement", f.controller.position().getX() > before.getX());
        for (int ms = 160; ms <= 2000; ms += 20) { f.frame(ms); assertTrue(f.controller.position().getX() <= p(12, 10).getX()); }
        assertEquals(f.authority, f.controller.position());
        f.controller.close();
    }

    @Test
    public void diagnosticsCopyNativeCacheStyleAndCombatWalkOwnershipBeforeWorkerFormatting()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = new Fixture(p(10, 10).getX(), p(10, 10).getY(), p(12, 10).getX(), p(12, 10).getY(), trace);
        f.ranged("Magic shortbow (i)", 12788, 1); f.smoothing = 0;
        f.click("Attack"); f.frame(0); f.frame(200);
        f.animation = 426; f.interacting = f.npc;
        f.controller.walkClick(); f.destination = p(7, 10); f.frame(220); f.frame(240);
        f.controller.close();
        MovementTrace.Sample attack = trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).filter(s -> s.combatProfile.equals("ranged")).findFirst().get();
        f.attackStyle = 2; f.weaponName = "Changed"; f.area = new WorldArea(3219, 3219, 1, 1, 0);
        String text = java.util.concurrent.CompletableFuture.supplyAsync(attack::line).join();
        assertTrue(text.contains("combatCategory=3 combatStyle=1 combatCacheStyle=ranging combatProfile=ranged"));
        assertTrue(trace.stream().filter(e -> e instanceof MovementTrace.Sample).map(e -> (MovementTrace.Sample) e)
            .anyMatch(s -> s.combatWalk && s.combatPhase.equals("none")));
    }
}
