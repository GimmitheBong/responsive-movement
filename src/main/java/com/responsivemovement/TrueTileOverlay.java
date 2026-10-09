package com.responsivemovement;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Native/server position only; independent of MovementPath and custom body/overhead ownership. */
final class TrueTileOverlay extends Overlay
{
    private final Client client;
    private final ResponsiveMovementConfig config;

    @Inject
    TrueTileOverlay(Client client, ResponsiveMovementConfig config)
    {
        this.client = client; this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showTrueTile() || client.getGameState() != GameState.LOGGED_IN) { return null; }
        LocalPoint tile = selectedTile(client.getLocalPlayer(), config.trueTileMode());
        if (tile == null) { return null; }
        Polygon polygon = Perspective.getCanvasTilePoly(client, tile);
        if (polygon != null)
        {
            paint(graphics, polygon, config.trueTileFill(), config.trueTileBorder(),
                config.trueTileBorderWidth(), config.trueTileFeather());
        }
        return null;
    }

    /** Re-read each overlay frame: no guessed intermediate server route or retained scene coordinates. */
    static LocalPoint selectedTile(Player player, TrueTileMode mode)
    {
        WorldView view = player == null ? null : player.getWorldView();
        if (view == null) { return null; }
        LocalPoint point;
        if (mode == TrueTileMode.NATIVE_MOVEMENT_TILES)
        {
            point = player.getLocalLocation();
            if (point == null || point.getWorldView() != view.getId()) { return null; }
            point = new LocalPoint(point.getSceneX() * 128 + 64, point.getSceneY() * 128 + 64, view.getId());
        }
        else
        {
            WorldPoint world = player.getWorldLocation();
            if (world == null || world.getPlane() != view.getPlane()) { return null; }
            point = LocalPoint.fromWorld(view, world);
        }
        return point != null && point.getSceneX() >= 0 && point.getSceneY() >= 0 &&
            point.getSceneX() < view.getSizeX() && point.getSceneY() < view.getSizeY() ? point : null;
    }

    /** Bounded screen-space feather bands, with no image blur, I/O or scene/model reads. */
    static void paint(Graphics2D target, Polygon polygon, Color fill, Color border, int width, int feather)
    {
        width = Math.max(0, Math.min(10, width));
        feather = Math.max(0, Math.min(20, feather));
        Graphics2D graphics = (Graphics2D) target.create();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (fill != null && fill.getAlpha() > 0)
            {
                Area whole = new Area(polygon), remaining = new Area(whole);
                for (int i = 1; i <= feather; ++i)
                {
                    Area inner = new Area(whole);
                    inner.subtract(strokeArea(polygon, i * 2));
                    Area band = new Area(remaining);
                    band.subtract(inner);
                    graphics.setColor(alpha(fill, (i - 0.5) / feather));
                    graphics.fill(band);
                    remaining = inner;
                }
                graphics.setColor(fill);
                graphics.fill(remaining);
            }
            if (width > 0 && border != null && border.getAlpha() > 0)
            {
                Area core = strokeArea(polygon, width), previous = core;
                for (int i = 1; i <= feather; ++i)
                {
                    Area outer = strokeArea(polygon, width + i * 2);
                    Area band = new Area(outer);
                    band.subtract(previous);
                    graphics.setColor(alpha(border, 1 - (i - 0.5) / feather));
                    graphics.fill(band);
                    previous = outer;
                }
                graphics.setColor(border);
                graphics.fill(core);
            }
        }
        finally { graphics.dispose(); }
    }

    private static Area strokeArea(Polygon polygon, int width)
    {
        return new Area(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(polygon));
    }

    private static Color alpha(Color color, double fraction)
    {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(color.getAlpha() * fraction));
    }
}
