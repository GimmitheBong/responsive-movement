package com.responsivemovement;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.coords.LocalPoint;

/** Draw-time native model provider; never retains an actor's temporary posed mesh. */
final class NativeModelObject extends RuneLiteObjectController
{
    private final Client client;
    private Renderable source;
    private boolean available;
    private int builderIdle = -1;

    NativeModelObject(Client client) { this.client = client; }

    boolean prepare(Renderable renderable, boolean visible)
    {
        return prepare(renderable, visible, -1);
    }

    boolean prepare(Renderable renderable, boolean visible, int idleSelector)
    {
        assert client.isClientThread();
        // Probe before suppressing the native body. Discard the returned model:
        // other native builders may reuse its storage before this object draws.
        available = renderable != null && build(renderable, idleSelector) != null;
        source = available && visible ? renderable : null;
        builderIdle = source == null ? -1 : idleSelector;
        return available;
    }

    @Override
    public Model getModel()
    {
        // This is the temporary-object model provider, not a RenderCallback.
        // RuneLiteObjectController permits shared models for immediate drawing.
        // Never inspect the actor/effect from a map-loader or render-worker thread.
        if (!client.isClientThread()) { return null; }
        Renderable current = source;
        return current == null ? null : build(current, builderIdle);
    }

    private Model build(Renderable renderable, int idleSelector)
    {
        if (idleSelector < 0 || !(renderable instanceof Player)) { return renderable.getModel(); }
        Player player = (Player) renderable;
        int original = player.getIdlePoseAnimation();
        try
        {
            if (original != idleSelector) { player.setIdlePoseAnimation(idleSelector); }
            return player.getModel();
        }
        finally
        {
            if (original != idleSelector) { player.setIdlePoseAnimation(original); }
        }
    }

    @Override
    public void setLocation(LocalPoint location, int plane)
    {
        boolean reregister = isActive() && location.getWorldView() != getWorldView();
        // A view transfer re-registers the same prepared provider; hide() is
        // reserved for an actual handoff, invalidation or removal.
        if (reregister) { client.removeRuneLiteObject(this); }
        super.setLocation(location, plane);
        if (reregister) { setActive(true); }
    }

    boolean ready() { return available; }
    void hide() { source = null; available = false; builderIdle = -1; }
    boolean isActive() { return client.isRuneLiteObjectRegistered(this); }
    void setActive(boolean active)
    {
        if (active) { client.registerRuneLiteObject(this); }
        else { hide(); client.removeRuneLiteObject(this); }
    }
}
