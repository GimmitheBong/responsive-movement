package com.responsivemovement;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public final class ResponsiveMovementLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ResponsiveMovementPlugin.class);
        RuneLite.main(args);
    }
}
