package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real AWT paint checks plus API-double authority/native geometry; never renders RuneScape. */
public class TrueTileOverlayTest
{
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static Polygon square() { return new Polygon(new int[] {30, 70, 70, 30}, new int[] {30, 30, 70, 70}, 4); }

    @Test
    public void serverHighlightUsesActualAuthorityEvenWhenDisplayAndNativePositionDisagree()
    {
        Fixture f = new Fixture(1344, 1344, 7000, 7000);
        try
        {
            f.smoothing = 0; f.controller.walkClick(); f.destination = tile(14, 10);
            f.frame(0); f.frame(300);
            assertNotEquals(f.authority, f.controller.position());
            f.authority = tile(12, 10); f.nativePoint = tile(11, 10);
            assertEquals(tile(12, 10), TrueTileOverlay.selectedTile(f.player, TrueTileMode.SERVER_TRUE_TILE));
            assertEquals(tile(11, 10), TrueTileOverlay.selectedTile(f.player, TrueTileMode.NATIVE_MOVEMENT_TILES));
        }
        finally { f.controller.close(); }
    }

    @Test
    public void nativeTrackingVisitsIntermediateRunningTilesWhileServerAuthorityStaysAtThePairEndpoint()
    {
        Fixture f = new Fixture(1344, 1344, 7000, 7000);
        try
        {
            f.authority = tile(12, 10);
            Set<LocalPoint> seen = new HashSet<>();
            for (int x = tile(10, 10).getX(); x <= tile(12, 10).getX(); x += 8)
            {
                f.nativePoint = new LocalPoint(x, tile(10, 10).getY(), 0);
                seen.add(TrueTileOverlay.selectedTile(f.player, TrueTileMode.NATIVE_MOVEMENT_TILES));
                assertEquals(tile(12, 10), TrueTileOverlay.selectedTile(f.player, TrueTileMode.SERVER_TRUE_TILE));
            }
            assertEquals(Set.of(tile(10, 10), tile(11, 10), tile(12, 10)), seen);
        }
        finally { f.controller.close(); }
    }

    @Test
    public void nativeSelectionIsTileAlignedAndRejectsForeignOrOutOfSceneCoordinates()
    {
        Fixture f = new Fixture(1344, 1344, 7000, 7000);
        try
        {
            f.nativePoint = new LocalPoint(1410, 1410, 0);
            assertEquals(tile(11, 11), TrueTileOverlay.selectedTile(f.player, TrueTileMode.NATIVE_MOVEMENT_TILES));
            f.nativePoint = new LocalPoint(1410, 1410, 1);
            assertNull(TrueTileOverlay.selectedTile(f.player, TrueTileMode.NATIVE_MOVEMENT_TILES));
            f.nativePoint = new LocalPoint(20000, 1410, 0);
            assertNull(TrueTileOverlay.selectedTile(f.player, TrueTileMode.NATIVE_MOVEMENT_TILES));
            assertNull(TrueTileOverlay.selectedTile(null, TrueTileMode.SERVER_TRUE_TILE));
        }
        finally { f.controller.close(); }
    }

