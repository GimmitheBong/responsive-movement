package com.responsivemovement;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises the actual menu-to-scene lookup, not only the footprint maths. */
public class ObjectApproachCaptureTest
{
    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void widgetUseKeepsAllFourRecordedHerbPatchPositionsStill()
    {
        // Bounds were recorded during picking immediately before the use clicks.
        int[][] captures = {
            {7232, 5952, 7360, 6080, 7104, 6080}, // 21:39 west, offset from anchor
            {7488, 8128, 7616, 8256, 7488, 8000}, // 21:40 south, beside anchor
            {4800, 6336, 4928, 6464, 4928, 6592}, // 21:41 north
            {4416, 6976, 4544, 7104, 4672, 6976}}; // 21:43 east
        for (int[] c : captures)
        {
            Fixture f = new Fixture(point(c[0], c[1]), point(c[2], c[3]));
            for (int repeat = 0; repeat < 2; ++repeat) // planting then compost
            {
                ObjectApproach object = ObjectApproach.capture(f.view, f.click(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT));
                assertNotNull("use action must capture the same patch footprint as picking", object);
                LocalPoint start = point(c[4], c[5]);
                LocalPoint goal = object.goal(start, f.min);
                assertEquals(start, goal);
                assertNull(MovementPath.anticipate(start, start, goal, true, 0, 1.1,
                    MovementPath.freshDeadline(0), (a, b) -> !object.contains(a) && !object.contains(b)));
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void allObjectUseAndOptionActionsShareFootprintHandlingOnEverySide()
    {
        Fixture f = new Fixture(point(4800, 6336), point(4928, 6464));
        MenuAction[] actions = {MenuAction.GAME_OBJECT_FIRST_OPTION, MenuAction.GAME_OBJECT_SECOND_OPTION,
            MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
            MenuAction.GAME_OBJECT_FIFTH_OPTION, MenuAction.ITEM_USE_ON_GAME_OBJECT,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT};
        LocalPoint[] sides = {point(4672, 6336), point(4672, 6464), point(5056, 6336), point(5056, 6464),
            point(4800, 6208), point(4928, 6208), point(4800, 6592), point(4928, 6592)};
        for (MenuAction action : actions)
        {
            ObjectApproach object = ObjectApproach.capture(f.view, f.click(action));
            assertNotNull(action.name(), object);
            for (LocalPoint side : sides) { assertEquals(action.name(), side, object.goal(side, f.min)); }
        }
    }

    @Test
    public void distantUseStillPredictsACheckedApproachAndHonoursNativeRefinement()
    {
        Fixture f = new Fixture(point(4800, 6336), point(4928, 6464));
        ObjectApproach object = ObjectApproach.capture(f.view, f.click(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT));
        assertNotNull(object);
        LocalPoint start = point(4928, 6976), end = point(4928, 6592);
        LocalPoint goal = object.goal(start, f.min);
        assertEquals(end, goal);
        MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> !object.contains(a) && !object.contains(b));
        assertNotNull(path);
        path.advance(20_000_000L);
        assertEquals(point(4928, 6968), path.position());
        assertEquals(start, path.confirmed());
        LocalPoint revised = point(5056, 6464);
        assertEquals(revised, object.goal(start, revised));
        assertNull(MovementPath.anticipate(start, start, goal, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> false));
    }

    @Test
    public void captureIsFreshForEachTargetAndRetainsGeometryAndIdentityGuards()
    {
        Fixture f = new Fixture(point(4800, 6336), point(4928, 6464));
        MenuOptionClicked click = f.click(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT);
        assertNotNull(ObjectApproach.capture(f.view, click));
        f.objectValues.put("getId", 999);
        assertNull(ObjectApproach.capture(f.view, click));
        f.objectValues.put("getId", 123);
        f.objectValues.put("getConfig", 9); // diagonal wall, not a rectangle
        assertNull(ObjectApproach.capture(f.view, click));
        f.objectValues.put("getConfig", 10);
        f.flags[f.min.getSceneX()][f.min.getSceneY()] = 0; // walkable object
        assertNull(ObjectApproach.capture(f.view, click));
        f.flags[f.min.getSceneX()][f.min.getSceneY()] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
        f.menuValues.put("getWorldViewId", 1);
        assertNull(ObjectApproach.capture(f.view, click));
        f.menuValues.put("getWorldViewId", 0);
        f.objectValues.put("getSceneMaxLocation", f.objectValues.get("getSceneMinLocation"));
        assertNull(ObjectApproach.capture(f.view, click)); // single-tile handling retained
    }

    @Test
    public void npcGroundItemWalkAndInventoryActionsNeverReadTheObjectScene()
    {
        Fixture f = new Fixture(point(4800, 6336), point(4928, 6464));
        WorldView unreadable = stub(WorldView.class, Map.of());
        for (MenuAction action : new MenuAction[] {MenuAction.NPC_FIRST_OPTION, MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.WIDGET_TARGET_ON_PLAYER, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            MenuAction.WALK, MenuAction.CC_OP, MenuAction.WIDGET_TARGET_ON_WIDGET})
        {
            assertNull(ObjectApproach.capture(unreadable, f.click(action)));
        }
    }

    private static final class Fixture
    {
        final LocalPoint min;
        final WorldView view;
        final int[][] flags = new int[104][104];
        final Map<String, Object> objectValues = new HashMap<>();
        final Map<String, Object> menuValues = new HashMap<>();

        Fixture(LocalPoint min, LocalPoint max)
        {
            this.min = min;
            Tile[][][] tiles = new Tile[4][104][104];
            CollisionData map = stub(CollisionData.class, Map.of("getFlags", flags));
            flags[min.getSceneX()][min.getSceneY()] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
            view = stub(WorldView.class, Map.of("getId", 0, "getPlane", 0, "getSizeX", 104, "getSizeY", 104,
                "getScene", stub(Scene.class, Map.of("getTiles", tiles)),
                "getCollisionMaps", new CollisionData[] {map}));
            objectValues.put("getId", 123);
            objectValues.put("getPlane", 0);
            objectValues.put("getWorldView", view);
            objectValues.put("getConfig", 10);
            objectValues.put("getSceneMinLocation", new Point(min.getSceneX(), min.getSceneY()));
            objectValues.put("getSceneMaxLocation", new Point(max.getSceneX(), max.getSceneY()));
            GameObject object = stub(GameObject.class, objectValues);
            tiles[0][min.getSceneX()][min.getSceneY()] = stub(Tile.class,
                Map.of("getGameObjects", new GameObject[] {null, object}));
            menuValues.put("getIdentifier", 123);
            menuValues.put("getParam0", min.getSceneX());
            menuValues.put("getParam1", min.getSceneY());
            menuValues.put("getWorldViewId", 0);
            menuValues.put("getOption", "Open");
        }

        MenuOptionClicked click(MenuAction action)
        {
            menuValues.put("getType", action);
            return new MenuOptionClicked(stub(MenuEntry.class, menuValues));
        }
    }

    // Test-only API doubles. No reflective access or proxies in plugin code.
    private static <T> T stub(Class<T> api, Map<String, Object> values)
    {
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) ->
        {
            if (api == Tile.class && method.getName().equals("getWallObject")) { return null; }
            if (!values.containsKey(method.getName())) { throw new AssertionError("Unexpected API call: " + method.getName()); }
            return values.get(method.getName());
        }));
    }
}
