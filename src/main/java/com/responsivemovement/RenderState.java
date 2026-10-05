package com.responsivemovement;

import net.runelite.api.Player;

/** Immutable publication; render callbacks never inspect live controllers. */
final class RenderState
{
    static final RenderState NATIVE = new RenderState(null, -1, -1, false, false);
    final Player player;
    final int playerId;
    final int worldView;
    final boolean hideBody;
    final boolean hideUi;

    RenderState(Player player, int playerId, int worldView, boolean hideBody, boolean hideUi)
    {
        this.player = player; this.playerId = playerId; this.worldView = worldView;
        this.hideBody = hideBody; this.hideUi = hideUi;
    }

    static boolean isPlayer(long hash) { return ((hash >>> 16) & 7L) == 0; }

    boolean drawObject(long hash, int id)
    {
        return !hideBody || !isPlayer(hash) || id != playerId || ((hash >>> 52) & 4095L) != worldView;
    }
}