    @Test
    public void serverSelectionRejectsWrongPlaneAndMissingSceneEvidence()
    {
        Fixture f = new Fixture(1344, 1344, 7000, 7000);
        try
        {
            Player wrongPlane = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (object, method, args) -> method.getName().equals("getWorldLocation") ? new WorldPoint(3210, 3210, 1) :
                    f.playerCall(method.getName(), args));
            assertNull(TrueTileOverlay.selectedTile(wrongPlane, TrueTileMode.SERVER_TRUE_TILE));
            f.authority = new LocalPoint(20000, 1344, 0);
            assertNull(TrueTileOverlay.selectedTile(f.player, TrueTileMode.SERVER_TRUE_TILE));
        }
        finally { f.controller.close(); }
    }

    @Test
    public void opaqueFillAndIndependentBorderColoursRenderAtTheConfiguredWidths()
    {
        BufferedImage image = paint(Color.RED, Color.BLUE, 4, 0);
        assertEquals(Color.RED.getRGB(), image.getRGB(50, 50));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(30, 50));
        assertEquals(0, image.getRGB(20, 50));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(29, 50));
        assertEquals(0, paint(Color.RED, Color.BLUE, 0, 0).getRGB(29, 50));
    }

    @Test
    public void featheredFillFadesAtTheEdgeWithoutChangingItsInteriorColourOrAlpha()
    {
        Color fill = new Color(10, 150, 240, 160);
        BufferedImage image = paint(fill, null, 0, 8);
        // AWT's translucent colour conversion may round a channel by one.
        assertEquals(paint(fill, null, 0, 0).getRGB(50, 50), image.getRGB(50, 50));
        int edge = alpha(image, 31, 50), middle = alpha(image, 34, 50), interior = alpha(image, 42, 50);
        assertTrue(edge > 0 && edge < middle);
        assertTrue(middle < interior);
        assertEquals(160, interior);
        assertEquals(0, alpha(image, 25, 50));
    }

    @Test
    public void borderFeatherMakesABoundedSoftEdgeAndTransparentSettingsHideIt()
    {
        BufferedImage image = paint(null, new Color(200, 30, 10, 180), 2, 8);
        assertEquals(180, alpha(image, 30, 50));
        assertTrue(alpha(image, 26, 50) > alpha(image, 23, 50));
        assertTrue(alpha(image, 23, 50) > 0);
        assertEquals(0, alpha(image, 19, 50));
        BufferedImage invisible = paint(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), 10, 20);
        for (int x = 0; x < 100; ++x) { assertEquals(0, invisible.getRGB(x, 50)); }
    }

    @Test
    public void paintingRestoresGraphicsStateAndClampsInvalidStyleSettings()
    {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(Color.GREEN);
            BasicStroke stroke = new BasicStroke(3); graphics.setStroke(stroke);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            TrueTileOverlay.paint(graphics, square(), Color.RED, Color.BLUE, 100, 100);
            assertEquals(Color.GREEN, graphics.getColor()); assertEquals(stroke, graphics.getStroke());
            assertEquals(RenderingHints.VALUE_ANTIALIAS_OFF, graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
        }
        finally { graphics.dispose(); }
        assertEquals(0, image.getRGB(0, 50));
    }

    @Test
    public void sectionsKeepTheExistingCatchUpKeysAndTrueTileIsOptIn()
    {
        try
        {
            ConfigItem toggle = ResponsiveMovementConfig.class.getMethod("catchUp").getAnnotation(ConfigItem.class);
            ConfigItem strength = ResponsiveMovementConfig.class.getMethod("catchUpPercent").getAnnotation(ConfigItem.class);
            assertEquals("catchUp", toggle.keyName()); assertEquals("catchUpPercent", strength.keyName());
            assertEquals(ResponsiveMovementConfig.PACING, toggle.section()); assertEquals(toggle.section(), strength.section());
            assertEquals(ResponsiveMovementConfig.PACING,
                ResponsiveMovementConfig.class.getMethod("slowAhead").getAnnotation(ConfigItem.class).section());
            assertEquals(ResponsiveMovementConfig.TRUE_TILE,
                ResponsiveMovementConfig.class.getMethod("showTrueTile").getAnnotation(ConfigItem.class).section());
        }
        catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
        ResponsiveMovementConfig config = new ResponsiveMovementConfig() {};
        assertFalse(config.showTrueTile()); assertEquals(TrueTileMode.SERVER_TRUE_TILE, config.trueTileMode());
        assertTrue(config.slowAhead()); assertEquals(10, config.slowAheadPercent());
    }

    private static BufferedImage paint(Color fill, Color border, int width, int feather)
    {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { TrueTileOverlay.paint(graphics, square(), fill, border, width, feather); }
        finally { graphics.dispose(); }
        return image;
    }

    private static int alpha(BufferedImage image, int x, int y) { return image.getRGB(x, y) >>> 24; }
}
