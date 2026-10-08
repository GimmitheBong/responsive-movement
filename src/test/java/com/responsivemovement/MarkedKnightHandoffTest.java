package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarkedKnightHandoffTest
{
    private static LocalPoint p(int x, int y) { return new LocalPoint(x, y, 0); }

    @Test
    public void all81CapturedClicksAndEveryMarkedPauseKeepContinuousCheckedTravel() throws Exception
    {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("marked-knights-2333.txt"),StandardCharsets.UTF_8)))
        {
            reader.lines().filter(s->!s.startsWith("#")).forEach(s->rows.add(s.split(" ")));
        }
        long[] pauses = {12_273_389,18_213_976,24_855_908,33_175_909,57_691_615};
        LocalPoint[] stops = {p(5952,6976),p(6080,6720),p(6080,6720),p(6080,6720),p(6080,6720)};
        for (long cadence : new long[] {8_333,20_000,33_333})
        {
            Fixture f = new Fixture(6208,6976,8000,8000);
            f.speed = 1.08; f.turnSpeed = 30; f.smoothing = 0; f.originalWhenAligned = false;
            f.nativeOrientation = 1280; f.controller.close(); f.controller.update(); f.controller.presented();
            for (int[] column : f.flags) { Arrays.fill(column,CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
            List<String[]> events = new ArrayList<>();
            for (String[] row : rows)
            {
                if (!row[0].equals("M")) { events.add(row); continue; }
                String[] flags = row[2].split(",");
                for (int y = 0; y < flags.length; ++y) { f.flags[Integer.parseInt(row[1])][50+y] = Integer.parseUnsignedInt(flags[y],16); }
            }
            TreeSet<Long> times = new TreeSet<>();
            for (long t = 0; t <= 60_673_814; t += cadence) { times.add(t); }
            for (String[] event : events) { times.add(Long.parseLong(event[1])); }
            LocalPoint previous = f.start;
            long previousTime = 0;
            int next = 0, clicks = 0;
            for (long t : times)
            {
                f.now = 1_000_000_000L + t*1000; f.cycle = 100+(int)(t/20_000);
                while (next < events.size() && Long.parseLong(events.get(next)[1]) <= t)
                {
                    String[] e = events.get(next++);
                    if (e[0].equals("K")) { ++clicks; f.controller.walkClick(); }
                    else
                    {
                        f.authority = p(Integer.parseInt(e[2]),Integer.parseInt(e[3]));
                        f.destination = Integer.parseInt(e[4]) < 0 ? null : p(Integer.parseInt(e[4]),Integer.parseInt(e[5]));
                        f.nativePoint = p(Integer.parseInt(e[6]),Integer.parseInt(e[7]));
                    }
                }
                int angle = f.controller.orientation(); f.controller.update();
                LocalPoint actual = f.controller.position();
                assertTrue("one captured movement budget at " + t, MovementPath.distance(previous,actual) <= Math.ceil((t-previousTime)*0.00044)+1);
                assertTrue("one captured turn budget at " + t, Math.abs(MotionMath.difference(angle,f.controller.orientation())) <= Math.ceil(30*(t-previousTime)/16_667.0)+1);
                if (t >= 23_795_142 && t < 24_353_369)
                {
                    assertTrue("no 50-ms rubber-band on late origin authority at " + t, squared(actual,p(6208,6976)) <= squared(previous,p(6208,6976))+2);
                }
                if (t >= 16_603_436 && t < 17_075_265)
                {
                    assertTrue("a visible-idle return click keeps its newest itinerary", squared(actual,p(6080,6720)) <= squared(previous,p(6080,6720))+2);
                }
                for (int i = 0; i < pauses.length; ++i)
                {
                    if (t >= pauses[i]+300_000 && t <= pauses[i]+2_000_000)
                    {
                        assertEquals("hold the marked stop rather than inventing a return trip at " + t,stops[i],actual);
                    }
                }
                previous = actual; previousTime = t;
            }
            assertEquals(81,clicks); assertEquals(p(6080,6720),f.controller.position()); f.controller.close();
        }
    }

    @Test
    public void aClickedKnightFromTheNearVisibleEndpointDoesNotUseAnInventedStagingCorner()
    {
        LocalPoint authority = p(6208,6976), valley = p(6080,6720), goal = p(5952,6976);
        MovementPath path = MovementPath.anticipate(authority,authority,valley,true,0,1.1,
            MovementPath.freshDeadline(0),(a,b)->true);
        for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
        LocalPoint before = path.position(); long deadline = path.predictionDeadlineNanos();
        MovementPath next = path.retargetWalk(goal,true,560_000_000L);
        assertNotNull(next); assertEquals(before,next.position()); assertEquals(deadline,next.predictionDeadlineNanos());
        assertTrue("the near visible endpoint owns the full knight route", next.traceSnapshot().fields(0).contains("logical=6080,6720;6080,6848;5952,6976"));
        assertTrue("retain the whole checked chord", next.traceSnapshot().fields(0).contains("6080,6720,5952,6976,5952,6976,1,1"));
        for (int ms = 580; ms <= 1000; ms += 20)
        {
            if (ms == 600) { assertTrue(next.accept(valley,true)); }
            if (ms == 980) { assertTrue(next.accept(goal,true)); }
            next.advance(ms*1_000_000L);
            assertTrue("same straight displayed itinerary through late authority", offLine(before,goal,next.position()) <= 1.5);
            assertTrue(next.clear());
        }
    }

    @Test
    public void adjacentCheckedKnightCorridorsJoinWithoutReturningToTheirSharedOrigin()
    {
        LocalPoint from = p(5952,6976), valley = p(6080,6720), goal = p(6208,6976);
        MovementPath path = MovementPath.anticipate(from,from,valley,true,0,1.1,
            MovementPath.freshDeadline(0),(a,b)->true);
        assertTrue(path.accept(valley,true));
        for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
        LocalPoint before = path.position();
        path = path.retargetWalk(goal,true,560_000_000L);
        assertNotNull(path);
        LocalPoint previous = before;
        for (int ms = 580; ms <= 1000; ms += 20)
        {
            path.advance(ms*1_000_000L);
            assertTrue("no brief y rewind to the already-passed construction origin", path.position().getY() >= previous.getY());
            assertTrue("line crosses only the two proven corridors", offLine(before,goal,path.position()) <= 1.5);
            assertTrue(MovementPath.distance(previous,path.position()) <= 10); assertTrue(path.clear());
            previous = path.position();
        }
    }

    @Test
    public void aStoppedPreviewClickBackToTheOldTrueTileCannotInheritConfirmation()
    {
        idleReturn(false);
    }

    @Test
    public void minimapIdleReturnUsesTheSamePendingConfirmationRule()
    {
        idleReturn(true);
    }

    private static void idleReturn(boolean minimap)
    {
        Fixture f = new Fixture(6080,6720,8000,8000);
        f.speed = 1.08; f.smoothing = 0; f.originalWhenAligned = false;
        LocalPoint goal = f.start, preceding = p(6208,6976);
        f.controller.walkClick(); f.destination = preceding;
        for (int ms = 20; ms <= 620; ms += 20) { f.frame(ms); }
        assertEquals("the preceding preview has visibly stopped before authority", preceding,f.controller.position());
        if (minimap) { f.destination = goal; f.controller.walkClick(preceding); }
        else { f.controller.walkClick(); f.destination = goal; }
        f.frame(640);
        LocalPoint previous = f.controller.position(); boolean arrived = false;
        for (int ms = 660; ms <= 2000; ms += 20)
        {
            if (ms == 700) { f.authority = preceding; }
            if (ms == 1300) { f.authority = goal; }
            f.frame(ms);
            LocalPoint actual = f.controller.position();
            assertTrue("idle fresh input must not append an obsolete forward endpoint", squared(actual,goal) <= squared(previous,goal)+2);
            if (arrived) { assertEquals("no extra return itinerary after arrival", goal,actual); }
            arrived |= actual.equals(goal); previous = actual;
        }
        assertTrue(arrived); f.controller.close();
    }

    @Test
    public void aBlockedAlternativeOrderingKeepsItsProvenCornerInsteadOfACrossingShortcut()
    {
        LocalPoint authority = p(6208,6976), valley = p(6080,6720), goal = p(5952,6976);
        MovementPath path = MovementPath.anticipate(authority,authority,valley,true,0,1.1,
            MovementPath.freshDeadline(0),(a,b)->!(a.equals(valley) && b.equals(p(5952,6848))));
        for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
        MovementPath next = path.retargetWalk(goal,true,560_000_000L);
        assertNotNull(next); assertTrue(next.clear());
        assertFalse("never force an unproved straight knight", next.traceSnapshot().fields(0).contains("6080,6720,5952,6976,5952,6976,1,1"));
    }

    @Test
    public void theIncomingCorridorRemainsCollisionCheckedUntilItsSharedCrossingFinishes()
    {
        boolean[] closed = {false};
        LocalPoint from = p(5952,6976), valley = p(6080,6720), goal = p(6208,6976);
        MovementPath path = MovementPath.anticipate(from,from,valley,true,0,1.1,MovementPath.freshDeadline(0),
            (a,b)->!closed[0] || !(a.equals(from) && b.equals(p(5952,6848))));
        assertTrue(path.accept(valley,true));
        for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
        path = path.retargetWalk(goal,true,560_000_000L); assertNotNull(path);
        path.advance(570_000_000L); assertTrue(path.clear());
        LocalPoint before = path.position(); closed[0] = true;
        assertFalse("the first subsegment still depends on its original corridor", path.clear());
        assertNull(path.retargetWalk(goal,true,580_000_000L)); assertEquals(before,path.position());
    }

    @Test
    public void sharedCorridorStraightJoinsRotateReflectAndRebaseWithinOneBudget()
    {
        for (int rotation = 0; rotation < 4; ++rotation)
        {
            for (boolean reflected : new boolean[] {false,true})
            {
                LocalPoint valley = p(6080,6720), from = rotated(-128,256,rotation,reflected), goal = rotated(128,256,rotation,reflected);
                MovementPath path = MovementPath.anticipate(from,from,valley,true,0,1.1,MovementPath.freshDeadline(0),(a,b)->true);
                assertTrue(path.accept(valley,true));
                for (int ms = 20; ms <= 560; ms += 20) { path.advance(ms*1_000_000L); }
                LocalPoint before = path.position(); long deadline = path.predictionDeadlineNanos();
                path = path.retargetWalk(goal,true,560_000_000L); assertNotNull(path);
                assertTrue(path.resumeScene(-128,-256,p(5952,6464),p(goal.getX()-128,goal.getY()-256),true,560_000_000L));
                assertEquals(deadline,path.predictionDeadlineNanos());
                LocalPoint start = p(before.getX()-128,before.getY()-256), target = p(goal.getX()-128,goal.getY()-256), previous = start;
                for (int ms = 580; ms <= 1400; ms += 20)
                {
                    if (ms == 1000) { assertTrue(path.accept(target,true)); }
                    path.advance(ms*1_000_000L);
                    assertTrue("one shared movement budget", MovementPath.distance(previous,path.position()) <= 10);
                    assertTrue("rotated joins retain their straight visible line", offLine(start,target,path.position()) <= 1.5);
                    assertTrue(path.clear()); previous = path.position();
                }
                assertEquals(target,path.position()); assertTrue(path.finished());
            }
        }
    }

    private static LocalPoint rotated(int dx, int dy, int rotation, boolean reflected)
    {
        if (reflected) { dx = -dx; }
        for (int i = 0; i < rotation; ++i) { int old = dx; dx = -dy; dy = old; }
        return p(6080+dx,6720+dy);
    }

    private static long squared(LocalPoint a, LocalPoint b)
    {
        long dx = a.getX()-b.getX(), dy = a.getY()-b.getY(); return dx*dx+dy*dy;
    }

    private static double offLine(LocalPoint from, LocalPoint to, LocalPoint point)
    {
        double dx = to.getX()-from.getX(), dy = to.getY()-from.getY();
        return Math.abs(dx*(point.getY()-from.getY())-dy*(point.getX()-from.getX()))/Math.hypot(dx,dy);
    }
}
