package com.responsivemovement;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.HeadIcon;
import net.runelite.api.HitsplatID;
import net.runelite.api.SkullIcon;
import net.runelite.client.util.ImageUtil;

/** Original overhead artwork retained under the source project's BSD license. */
final class OverheadAssets
{
    private final Map<HeadIcon, BufferedImage> prayers = new EnumMap<>(HeadIcon.class);
    private final Map<Integer, BufferedImage> skulls = new HashMap<>();
    private final Map<Integer, BufferedImage> hits = new HashMap<>();
    private final BufferedImage damage = load("/hitsplats/DamageMe.png");
    private final boolean ready;

    OverheadAssets()
    {
        prayers.put(HeadIcon.MAGIC, load("/Magic.png"));
        prayers.put(HeadIcon.MELEE, load("/Melee.png"));
        prayers.put(HeadIcon.RANGED, load("/Ranged.png"));
        prayers.put(HeadIcon.SMITE, load("/Smite.png"));
        prayers.put(HeadIcon.RETRIBUTION, load("/Retribution.png"));
        prayers.put(HeadIcon.REDEMPTION, load("/Redemption.png"));
        skulls.put(SkullIcon.SKULL, load("/skulls/Skull.png"));
        skulls.put(SkullIcon.SKULL_HIGH_RISK, load("/skulls/SkullHighRisk.png"));
        skulls.put(SkullIcon.SKULL_FIGHT_PIT, load("/skulls/SkullFightPits.png"));
        skulls.put(SkullIcon.SKULL_DEADMAN, load("/skulls/SkullDeadman.png"));
        skulls.put(SkullIcon.FORINTHRY_SURGE, load("/skulls/SkullForinthrySurge.png"));
        skulls.put(SkullIcon.FORINTHRY_SURGE_DEADMAN, load("/skulls/SkullForinthrySurgeDeadman.png"));
        int[] keys = {SkullIcon.LOOT_KEYS_ONE, SkullIcon.LOOT_KEYS_TWO, SkullIcon.LOOT_KEYS_THREE,
            SkullIcon.LOOT_KEYS_FOUR, SkullIcon.LOOT_KEYS_FIVE};
        int[] surge = {SkullIcon.FORINTHRY_SURGE_KEYS_ONE, SkullIcon.FORINTHRY_SURGE_KEYS_TWO,
            SkullIcon.FORINTHRY_SURGE_KEYS_THREE, SkullIcon.FORINTHRY_SURGE_KEYS_FOUR, SkullIcon.FORINTHRY_SURGE_KEYS_FIVE};
        for (int i = 0; i < keys.length; ++i)
        {
            skulls.put(keys[i], load("/skulls/SkullLootKey" + (i + 1) + ".png"));
            skulls.put(surge[i], load("/skulls/SkullForinthrySurgeDeadmanKey" + (i + 1) + ".png"));
        }
        hits.put(HitsplatID.BLOCK_ME, load("/hitsplats/BlockMe.png"));
        hits.put(HitsplatID.BLOCK_OTHER, hits.get(HitsplatID.BLOCK_ME));
        hits.put(HitsplatID.POISON, load("/hitsplats/Poison.png"));
        hits.put(HitsplatID.VENOM, load("/hitsplats/Venom.png"));
        hits.put(HitsplatID.DISEASE, load("/hitsplats/Disease.png"));
        hits.put(HitsplatID.DISEASE_BLOCKED, hits.get(HitsplatID.DISEASE));
        hits.put(HitsplatID.BLEED, load("/hitsplats/Bleed.png"));
        hits.put(HitsplatID.CORRUPTION, load("/hitsplats/Corruption.png"));
        hits.put(HitsplatID.BURN, load("/hitsplats/Burn.png"));
        hits.put(HitsplatID.SANITY_DRAIN, load("/hitsplats/SanityDrain.png"));
        hits.put(HitsplatID.SANITY_RESTORE, load("/hitsplats/SanityRestore.png"));
        hits.put(HitsplatID.HEAL, load("/hitsplats/Heal.png"));
        hits.put(HitsplatID.DOOM, load("/hitsplats/Doom.png"));
        hits.put(HitsplatID.PRAYER_DRAIN, load("/hitsplats/PrayerDrain.png"));
        ready = damage != null && images().stream().allMatch(image -> image != null);
    }

    private static BufferedImage load(String path) { return ImageUtil.loadImageResource(OverheadAssets.class, path); }
    BufferedImage prayer(HeadIcon icon) { return prayers.get(icon); }
    BufferedImage skull(int icon) { return skulls.get(icon); }
    BufferedImage hit(int type) { return hits.getOrDefault(type, damage); }
    boolean ready() { return ready; }

    Collection<BufferedImage> images()
    {
        Collection<BufferedImage> images = new ArrayList<>(prayers.values());
        images.addAll(skulls.values()); images.addAll(hits.values()); images.add(damage);
        return images;
    }
}
