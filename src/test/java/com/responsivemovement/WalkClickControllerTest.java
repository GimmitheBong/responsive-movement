package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Scene/menu and post-interface minimap observation, using the real controller and native API doubles. */
public class WalkClickControllerTest
{
    @Test
    public void minimapAndSceneIdleStartsShareSmoothingWalkingRunningAndFacing()
    {
        for (boolean run : new boolean[] {false, true})
        {
            for (int smoothing : new int[] {0, 50, 60})
            {
                Fixture scene = fixture(), minimap = fixture();
                scene.run = minimap.run = run;
                scene.smoothing = minimap.smoothing = smoothing;
                walk(scene, false, p(6592, 6848), 0);
                walk(minimap, true, p(6592, 6848), 0);
                for (int ms = 0; ms <= 600; ms += 20)
                {
                    scene.frame(ms); minimap.frame(ms);
                    assertSamePresentation(scene, minimap);
                    if (ms < smoothing) { assertEquals(minimap.start, minimap.controller.position()); }
                }
                assertNotEquals(minimap.start, minimap.controller.position());
                close(scene, minimap);
            }
        }
    }

    @Test
    public void movingMinimapRetargetsMatchSceneClicksOnCardinalsDiagonalsAndKnights()
    {
        int[][] legs = {{2, 0}, {0, 2}, {2, 2}, {2, 1}, {-1, -2}};
        int[][] clicks = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, -2}, {-2, 3}};
        for (int[] leg : legs)
        {
            for (int[] click : clicks)
            {
                Fixture scene = fixture(), minimap = fixture();
                LocalPoint oldGoal = p(6080 + leg[0] * 128, 6592 + leg[1] * 128);
                scene.authority = minimap.authority = oldGoal;
                scene.destination = minimap.destination = oldGoal;
                for (int ms = 0; ms <= 200; ms += 20) { scene.frame(ms); minimap.frame(ms); }
                LocalPoint target = p(6080 + click[0] * 128, 6592 + click[1] * 128);
                walk(scene, false, target, 200);
                walk(minimap, true, target, 200);
                for (int ms = 220; ms <= 600; ms += 20)
                {
                    LocalPoint before = minimap.controller.position();
                    scene.frame(ms); minimap.frame(ms);
                    assertSamePresentation(scene, minimap);
                    assertNotEquals("a moving click must keep moving", before, minimap.controller.position());
                    assertTrue(MovementPath.distance(before, minimap.controller.position()) <= 8);
                }
                close(scene, minimap);
            }
        }
    }

    @Test
    public void minimapTrueTileAlignmentLeavesTheParallelRowWithoutTimeoutBacksteps()
    {
        Fixture scene = new Fixture(4416, 6720, 7000, 7000);
        Fixture minimap = new Fixture(4416, 6720, 7000, 7000);
        scene.authority = minimap.authority = p(4416, 6592);
        scene.frame(0); minimap.frame(0);
        walk(scene, false, p(5312, 6720), 0);
        walk(minimap, true, p(5312, 6720), 0);
        for (int ms = 20; ms <= 1600; ms += 20)
        {
            if (ms == 380) { scene.authority = minimap.authority = p(4672, 6592); }
            if (ms == 980) { scene.authority = minimap.authority = p(4928, 6592); }
            if (ms == 1580) { scene.authority = minimap.authority = p(5184, 6720); }
            LocalPoint before = minimap.controller.position();
            scene.frame(ms); minimap.frame(ms);
            assertSamePresentation(scene, minimap);
            assertTrue("no timeout reversal", minimap.controller.position().getX() >= before.getX());
            assertTrue(MovementPath.distance(before, minimap.controller.position()) <= 8);
            if (ms == 1060) { assertEquals(6592, minimap.controller.position().getY()); }
        }
        close(scene, minimap);
    }

    @Test
    public void nativeMinimapPublicationIsLatchedBeforeAnOldFlagCanReturn()
    {
        Fixture f = fixture();
        LocalPoint previousFlag = p(6080, 7104), target = p(6592, 6592);
        f.destination = target; // Native interface processing already published this click.
        f.controller.walkClick(previousFlag);
        f.destination = previousFlag; // A late old server flag, before the first preparation.
        for (int ms = 0; ms <= 800; ms += 20)
        {
            if (ms == 20) { f.authority = p(6336, 6592); }
            f.frame(ms);
            assertEquals("keep the clicked east route", 6592, f.controller.position().getY());
        }
        assertTrue("the preview must continue toward the latched click", f.controller.position().getX() > 6336);
        f.controller.close();
    }

    @Test
    public void unchangedNativeDestinationCanReplaceADifferentVisualClickAfterMinimapProcessing()
    {
        for (int smoothing : new int[] {0, 50})
        {
            Fixture f = fixture();
            f.smoothing = smoothing;
            walk(f, true, p(6976, 6592), 0);
            for (int ms = 0; ms <= 200; ms += 20) { f.frame(ms); }
            LocalPoint target = p(5696, 6592);
            f.destination = target;
            f.controller.walkClick(target); // The preceding tick already has this flag.
            for (int ms = 220; ms <= 280; ms += 20) { f.frame(ms); }
            LocalPoint before = f.controller.position();
            f.frame(300);
            assertTrue("an unchanged native flag can still select a new visual route",
                f.controller.position().getX() < before.getX());
            assertEquals(6592, f.controller.position().getY());
            f.controller.close();
        }
    }

    @Test
    public void sameTargetRepeatsKeepTheExistingMotionAndCannotRenewPrediction()
    {
        Fixture baseline = fixture(), repeated = fixture();
        LocalPoint target = p(6976, 6592);
        walk(baseline, true, target, 0);
        walk(repeated, true, target, 0);
        for (int ms = 0; ms <= 2400; ms += 20)
        {
            if (ms > 0 && ms <= 800 && ms % 200 == 0) { walk(repeated, true, target, ms); }
            baseline.frame(ms); repeated.frame(ms);
            assertSamePresentation(baseline, repeated);
        }
        assertEquals("unconfirmed movement remains bounded", repeated.authority, repeated.controller.position());
        close(baseline, repeated);
    }

    @Test
    public void repeatedPendingClicksKeepTheCapturedDestinationWithTheSameSettlingWindow()
    {
        Fixture scene = fixture(), minimap = fixture();
        LocalPoint target = p(6592, 6592);
        walk(scene, false, target, 0); walk(minimap, true, target, 0);
        scene.frame(0); minimap.frame(0);
        walk(scene, false, target, 20); walk(minimap, true, target, 20);
        for (int ms = 20; ms <= 200; ms += 20)
        {
            scene.frame(ms); minimap.frame(ms);
            assertSamePresentation(scene, minimap);
            if (ms < 70) { assertEquals(minimap.start, minimap.controller.position()); }
        }
        assertTrue(minimap.controller.position().getX() > minimap.start.getX());
        close(scene, minimap);
    }

    @Test
    public void alternatingSceneAndMinimapClicksUseOneContinuousMovementPipeline()
    {
        Fixture scene = fixture(), mixed = fixture();
        LocalPoint[] targets = {p(6592, 6592), p(6080, 7104), p(5696, 6464), p(6464, 6848)};
        for (int ms = 0; ms <= 800; ms += 20)
        {
            if (ms % 200 == 0 && ms / 200 < targets.length)
            {
                int click = ms / 200;
                walk(scene, false, targets[click], ms);
                walk(mixed, click % 2 == 0, targets[click], ms);
            }
            LocalPoint before = mixed.controller.position();
            scene.frame(ms); mixed.frame(ms);
            assertSamePresentation(scene, mixed);
            assertTrue(MovementPath.distance(before, mixed.controller.position()) <= 8);
        }
        close(scene, mixed);
    }

    @Test
    public void nativeStartGatesAndBlockedRoutesApplyEquallyToBothClickSources()
    {
        for (int gate = 0; gate < 5; ++gate)
        {
            Fixture scene = fixture(), minimap = fixture();
            for (Fixture f : new Fixture[] {scene, minimap})
            {
                if (gate == 0) { f.starts = false; }
                if (gate == 1) { f.control = true; }
                if (gate == 2) { f.animation = 422; }
                if (gate == 3) { f.spot = true; }
                if (gate == 4)
                {
                    for (int x = f.start.getSceneX() - 1; x <= f.start.getSceneX() + 1; ++x)
                    {
                        for (int y = f.start.getSceneY() - 1; y <= f.start.getSceneY() + 1; ++y)
                        {
                            f.flags[x][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
                        }
                    }
                }
            }
            walk(scene, false, p(6592, 6592), 0); walk(minimap, true, p(6592, 6592), 0);
            for (int ms = 0; ms <= 200; ms += 20)
            {
                scene.frame(ms); minimap.frame(ms);
                assertSamePresentation(scene, minimap);
                assertEquals(minimap.start, minimap.controller.position());
            }
            close(scene, minimap);
        }
    }

    @Test
    public void unavailableDestinationsAndSceneChangesCannotReplayAnOldMinimapClick()
    {
        for (boolean sceneChange : new boolean[] {false, true})
        {
            Fixture f = fixture();
            if (sceneChange)
            {
                walk(f, true, p(6592, 6592), 0);
                f.controller.sceneChanged(true);
            }
            else { f.controller.walkClick(null); }
            f.frame(120);
            f.destination = p(6080, 7104);
            for (int ms = 140; ms <= 400; ms += 20) { f.frame(ms); }
            assertEquals(f.start, f.controller.position());
            f.controller.close();
        }
    }

    private static Fixture fixture() { return new Fixture(6080, 6592, 7000, 7000); }
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    private static void walk(Fixture f, boolean minimap, LocalPoint target, int millis)
    {
        f.at(millis);
        LocalPoint previous = f.destination;
        if (minimap)
        {
            f.destination = target;
            f.controller.walkClick(previous);
        }
        else
        {
            f.controller.walkClick();
            f.destination = target;
        }
    }

    private static void assertSamePresentation(Fixture scene, Fixture minimap)
    {
        assertEquals(scene.controller.position(), minimap.controller.position());
        assertEquals(scene.controller.orientation(), minimap.controller.orientation());
        assertEquals(scene.controller.nativeVisible(), minimap.controller.nativeVisible());
    }

    private static void close(Fixture scene, Fixture minimap)
    {
        scene.controller.close(); minimap.controller.close();
        assertTrue(scene.objects.isEmpty()); assertTrue(minimap.objects.isEmpty());
    }
}
