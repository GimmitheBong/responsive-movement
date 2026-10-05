package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class ObjectApproachTest
{
    private static LocalPoint point(int x, int y) { return new LocalPoint(x, y, 0); }

    // Synthetic 3x3 footprints consistent with the recorded anchors and native
    // endpoints. The old trace did not record scene footprints or collision maps.
    private static final ObjectApproach NORTH = ObjectApproach.bounds(point(5312, 6208), point(5568, 6464));
    private static final ObjectApproach SOUTH = ObjectApproach.bounds(point(5312, 5568), point(5568, 5824));

    private static BiPredicate<LocalPoint, LocalPoint> outside(ObjectApproach object)
    {
        return (a, b) -> !object.contains(a) && !object.contains(b);
    }

    private static void advance(MovementPath path, int from, int to)
    {
        for (int ms = from + 10; ms <= to; ms += 10) { path.advance(ms * 1_000_000L); }
    }

    @Test
    public void alreadyBesideTheFootprintDoesNotWalkToItsAnchorAndBack()
    {
        LocalPoint start = point(5440, 6080);
        assertEquals(start, NORTH.goal(start, NORTH.min));
        assertNull(MovementPath.anticipate(start, start, NORTH.goal(start, NORTH.min), true, 0, 1.1,
            MovementPath.freshDeadline(0), outside(NORTH)));
    }

    @Test
    public void recordedOneTileApproachesStayOnTheirColumnAcrossConfirmation()
    {
        for (int x : new int[] {5440, 5568})
        {
            for (ObjectApproach object : new ObjectApproach[] {NORTH, SOUTH})
            {
                LocalPoint start = point(x, object == NORTH ? 5952 : 6080);
                LocalPoint end = point(x, object == NORTH ? 6080 : 5952);
                LocalPoint goal = object.goal(start, object.min);
                assertEquals(end, goal);
                MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1.1,
                    MovementPath.freshDeadline(0), outside(object));
                assertNotNull(path);
                for (int ms = 10; ms <= 1000; ms += 10)
                {
                    if (ms == 620) { assertTrue(path.accept(end, true)); }
                    path.advance(ms * 1_000_000L);
                    assertEquals("no lateral detour", x, path.position().getX());
                    assertTrue(path.clear());
                    assertTrue(path.position().getY() >= 5952 && path.position().getY() <= 6080);
                }
                assertEquals(end, path.position());
                assertTrue(path.finished());
            }
        }
    }

    @Test
    public void distantObjectStillStartsBeforeTheFirstServerStep()
    {
        LocalPoint start = point(5440, 6976), end = point(5440, 6592);
        LocalPoint goal = NORTH.goal(start, NORTH.min);
        assertEquals(end, goal);
        MovementPath path = MovementPath.anticipate(start, start, goal, true, 0, 1,
            MovementPath.freshDeadline(0), outside(NORTH));
        assertNotNull(path);
        advance(path, 0, 20);
        assertEquals(point(5440, 6968), path.position());
        assertEquals(start, path.confirmed());
    }

    @Test
    public void revisedNativeEndpointOutsideTheObjectIsUsedExactly()
    {
        LocalPoint revised = point(5696, 6336);
        assertEquals(revised, NORTH.goal(point(5440, 6080), revised));
        assertNull(NORTH.goal(point(5440, 6080), null));
    }

    @Test
    public void blockedApproachStillRequiresACheckedRoute()
    {
        LocalPoint start = point(5440, 5952);
        assertNull(MovementPath.anticipate(start, start, NORTH.goal(start, NORTH.min), true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> false));
    }

    @Test
    public void correctedPreviewStillTimesOutAndCancelsContinuously()
    {
        for (boolean cancel : new boolean[] {false, true})
        {
            LocalPoint start = point(5440, 5952);
            MovementPath path = MovementPath.anticipate(start, start, NORTH.goal(start, NORTH.min), true, 0, 1,
                MovementPath.freshDeadline(0), outside(NORTH));
            assertNotNull(path);
            advance(path, 0, 200);
            LocalPoint before = path.position();
            if (cancel) { path.cancel(); }
            assertEquals(before, path.position());
            for (int ms = 210; ms <= 1800; ms += 10)
            {
                before = path.position();
                path.advance(ms * 1_000_000L);
                assertTrue(MovementPath.distance(before, path.position()) <= 2);
                assertTrue(path.clear());
            }
            assertEquals(start, path.position());
        }
    }

    @Test
    public void invalidOrSingleTileBoundsDoNotChangeExistingHandling()
    {
        assertNull(ObjectApproach.bounds(point(5312, 6208), point(5312, 6208)));
        assertNull(ObjectApproach.bounds(point(5440, 6208), point(5312, 6464)));
        assertNull(ObjectApproach.bounds(point(5313, 6208), point(5568, 6464)));
        assertNull(ObjectApproach.bounds(point(5312, 6208), new LocalPoint(5568, 6464, 1)));
    }
}
