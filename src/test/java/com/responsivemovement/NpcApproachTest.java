package com.responsivemovement;

import java.util.function.BiPredicate;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class NpcApproachTest
{
    private static final BiPredicate<LocalPoint, LocalPoint> OPEN = (a, b) -> true;
    private static LocalPoint p(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }

    @Test
    public void straightFirstPrefixStopsOutsideSingleAndMultiTileNpcs()
    {
        LocalPoint from = p(10, 10);
        assertEquals(p(14, 12), NpcApproach.goal(from, p(15, 13), p(15, 13), OPEN));
        assertEquals(p(14, 10), NpcApproach.goal(from, p(15, 9), p(17, 11), OPEN));
        assertNull(NpcApproach.goal(p(14, 10), p(15, 9), p(17, 11), OPEN));
        assertNull(NpcApproach.goal(p(14, 8), p(15, 9), p(17, 11), OPEN));
        assertNull(NpcApproach.goal(p(16, 10), p(15, 9), p(17, 11), OPEN));
    }

    @Test
    public void counterStopsThePrefixWithoutInventingADetourOrCrossingAWall()
    {
        BiPredicate<LocalPoint, LocalPoint> counter = (a, b) -> a.getSceneX() < 14 && b.getSceneX() < 14;
        assertEquals(p(13, 10), NpcApproach.goal(p(10, 10), p(15, 10), p(15, 10), counter));
        assertNull(NpcApproach.goal(p(13, 10), p(15, 10), p(15, 10), counter));
        assertNull(NpcApproach.goal(p(10, 10), p(15, 10), p(15, 10), (a, b) -> b.getX() > a.getX()));
        assertNull(NpcApproach.goal(p(10, 10), p(15, 10), p(15, 10), (a, b) -> false));
    }

    @Test
    public void unsupportedCoordinatesAndLongRoutesDoNotStart()
    {
        assertNull(NpcApproach.goal(p(10, 10), p(80, 10), p(80, 10), OPEN));
        assertNull(NpcApproach.goal(new LocalPoint(1345, 1344, 0), p(15, 10), p(15, 10), OPEN));
        assertNull(NpcApproach.goal(new LocalPoint(1344, 1344, 1), p(15, 10), p(15, 10), OPEN));
    }

    @Test
    public void provisionalMovementKeepsExistingTimeoutAndReconcilesToAuthority()
    {
        LocalPoint from = p(10, 10), npc = p(16, 10);
        MovementPath path = MovementPath.anticipate(from, from, NpcApproach.goal(from, npc, npc, OPEN), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        assertNotNull(path);
        for (int ms = 20; ms <= 480; ms += 20) { path.advance(ms * 1_000_000L); }
        assertTrue(path.position().getX() > from.getX());
        assertEquals(from, path.confirmed());
        assertTrue(path.accept(p(12, 10), true));
        for (int ms = 500; ms <= 2200; ms += 20) { path.advance(ms * 1_000_000L); }
        assertEquals(p(12, 10), path.position());
        assertTrue(path.finished());
    }

    @Test
    public void nativeDestinationCanExtendACompletedProvisionalPrefixWithoutSnappingOrNewTime()
    {
        MovementPath path = MovementPath.anticipate(p(10, 10), p(10, 10), p(11, 10), true,
            0, 1, MovementPath.freshDeadline(0), OPEN);
        for (int ms = 20; ms <= 700; ms += 20) { path.advance(ms * 1_000_000L); }
        assertFalse(path.moving());
        assertEquals("preview", path.phase());
        MovementPath refined = path.retargetNativeApproach(p(14, 10), true, 700_000_000L);
        assertNotNull(refined);
        assertEquals(path.position(), refined.position());
        assertEquals(path.confirmed(), refined.confirmed());
        refined.advance(901_000_000L);
        assertEquals("recovery", refined.phase());
        assertNull(path.retargetNativeApproach(p(14, 10), true, 901_000_000L));
    }
}
