package com.responsivemovement;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Player;

/** Bounded metadata for effects observed while leaving native combat; no models or movement state. */
final class CombatEffectCarry
{
    private static final int MAX_EFFECTS = 8;
    private static final long LIFETIME_NANOS = 900_000_000L;
    private final List<Effect> effects;
    private final long deadline;

    private CombatEffectCarry(List<Effect> effects, long now)
    {
        this.effects = List.copyOf(effects); deadline = now + LIFETIME_NANOS;
    }

    static CombatEffectCarry capture(Player player, int cycle, long now)
    {
        IterableHashTable<ActorSpotAnim> table = player.getSpotAnims();
        if (table == null) { return null; }
        List<Effect> effects = new ArrayList<>();
        int visited = 0;
        for (ActorSpotAnim effect : table)
        {
            if (++visited > MAX_EFFECTS) { return null; }
            if (active(effect, cycle)) { effects.add(new Effect(effect)); }
        }
        return effects.isEmpty() ? null : new CombatEffectCarry(effects, now);
    }

    boolean allows(Player player, int cycle, long now) { return now < deadline && containsOnly(player, cycle); }

    boolean containsOnly(Player player, int cycle)
    {
        IterableHashTable<ActorSpotAnim> table = player.getSpotAnims();
        if (table == null) { return false; }
        boolean found = false;
        int visited = 0;
        for (ActorSpotAnim current : table)
        {
            if (++visited > MAX_EFFECTS) { return false; }
            if (!active(current, cycle)) { continue; }
            found = true;
            boolean matched = false;
            for (Effect effect : effects) { matched |= effect.matches(current); }
            if (!matched) { return false; }
        }
        return found;
    }

    private static boolean active(ActorSpotAnim effect, int cycle)
    {
        return effect != null && effect.getId() != -1 && effect.getFrame() >= 0 && effect.getStartCycle() <= cycle;
    }

    /** Value-only click diagnostics, including pending effects; formatting remains on the writer. */
    static TraceSnapshot snapshot(Player player)
    {
        long[] values = new long[MAX_EFFECTS * 4];
        int count = 0;
        int visited = 0;
        boolean complete = true;
        IterableHashTable<ActorSpotAnim> table = player == null ? null : player.getSpotAnims();
        if (table != null)
        {
            for (ActorSpotAnim effect : table)
            {
                if (++visited > MAX_EFFECTS) { complete = false; break; }
                if (effect == null) { continue; }
                values[count++] = effect.getId(); values[count++] = effect.getHash();
                values[count++] = effect.getStartCycle(); values[count++] = effect.getFrame();
            }
        }
        return new TraceSnapshot(java.util.Arrays.copyOf(values, count), complete);
    }

    static final class TraceSnapshot
    {
        final long[] values;
        final boolean complete;
        TraceSnapshot(long[] values, boolean complete) { this.values = values; this.complete = complete; }
    }

    private static final class Effect
    {
        final ActorSpotAnim source;
        final int id, startCycle;
        final long hash;

        Effect(ActorSpotAnim effect)
        {
            source = effect; id = effect.getId(); startCycle = effect.getStartCycle(); hash = effect.getHash();
        }

        boolean matches(ActorSpotAnim effect)
        {
            return source == effect && id == effect.getId() && startCycle == effect.getStartCycle() && hash == effect.getHash();
        }
    }
}
