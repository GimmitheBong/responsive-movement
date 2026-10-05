package com.responsivemovement;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.SkullIcon;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

import static org.junit.Assert.*;

public class PresentationTest
{
    @Test
    public void expiredEffectsAndNativeFallbackRemoveRegisteredEffectObjects()
    {
        NpcApproachControllerTest.Fixture f = new NpcApproachControllerTest.Fixture(6336, 6336, 6848, 6336);
        f.controller.close();
        PlayerPresentation presentation = new PlayerPresentation(f.client);
        presentation.owner(f.player);
        f.spot = true;
        assertTrue(presentation.prepare(f.start, 0, false, false, false, false, true));
        assertEquals(2, f.objects.size()); // Body and the currently active effect.
        f.spot = false;
        presentation.prepare(f.start, 0, false, false, false, false, true);
        assertEquals("expired effects must leave the client's registered object set", 1, f.objects.size());
        f.spot = true;
        presentation.prepare(f.start, 0, false, false, false, false, true);
        assertEquals(2, f.objects.size());
        presentation.nativeFallback();
        assertEquals("a native handoff removes custom effects but retains the hidden body", 1, f.objects.size());
        for (net.runelite.api.RuneLiteObjectController object : f.objects) { assertNull(object.getModel()); }
        presentation.close();
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void interactionFacingWaitsForBothTheNativeAndVisibleArrival()
    {
        LocalPoint endpoint = new LocalPoint(6464, 6336, 0);
        LocalPoint catchingUp = new LocalPoint(6336, 6185, 0);
        assertFalse(MovementController.interactionFacingReady(catchingUp, endpoint, true));
        assertFalse(MovementController.interactionFacingReady(endpoint, endpoint, false));
        assertTrue(MovementController.interactionFacingReady(endpoint, endpoint, true));
        assertFalse(MovementController.interactionFacingReady(null, endpoint, true));
    }

    @Test
    public void cameraHeightEasingIsFrameRateIndependent()
    {
        assertEquals(50, PresentationCamera.easeHeight(0, 100, 80), 0.001);
        float split = PresentationCamera.easeHeight(PresentationCamera.easeHeight(0, 100, 40), 100, 40);
        assertEquals(50, split, 0.001);
        assertEquals(100, PresentationCamera.easeHeight(Float.NaN, 100, 20), 0);
        assertEquals(50, PresentationCamera.easeHeight(50, Float.NaN, 20), 0);
    }

    @Test
    public void zoomFollowHeightKeepsNativeIntegerRoundingAndViewportBlend()
    {
        assertEquals(50, PresentationCamera.followHeight(256, 512, 334));
        assertEquals(62, PresentationCamera.followHeight(256, 512, 384));
        assertEquals(75, PresentationCamera.followHeight(256, 512, 434));
        assertEquals(50, PresentationCamera.followHeight(256, 512, 200));
        assertEquals(75, PresentationCamera.followHeight(256, 512, 700));
    }

    @Test
    public void onlyTheMatchingLocalPlayerCanBeSuppressed()
    {
        RenderState state = new RenderState(null, 42, 7, true, true);
        long player = 7L << 52;
        assertFalse(state.drawObject(player, 42));
        assertTrue(state.drawObject(player, 43));
        assertTrue(state.drawObject(player | 2L << 16, 42));
        assertTrue(state.drawObject(8L << 52, 42));
        assertTrue(RenderState.NATIVE.drawObject(player, 42));
    }

    @Test
    public void actualRightClickWorldActionsAreKeptSeparateFromWidgets()
    {
        assertTrue(ResponsiveMovementPlugin.worldInteraction(MenuAction.GAME_OBJECT_FIRST_OPTION));
        assertTrue(ResponsiveMovementPlugin.worldInteraction(MenuAction.NPC_FIRST_OPTION));
        assertTrue(ResponsiveMovementPlugin.worldInteraction(MenuAction.WIDGET_TARGET_ON_NPC));
        assertFalse(ResponsiveMovementPlugin.worldInteraction(MenuAction.WALK));
        assertFalse(ResponsiveMovementPlugin.worldInteraction(MenuAction.CC_OP));
        assertFalse(ResponsiveMovementPlugin.worldInteraction(null));
        Rectangle map = new Rectangle(100, 100, 150, 150);
        assertTrue(ResponsiveMovementPlugin.insideMinimap(new Point(175, 175), map));
        assertFalse(ResponsiveMovementPlugin.insideMinimap(new Point(100, 100), map));
    }

    @Test
    public void sourceOverheadArtworkIsBundledAndSmall()
    {
        OverheadAssets assets = new OverheadAssets();
        assertTrue(assets.ready());
        assertNotNull(assets.prayer(HeadIcon.MAGIC));
        assertNotNull(assets.skull(SkullIcon.LOOT_KEYS_FIVE));
        assertNotNull(assets.hit(HitsplatID.HEAL));
        assertNotSame(assets.hit(HitsplatID.HEAL), assets.hit(HitsplatID.DAMAGE_ME));
        for (BufferedImage image : assets.images())
        {
            assertNotNull(image);
            assertTrue(image.getWidth() > 0 && image.getWidth() <= 128);
            assertTrue(image.getHeight() > 0 && image.getHeight() <= 128);
        }
    }

    @Test
    public void healthBarsKeepClampingAndHdPadding()
    {
        assertEquals(30, MovementOverheads.healthFill(30, 1.2f, 0));
        assertEquals(0, MovementOverheads.healthFill(30, 0, 0));
        assertEquals(2, MovementOverheads.healthFill(40, 0, 1));
        assertEquals(20, MovementOverheads.healthFill(40, 0.5f, 1));
    }

    @Test
    public void nativeBodyHandoffRequiresStationaryAlignedPresentation()
    {
        LocalPoint point = new LocalPoint(1344, 1344, 0);
        assertTrue(PlayerPresentation.canUseNative(true, false, false, false, false, point, point, 0, 0));
        assertFalse(PlayerPresentation.canUseNative(true, true, false, false, false, point, point, 0, 0));
        assertFalse(PlayerPresentation.canUseNative(true, false, true, false, false, point, point, 0, 0));
        assertFalse(PlayerPresentation.canUseNative(true, false, false, false, true, point, point, 0, 0));
        assertFalse(PlayerPresentation.canUseNative(true, false, false, false, false, point, point, 0, 512));
        assertEquals(0, PlayerPresentation.poseFrame(-1, 8, true));
        assertEquals(0, PlayerPresentation.poseFrame(7, 8, false));
        assertEquals(7, PlayerPresentation.poseFrame(7, 8, true));
    }

    @Test
    public void nativeActionsRemainRealActionsRatherThanSyntheticTeleports()
    {
        assertTrue(MovementController.nativeLocationAction(AnimationID.HUMAN_CASTTELEPORT));
        assertTrue(MovementController.nativeLocationAction(AnimationID.POH_SMASH_MAGIC_TABLET));
        assertFalse(MovementController.nativeLocationAction(AnimationID.ZAROS_VERTICAL_CASTING));
        assertFalse(MovementController.nativeLocationAction(-1));
    }

    @Test
    public void turningHonorsTheRingAndTheConfiguredLimit()
    {
        assertEquals(1, MotionMath.difference(2047, 0));
        assertEquals(2047, MotionMath.turn(0, 1024, 1), 0);
        assertEquals(0, MotionMath.turn(2047, 0, 1), 0);
        assertEquals(2047.75, MotionMath.turn(2047.75, 0, 0), 0);
        assertEquals(0, MotionMath.turn(2047.75, 0, 0.5), 0);
        assertEquals(2047.5, MotionMath.turn(0.5, 2047, 1), 0);
        assertEquals(1536, MotionMath.heading(1, 0));
        assertEquals(1024, MotionMath.heading(0, 1));
        assertEquals(512, MotionMath.heading(-1, 0));
        assertEquals(0, MotionMath.heading(0, -1));
    }
}
