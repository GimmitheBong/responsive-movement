package com.responsivemovement;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Click-time combat evidence; a stopping reserve, never a server attack-range decision. */
final class CombatApproach
{
    private static final Map<String, Integer> RANGED_REACH = rangedReach();
    final int reserveTiles;
    final int weaponId;
    final boolean adjacentMelee;
    final boolean knownRanged;
    final int category;
    final int style;
    final String selectedStyle;

    private CombatApproach(int weaponId, int category, int style, String selectedStyle,
        int reserveTiles, boolean adjacentMelee, boolean knownRanged)
    {
        this.weaponId = weaponId; this.category = category; this.style = style;
        this.reserveTiles = reserveTiles;
        this.adjacentMelee = adjacentMelee;
        this.knownRanged = knownRanged;
        this.selectedStyle = selectedStyle;
    }

    static CombatApproach capture(Client client)
    {
        if (client == null) { return null; }
        int weapon = weapon(client);
        int category = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        int style = client.getVarpValue(VarPlayerID.COM_MODE);
        // The supported cache style enums are also used by RuneLite Attack Styles.
        // Read them once at the click, not for every prepared frame.
        EnumComposition categories = client.getEnum(EnumID.WEAPON_STYLES);
        int styleEnum = categories == null ? -1 : categories.getIntValue(category);
        EnumComposition styles = styleEnum < 0 ? null : client.getEnum(styleEnum);
        int[] structs = styles == null ? null : styles.getIntVals();
        String selected = "";
        boolean ranged = false;
        if (structs != null)
        {
            for (int i = 0; i < structs.length; ++i)
            {
                StructComposition data = client.getStructComposition(structs[i]);
                String name = data == null ? "" : normalize(data.getStringValue(ParamID.ATTACK_STYLE_NAME));
                // ATTACK_STYLE_NAME describes XP style: native bows use Ranging
                // for both Accurate and Rapid, not their combat-button labels.
                if (name.equals("ranging") || name.equals("rapid") || name.equals("longrange")) { ranged = true; }
                if (i == style) { selected = name; }
            }
        }
        ItemComposition item = weapon < 0 ? null : client.getItemDefinition(weapon);
        String name = item == null ? "" : item.getName();
        boolean adjacent = adjacentMelee(name, selected, ranged, style);
        boolean knownRanged = ranged && rangedStyle(selected) && style >= 0 && style < 4 &&
            RANGED_REACH.containsKey(weaponName(name));
        return new CombatApproach(weapon, category, style, selected,
            adjacent ? 1 : reserve(name, selected, ranged, style), adjacent, knownRanged);
    }

    boolean valid(Client client)
    {
        // A weapon/style switch cannot carry a forecast based on the old reach.
        return weapon(client) == weaponId && client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY) == category &&
            client.getVarpValue(VarPlayerID.COM_MODE) == style;
    }

    private static int weapon(Client client)
    {
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        Item item = equipment == null ? null : equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        return item == null ? -1 : item.getId();
    }

    static int reserve(String weapon, String selected, boolean ranged, int style)
    {
        selected = normalize(selected);
        if (style >= 4 || selected.equals("casting") || selected.equals("defensivecasting")) { return 10; }
        if (!ranged && (selected.equals("accurate") || selected.equals("aggressive") ||
            selected.equals("controlled") || selected.equals("defensive")))
        {
            return 2; // Includes the longer reach of halberds; authority owns the final approach.
        }
        if (!ranged || !rangedStyle(selected)) { return 10; }
        // Poison variants retain the same reach. All other unknown variants use
        // the conservative ten-tile envelope rather than a guessed short range.
        int base = RANGED_REACH.getOrDefault(weaponName(weapon), 10);
        return Math.max(2, Math.min(10, base + (selected.equals("longrange") ? 2 : 0)));
    }

    private static boolean rangedStyle(String style)
    {
        return style.equals("ranging") || style.equals("accurate") || style.equals("rapid") || style.equals("longrange");
    }

    private static String weaponName(String name)
    {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replaceAll("\\(p\\+{0,2}\\)$", "").trim();
    }

    static boolean adjacentMelee(String weapon, String selected, boolean ranged, int style)
    {
        selected = normalize(selected);
        return style >= 0 && style < 4 && !ranged &&
            (selected.equals("accurate") || selected.equals("aggressive") || selected.equals("controlled") || selected.equals("defensive")) &&
            (weapon == null || !weapon.toLowerCase(Locale.ROOT).contains("halberd"));
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "").trim();
    }

    private static Map<String, Integer> rangedReach()
    {
        Map<String, Integer> ranges = new HashMap<>();
        for (String wood : new String[] {"", "oak ", "willow ", "maple ", "yew ", "magic "})
        {
            ranges.put(wood + "shortbow", 7);
            ranges.put(wood + "longbow", 9);
        }
        ranges.put("magic shortbow (i)", 7);
        ranges.put("dark bow", 10);
        for (String metal : new String[] {"bronze", "iron", "steel", "black", "mithril", "adamant", "rune", "dragon", "amethyst"})
        {
            ranges.put(metal + " dart", 3);
            ranges.put(metal + " knife", 4);
            ranges.put(metal + " thrownaxe", 4);
        }
        for (String metal : new String[] {"bronze", "iron", "steel", "mithril", "adamant", "rune", "dragon"})
        {
            ranges.put(metal + " crossbow", 7);
        }
        ranges.put("crossbow", 7);
        ranges.put("armadyl crossbow", 7);
        ranges.put("zaryte crossbow", 7);
        ranges.put("dorgeshuun crossbow", 6);
        ranges.put("karil's crossbow", 8);
        ranges.put("hunter's crossbow", 4);
        ranges.put("sunlight hunter crossbow", 4);
        ranges.put("toxic blowpipe", 5);
        ranges.put("light ballista", 9);
        ranges.put("heavy ballista", 9);
        ranges.put("chinchompa", 4);
        ranges.put("red chinchompa", 4);
        ranges.put("black chinchompa", 4);
        ranges.put("toktz-xil-ul", 7);
        return Map.copyOf(ranges);
    }
}
