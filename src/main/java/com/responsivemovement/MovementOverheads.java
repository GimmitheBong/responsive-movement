package com.responsivemovement;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.interfacestyles.InterfaceStylesPlugin;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/** Self-player overheads at the prepared presentation point; no movement updates here. */
final class MovementOverheads extends Overlay
{
    private final Client client;
    private final ResponsiveMovementPlugin plugin;
    private final ResponsiveMovementConfig config;
    private final ConfigManager configManager;
    private final List<Hitsplat> hitsplats = new ArrayList<>();
    private final OverheadAssets assets = new OverheadAssets();
    private int healthUntilCycle;
    private final BufferedImage hdFront;
    private final BufferedImage hdBack;

    @Inject
    MovementOverheads(Client client, ResponsiveMovementPlugin plugin,
        ResponsiveMovementConfig config, ConfigManager configManager)
    {
        this.client = client; this.plugin = plugin; this.config = config; this.configManager = configManager;
        hdFront = ImageUtil.loadImageResource(InterfaceStylesPlugin.class, "2010/healthbar/default_front_40px.png");
        hdBack = ImageUtil.loadImageResource(InterfaceStylesPlugin.class, "2010/healthbar/default_back_40px.png");
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_HIGH);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    void tick()
    {
        hitsplats.removeIf(hit -> client.getGameCycle() >= hit.getDisappearsOnGameCycle());
    }

    void hit(Hitsplat hit)
    {
        hitsplats.add(hit);
        healthUntilCycle = client.getGameCycle() + 300;
    }

    boolean ready() { return assets.ready(); }
    void clear() { hitsplats.clear(); healthUntilCycle = 0; }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.supported())
        {
            graphics.setFont(FontManager.getRunescapeSmallFont());
            graphics.setColor(Color.ORANGE);
            graphics.drawString("Responsive Movement requires GPU or 117 HD", 12, 32);
            return null;
        }
        if (!config.overheads() || !plugin.customUi()) { return null; }
        Player player = client.getLocalPlayer();
        LocalPoint point = plugin.position();
        if (player == null || point == null) { return null; }
        int ground = Perspective.getFootprintTileHeight(client, point, player.getWorldView().getPlane(), player.getFootprintSize());
        Point head = Perspective.localToCanvas(client, point.getX(), point.getY(),
            ground - player.getLogicalHeight() - config.overheadHeight());
        if (head == null) { return null; }
        int offset = 0;
        String text = player.getOverheadText();
        if (text != null && !text.isEmpty())
        {
            graphics.setFont(FontManager.getRunescapeBoldFont());
            int x = head.getX() - graphics.getFontMetrics().stringWidth(text) / 2;
            int y = head.getY() + config.textOffset();
            drawText(graphics, text, x, y, Color.YELLOW);
            offset -= 5;
        }
        if (client.getGameCycle() < healthUntilCycle)
        {
            drawHealth(graphics, head.getX(), head.getY() - config.healthOffset() + offset);
            offset -= 4;
        }
        BufferedImage skull = assets.skull(player.getSkullIcon());
        if (skull != null)
        {
            graphics.drawImage(skull, head.getX() - skull.getWidth() / 2, head.getY() - 32 + offset, null);
            offset -= 28;
        }
        HeadIcon prayer = player.getOverheadIcon();
        BufferedImage prayerImage = prayer == null ? null : assets.prayer(prayer);
        if (prayerImage != null)
        {
            graphics.drawImage(prayerImage, head.getX() - prayerImage.getWidth() / 2, head.getY() - 32 + offset, null);
        }
        drawHitsplats(graphics, player, point);
        return null;
    }

    private void drawHealth(Graphics2D graphics, int x, int y)
    {
        float ratio = (float) client.getBoostedSkillLevel(Skill.HITPOINTS) / Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS));
        boolean hd = Boolean.parseBoolean(configManager.getConfiguration("interfaceStyles", "hdHealthBars")) &&
            hdFront != null && hdBack != null && hdFront.getWidth() == hdBack.getWidth() && hdFront.getHeight() == hdBack.getHeight();
        if (hd)
        {
            int width = hdFront.getWidth(), height = hdFront.getHeight();
            int left = x - width / 2, fill = healthFill(width, ratio, 1);
            graphics.drawImage(hdBack, left, y, null);
            graphics.drawImage(hdFront, left, y, left + fill, y + height, 0, 0, fill, height, null);
        }
        else
        {
            graphics.setColor(Color.RED); graphics.fillRect(x - 15, y, 30, 5);
            graphics.setColor(Color.GREEN); graphics.fillRect(x - 15, y, healthFill(30, ratio, 0), 5);
        }
    }

    static int healthFill(int width, float ratio, int padding)
    {
        if (width <= 0) { return 0; }
        return (int) Math.ceil(Math.max(Math.min(width, Math.max(0, padding) * 2), width * Math.max(0, Math.min(1, ratio))));
    }

    private void drawHitsplats(Graphics2D graphics, Player player, LocalPoint location)
    {
        List<Hitsplat> active = new ArrayList<>();
        for (Hitsplat hit : hitsplats)
        {
            if (hit.getDisappearsOnGameCycle() > client.getGameCycle()) { active.add(hit); }
        }
        active.sort(Comparator.comparingInt((Hitsplat hit) ->
            100 * (hit.getDisappearsOnGameCycle() - client.getGameCycle()) + hit.getAmount()).reversed());
        graphics.setFont(FontManager.getRunescapeSmallFont());
        int spacing = config.hitsplatSpacing();
        int[][] offsets = {{0, 0}, {0, -spacing + 5}, {-spacing / 2 - 3, -spacing / 2}, {spacing / 2 + 3, -spacing / 2}};
        for (int i = Math.min(4, active.size()) - 1; i >= 0; --i)
        {
            Hitsplat hit = active.get(i);
            String text = Integer.toString(hit.getAmount());
            Point point = Perspective.getCanvasTextLocation(client, graphics, location, text, player.getLogicalHeight() / 2);
            if (point == null) { continue; }
            int x = point.getX() + offsets[i][0], y = point.getY() + 8 + offsets[i][1];
            BufferedImage image = assets.hit(hit.getHitsplatType());
            FontMetrics metrics = graphics.getFontMetrics();
            if (image != null)
            {
                graphics.drawImage(image, x + metrics.stringWidth(text) / 2 - image.getWidth() / 2,
                    y - metrics.getHeight() + (metrics.getHeight() - image.getHeight()) / 2, null);
            }
            drawText(graphics, text, x, y, Color.WHITE);
        }
    }

    private static void drawText(Graphics2D graphics, String text, int x, int y, Color color)
    {
        graphics.setColor(Color.BLACK); graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color); graphics.drawString(text, x, y);
    }
}
