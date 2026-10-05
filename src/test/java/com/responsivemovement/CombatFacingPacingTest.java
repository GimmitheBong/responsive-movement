package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuAction;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Arrival-facing ownership and slower pursuit of observed target steps. */
public class CombatFacingPacingTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static Fixture fixture(int x, int y, int nx, int ny)
    {
        return fixture(x, y, nx, ny, null);
    }
    private static Fixture fixture(int x, int y, int nx, int ny, List<MovementTrace.Entry> trace)
    {
        Fixture f = new Fixture(p(x, y).getX(), p(x, y).getY(), p(nx, ny).getX(), p(nx, ny).getY(), trace);
        f.weaponId = 26484; f.weaponName = "Captured adjacent melee weapon"; f.speed = 1.3;
        f.interacting = f.npc;
        return f;
    }

    @Test
    public void arrivedCombatFacingKeepsTheCustomProviderEvenWhenNativeIdleIsAligned()
    {
        Fixture f = fixture(10, 10, 10, 9);
        f.click("Attack"); f.frame(0); f.frame(100);
        assertEquals(f.start, f.controller.position());
        assertFalse("native idle must not take over the owned combat gaze", f.controller.nativeVisible());
        f.controller.close();
    }

    @Test
    public void stationaryPlayerTracksTheDrawnNpcBetweenTileUpdatesThroughCombatPoses()
    {
        Fixture f = fixture(10, 10, 10, 9);
        f.click("Attack"); f.frame(0); f.frame(100);
        LocalPoint held = f.controller.position();
        f.npcNativePoint = new LocalPoint(p(10, 9).getX() + 64, p(10, 9).getY(), 0);
        f.animation = 1658;
        for (int ms = 120; ms <= 600; ms += 20)
        {
            int before = f.controller.orientation(); f.frame(ms);
            assertEquals(held, f.controller.position());
            assertTrue(Math.abs(MotionMath.difference(before, f.controller.orientation())) <= 37);
        }
        assertEquals(MotionMath.heading(64, -128), f.controller.orientation());
        f.npcNativePoint = new LocalPoint(p(10, 9).getX() - 64, p(10, 9).getY(), 0);
        f.animation = 4177;
        for (int ms = 620; ms <= 1200; ms += 20) { f.frame(ms); assertEquals(held, f.controller.position()); }
        assertEquals(MotionMath.heading(-64, -128), f.controller.orientation());
        f.animation = -1; f.controller.walkClick(); f.destination = p(10, 7);
        f.frame(1220); f.frame(1300); f.frame(1400);
        assertNotEquals("Walk releases the lock", held, f.controller.position());
        f.controller.close();
    }

    @Test
    public void followingAfterArrivalStartsBelowTheConfiguredWalkingRate()
    {
        Fixture f = fixture(14, 10, 15, 10);
        f.click("Attack"); f.frame(0); f.frame(100);
        f.area = new WorldArea(3216, 3210, 1, 1, 0);
        f.frame(120);
        int travel = f.controller.position().getX() - f.start.getX();
        assertTrue("observed motion releases pursuit", travel > 0);
        assertTrue("a 20 ms walking frame at speed 6.5 is about 5 units", travel < 5);
        f.controller.close();
    }

    static Hitsplat hit(boolean mine)
    {
        return new Hitsplat() {
            @Override public int getHitsplatType() { return mine ? HitsplatID.DAMAGE_ME : HitsplatID.DAMAGE_OTHER; }
            @Override public int getAmount() { return 5; }
            @Override public int getDisappearsOnGameCycle() { return 1000; }
        };
    }

    @Test
    public void lockedFollowingFacesTheDrawnNpcInsteadOfItsTravelDirection()
    {
        Fixture f = fixture(14, 10, 15, 10);
        f.click("Attack"); f.frame(0);
        for (int ms = 20; ms <= 400; ms += 20) { f.frame(ms); }
        f.area = new WorldArea(3216, 3210, 1, 1, 0);
        f.npcNativePoint = new LocalPoint(p(16, 10).getX(), p(16, 10).getY() - 64, 0);
        f.animation = 1658;
        f.frame(420);
        assertTrue(f.controller.position().getX() > f.start.getX());
        assertNotEquals("a moving gaze lock must not switch back to the eastbound travel heading", 1536, f.controller.orientation());
        assertEquals("native hit pose is retained", 1658, f.animation);
        f.controller.close();
    }

    @Test
    public void anOwnedTargetHitSettlesConfirmedDebtWithoutSnappingOrChangingAnimation()
    {
        Fixture f = fixture(14, 10, 15, 10);
        f.click("Attack"); f.frame(0); f.frame(100);
        f.area = new WorldArea(3216, 3210, 1, 1, 0);
        for (int ms = 120; ms <= 680; ms += 20) { f.frame(ms); }
        LocalPoint before = f.controller.position();
        assertTrue("trailing leaves a positional reserve before the real hit", before.getX() < p(15, 10).getX());
        f.authority = p(15, 10); f.animation = 1658;
        f.at(700); f.controller.combatHit(f.npc, hit(true)); f.frame(700);
        assertTrue("one normal movement budget", MovementPath.distance(before, f.controller.position()) <= 11);
        assertTrue("hit confirmation is not a teleport", f.controller.position().getX() < f.authority.getX());
        for (int ms = 720; ms <= 1000; ms += 20) { f.frame(ms); }
        assertEquals(f.authority, f.controller.position());
        assertEquals(1658, f.animation);
        f.controller.close();
    }

    @Test
    public void otherPlayersHitsAndIncomingPlayerHitsCannotReleaseTheTrail()
    {
        Fixture f = fixture(14, 10, 15, 10);
        f.click("Attack"); f.frame(0); f.frame(100);
        f.area = new WorldArea(3216, 3210, 1, 1, 0);
        for (int ms = 120; ms <= 680; ms += 20) { f.frame(ms); }
        f.authority = p(15, 10); f.animation = 4177;
        f.at(700); f.controller.combatHit(f.npc, hit(false)); f.controller.combatHit(f.player, hit(true));
        f.frame(700);
        LocalPoint before = f.controller.position(); f.frame(720);
        assertTrue("incoming block/damage is not the outgoing owned hit", f.controller.position().getX() - before.getX() < 5);
        assertTrue(f.controller.position().getX() < f.authority.getX());
        f.controller.close();
    }

    @Test
    public void repeatedAttackOnTheSameNpcRetainsTheFacingLockAndSlowerPacing()
    {
        List<MovementTrace.Entry> trace = new ArrayList<>();
        Fixture f = fixture(14, 10, 15, 10, trace);
        f.click("Attack"); f.frame(0); f.frame(100);
        f.area = new WorldArea(3216, 3210, 1, 1, 0);
        f.npcNativePoint = new LocalPoint(p(16, 10).getX(), p(16, 10).getY() - 64, 0);
        f.frame(120); f.click("Attack"); f.frame(120);
        LocalPoint before = f.controller.position(); f.frame(140);
        assertTrue(f.controller.position().getX() - before.getX() < 5);
        f.animation = -1;
        f.controller.worldInteraction(f.event(MenuAction.NPC_FIRST_OPTION, "Talk-to"));
        f.frame(160);
        f.controller.close();
        assertTrue("a repeated Attack preserves the lock", trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).anyMatch(s -> s.cycle == 107 && s.combatLocked && s.combatTrailing));
        assertTrue("another world interaction retires the combat gaze owner", trace.stream().filter(e -> e instanceof MovementTrace.Sample)
            .map(e -> (MovementTrace.Sample) e).anyMatch(s -> s.cycle == 108 && !s.combatLocked && s.combatPhase.equals("none")));
    }

    @Test
    public void trailerUsesTheSameExponentialClockAcrossFrameCadencesAndStillExpires()
    {
        Fixture f = fixture(14, 10, 16, 10);
        NpcApproach npc = NpcApproach.capture(f.client, f.view, f.event(MenuAction.NPC_SECOND_OPTION, "Attack"), null);
        MovementPath fine = MovementPath.idle(f.start, 0, 1.3, (a, b) -> true);
        MovementPath coarse = MovementPath.idle(f.start, 0, 1.3, (a, b) -> true);
        fine.armCombat(0); coarse.armCombat(0);
        fine = fine.followCombat(npc, true, 0, true); coarse = coarse.followCombat(npc, true, 0, true);
        assertNotNull(fine); assertNotNull(coarse);
        long hard = fine.predictionDeadlineNanos();
        for (int ms = 20; ms <= 600; ms += 20) { fine.advance(ms * 1_000_000L); }
        for (int ms = 100; ms <= 600; ms += 100) { coarse.advance(ms * 1_000_000L); }
        assertEquals(fine.position(), coarse.position());
        assertTrue(fine.moving()); assertFalse(fine.running());
        assertEquals(hard, fine.predictionDeadlineNanos());
        fine.advance(901_000_000L);
        for (int ms = 921; ms <= 2501; ms += 20) { fine.advance(ms * 1_000_000L); }
        assertEquals("unconfirmed trail still reconciles on timeout", f.start, fine.position());
        f.controller.close();
    }
}
