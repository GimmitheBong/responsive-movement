package com.responsivemovement;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.*;

/** API-double checks: the native loading renderer itself requires in-game validation. */
public class SceneCameraTest
{
    @Test
    public void markerUsesOnlyTheFixedOrbAndUnregistersOnDisableAlignmentAndShutdown()
    {
        Fixture f = new Fixture();
        PresentationCamera camera = new PresentationCamera(f.client, new ResponsiveMovementConfig() {
            @Override public boolean adaptiveCamera() { return false; }
            @Override public boolean cameraMarker() { return f.markerEnabled; }
        });
        LocalPoint visible = new LocalPoint(7232, 1878, 0);
        camera.prepare(f.player, visible, false);
        assertEquals(1, f.objects.size());
        assertEquals(3351, f.loadedModel);
        f.markerEnabled = false;
        camera.prepare(f.player, visible, false);
        assertTrue(f.objects.isEmpty());
        f.markerEnabled = true;
        camera.prepare(f.player, visible, false);
        assertEquals(1, f.objects.size());
        camera.prepare(f.player, f.player.getLocalLocation(), false);
        assertTrue(f.objects.isEmpty());
        camera.prepare(f.player, visible, false);
        assertEquals(1, f.objects.size());
        camera.close();
        assertTrue(f.objects.isEmpty());
    }

    @Test
    public void rebaseRetainsWorldRelativeFocusAndRestoresNativeInputMode()
    {
        Fixture f = new Fixture();
        PresentationCamera camera = new PresentationCamera(f.client, new ResponsiveMovementConfig() {});
        camera.prepare(f.player, new LocalPoint(7104, 1878, 0), false);
        float oldX = f.focus[0], oldY = f.focus[1], oldZ = f.focus[2];
        assertEquals(1, f.mode);
        camera.afterDraw();
        assertEquals(0, f.mode);
        camera.sceneChanged();
        assertEquals(0, f.mode);

        // The native client can move its own camera during loading. Resumption
        // must use the retained world focus, not adopt that unrelated focal point.
        f.baseX -= 8;
        f.baseY -= 40;
        f.focus[0] = f.focus[2] = 3000;
        camera.prepare(f.player, new LocalPoint(8128, 6998, 0), false);
        assertEquals(oldX + 1024, f.focus[0], 0.01);
        assertEquals(oldZ + 5120, f.focus[2], 0.01);
        assertEquals(oldY, f.focus[1], 0.01);
        assertEquals(1, f.mode);
        camera.beforeInput();
        assertEquals(0, f.mode);
        camera.close();
        assertEquals(0, f.mode);
    }

    private interface Call { Object invoke(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (object, method, args) -> call.invoke(method.getName(), args)));
    }

    private static final class Fixture
    {
        int baseX = 3200, baseY = 3200, mode;
        boolean markerEnabled = true;
        int loadedModel;
        final Set<RuneLiteObjectController> objects = Collections.newSetFromMap(new IdentityHashMap<>());
        final float[] focus = {7104, -25, 1878};
        final int[][][] heights = new int[4][105][105];
        final byte[][][] settings = new byte[4][104][104];
        final WorldView view = proxy(WorldView.class, (method, args) -> {
            switch (method)
            {
                case "getId": case "getPlane": return 0;
                case "getBaseX": return baseX;
                case "getBaseY": return baseY;
                case "getSizeX": case "getSizeY": return 104;
                case "getTileHeights": return heights;
                case "getTileSettings": return settings;
                default: throw new AssertionError("Unexpected view access: " + method);
            }
        });
        final Player player = proxy(Player.class, (method, args) -> {
            switch (method)
            {
                case "getWorldView": return view;
                case "getWorldLocation": return new WorldPoint(3255, 3217, 0);
                case "getLocalLocation": return new LocalPoint(7104, 1878, 0);
                case "getFootprintSize": return 1;
                case "getAnimationHeightOffset": return 0;
                default: throw new AssertionError("Unexpected player access: " + method);
            }
        });
        final Client client = proxy(Client.class, (method, args) -> {
            switch (method)
            {
                case "getTopLevelWorldView": case "getWorldView": return view;
                case "createRuneLiteObject": return new RuneLiteObject(this.client);
                case "loadModel":
                    loadedModel = (int) args[0];
                    return proxy(Model.class, (methodName, values) -> { throw new AssertionError("marker mesh must not be modified"); });
                case "isRuneLiteObjectRegistered": return objects.contains(args[0]);
                case "registerRuneLiteObject": objects.add((RuneLiteObjectController) args[0]); return null;
                case "removeRuneLiteObject": objects.remove(args[0]); return null;
                case "getCameraMode": return mode;
                case "setCameraMode": mode = (int) args[0]; return null;
                case "setFreeCameraSpeed": assertEquals(0, args[0]); return null;
                case "getCameraFocalPointX": return focus[0];
                case "getCameraFocalPointY": return focus[1];
                case "getCameraFocalPointZ": return focus[2];
                case "setCameraFocalPointX": focus[0] = (float) args[0]; return null;
                case "setCameraFocalPointY": focus[1] = (float) args[0]; return null;
                case "setCameraFocalPointZ": focus[2] = (float) args[0]; return null;
                case "getVarcIntValue": return 0;
                case "getViewportHeight": return 334;
                default: throw new AssertionError("Unexpected client access: " + method);
            }
        });
    }
}
