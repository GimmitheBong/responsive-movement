package com.responsivemovement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/** Bounded collision search for a click or a prediction correction, never a scene scan. */
final class MovementRoute
{
    static final int MAX_VISITED = 2048;
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},
        {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    private MovementRoute() { }

    static List<LocalPoint> find(LocalPoint from, LocalPoint to, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        return find(from, to, maxSteps, collision, false);
    }

    static List<LocalPoint> find(LocalPoint from, LocalPoint to, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision, boolean approach)
    {
        int tile = Perspective.LOCAL_TILE_SIZE;
        if (!MovementPath.sameView(from, to) || !MovementPath.tileCenter(from) || !MovementPath.tileCenter(to) ||
            maxSteps < 0 || MovementPath.distance(from, to) > maxSteps * tile) { return null; }
        return search(from, maxSteps, collision, point -> reached(point, to, approach));
    }

    /** First reachable cardinal perimeter tile, using the same bounded reversible search. */
    static List<LocalPoint> boundary(LocalPoint from, LocalPoint min, LocalPoint max, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision)
    {
        if (!MovementPath.sameView(from, min) || !MovementPath.sameView(min, max) ||
            !MovementPath.tileCenter(from) || !MovementPath.tileCenter(min) || !MovementPath.tileCenter(max) ||
            min.getX() > max.getX() || min.getY() > max.getY() || maxSteps < 0) { return null; }
        return search(from, maxSteps, collision, point ->
        {
            int dx = Math.max(0, Math.max(min.getX() - point.getX(), point.getX() - max.getX()));
            int dy = Math.max(0, Math.max(min.getY() - point.getY(), point.getY() - max.getY()));
            return dx + dy == Perspective.LOCAL_TILE_SIZE;
        });
    }

    private static List<LocalPoint> search(LocalPoint from, int maxSteps,
        BiPredicate<LocalPoint, LocalPoint> collision, Predicate<LocalPoint> reached)
    {
        int tile = Perspective.LOCAL_TILE_SIZE;
        if (reached.test(from)) { return Collections.emptyList(); }
        Map<LocalPoint, LocalPoint> parent = new HashMap<>();
        ArrayDeque<LocalPoint> queue = new ArrayDeque<>();
        parent.put(from, from);
        queue.add(from);
        for (int depth = 0; depth < maxSteps && !queue.isEmpty(); ++depth)
        {
            int count = queue.size();
            for (int i = 0; i < count; ++i)
            {
                LocalPoint current = queue.removeFirst();
                for (int[] direction : DIRECTIONS)
                {
                    LocalPoint next = new LocalPoint(current.getX() + direction[0] * tile,
                        current.getY() + direction[1] * tile, from.getWorldView());
                    if (parent.containsKey(next) || !collision.test(current, next) || !collision.test(next, current)) { continue; }
                    parent.put(next, current);
                    if (reached.test(next))
                    {
                        List<LocalPoint> route = new ArrayList<>();
                        for (LocalPoint point = next; !point.equals(from); point = parent.get(point)) { route.add(point); }
                        Collections.reverse(route);
                        return route;
                    }
                    if (parent.size() >= MAX_VISITED) { return null; }
                    queue.addLast(next);
                }
            }
        }
        return null;
    }

    private static boolean reached(LocalPoint point, LocalPoint target, boolean approach)
    {
        return point.equals(target) || approach &&
            Math.abs(point.getX() - target.getX()) + Math.abs(point.getY() - target.getY()) == Perspective.LOCAL_TILE_SIZE;
    }
}
