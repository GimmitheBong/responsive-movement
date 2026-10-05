package com.responsivemovement;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

/** Explicit Follow identity/engagement only; no predicted target route or movement clock. */
final class FollowPresentation
{
    private final Actor target;
    private final WorldView view;
    private final int plane;
    private final int index;
    private final int definition;
    private final long clicked;
    private boolean engaged;
    boolean nativeOwned;

    private FollowPresentation(Actor target, WorldView view, int index, long now)
    {
        this.target = target; this.view = view; this.index = index;
        plane = view.getPlane(); definition = target instanceof NPC ? ((NPC) target).getId() : -1;
        clicked = now;
    }

    static FollowPresentation capture(Player owner, MenuOptionClicked event, long now)
    {
        String option = event.getMenuOption();
        if (owner == null || option == null || !"Follow".equalsIgnoreCase(Text.removeTags(option).trim())) { return null; }
        boolean npc;
        switch (event.getMenuAction())
        {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION: case NPC_FIFTH_OPTION: npc = true; break;
            case PLAYER_FIRST_OPTION: case PLAYER_SECOND_OPTION: case PLAYER_THIRD_OPTION: case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION: case PLAYER_SIXTH_OPTION: case PLAYER_SEVENTH_OPTION: case PLAYER_EIGHTH_OPTION:
                npc = false; break;
            default: return null;
        }
        WorldView view = owner.getWorldView();
        if (view == null || event.getMenuEntry().getWorldViewId() != view.getId()) { return null; }
        Actor target = npc ? event.getMenuEntry().getNpc() : event.getMenuEntry().getPlayer();
        if (target == null || target == owner || target.getWorldView() != view || target.isDead()) { return null; }
        int index = npc ? ((NPC) target).getIndex() : ((Player) target).getId();
        if (event.getId() != index || target.getWorldLocation() == null || target.getWorldLocation().getPlane() != view.getPlane()) { return null; }
        return new FollowPresentation(target, view, index, now);
    }

    boolean same(FollowPresentation other)
    {
        return other != null && target == other.target && view == other.view && plane == other.plane && definition == other.definition;
    }

    boolean observe(Player player, WorldView current, long now)
    {
        if (current != view || view.getPlane() != plane || target.getWorldView() != view || target.isDead() ||
            target.getWorldLocation() == null || target.getWorldLocation().getPlane() != plane) { return false; }
        if (target instanceof NPC ? view.npcs().byIndex(index) != target || ((NPC) target).getId() != definition :
            view.players().byIndex(index) != target) { return false; }
        Actor interacting = player.getInteracting();
        if (interacting == target)
        {
            // A rejected/pending click cannot acquire ownership long after its window.
            if (!engaged && now - clicked >= 1_800_000_000L) { return false; }
            engaged = true;
        }
        else if (engaged || interacting != null || now - clicked >= 1_800_000_000L) { return false; }
        return true;
    }

    boolean engaged() { return engaged; }
}
