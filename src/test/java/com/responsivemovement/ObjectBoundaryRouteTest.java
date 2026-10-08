package com.responsivemovement;

import java.util.List;
import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Geometry-only guards for the shared bounded object-perimeter search. */
public class ObjectBoundaryRouteTest
{
    private static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static final ObjectApproach OBJECT = ObjectApproach.bounds(tile(40, 40), tile(42, 42));
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;

    @Test
    public void equalLengthBoundaryRoutesPreferTheEarlierCardinalPrefix()
    {
        LocalPoint start = tile(45, 45);
        assertEquals(tile(42, 43), OBJECT.goal(start, OBJECT.min, OPEN));
        for (LocalPoint boundary : new LocalPoint[] {tile(40, 39), tile(42, 43), tile(39, 41), tile(43, 42)})
        {
            assertEquals("an adjacent start must not move sideways", boundary, OBJECT.goal(boundary, OBJECT.min, OPEN));
        }
    }

    @Test
    public void blockedNearestSideUsesACheckedAccessibleBoundary()
    {
        BiPredicate<LocalPoint, LocalPoint> blockedEast = (a, b) ->
            !(b.getSceneX() == 43 && b.getSceneY() >= 40 && b.getSceneY() <= 42);
        LocalPoint start = tile(45, 41), goal = OBJECT.goal(start, OBJECT.min, blockedEast);
        assertNotNull(goal);
        assertFalse(goal.getSceneX() == 43 && goal.getSceneY() >= 40 && goal.getSceneY() <= 42);
        assertNull(OBJECT.goal(start, OBJECT.min, (a, b) -> false));
    }

    @Test
    public void boundarySearchRetainsVisitedStepAndReverseEdgeBounds()
    {
        int[] calls = {0};
        assertNull(MovementRoute.boundary(tile(45, 41), OBJECT.min, OBJECT.max, 1, OPEN));
        assertNull(OBJECT.goal(tile(45, 41), OBJECT.min, (a, b) -> b.getX() < a.getX()));
        assertNull(MovementRoute.boundary(tile(0, 0), tile(100, 100), tile(102, 102), 64,
            (a, b) -> { ++calls[0]; return true; }));
        assertTrue("bounded collision work", calls[0] < MovementRoute.MAX_VISITED * 16);
    }

    @Test
    public void outsideNativeGoalsAndWallsRetainTheirExistingPolicy()
    {
        LocalPoint exact = tile(48, 46), start = tile(45, 44);
        assertEquals(exact, OBJECT.goal(start, exact, (a, b) -> false));
        assertNull(OBJECT.goal(start, null, OPEN));
        assertNull(OBJECT.goal(OBJECT.min, OBJECT.min, OPEN));
    }

    @Test
    public void reachablePerimetersWorkAcrossSidesAndTranslatedRectangles()
    {
        for (int[] start : new int[][] {{45, 45}, {37, 45}, {45, 37}, {37, 37}})
        {
            List<LocalPoint> route = MovementRoute.boundary(tile(start[0], start[1]), OBJECT.min, OBJECT.max, 64,
                (a, b) -> !OBJECT.contains(a) && !OBJECT.contains(b));
            assertNotNull(route); assertEquals(3, route.size());
            LocalPoint goal = route.get(route.size() - 1);
            assertEquals(goal, OBJECT.goal(tile(start[0], start[1]), OBJECT.min, OPEN));
            assertFalse(OBJECT.contains(goal));
        }
    }
}
