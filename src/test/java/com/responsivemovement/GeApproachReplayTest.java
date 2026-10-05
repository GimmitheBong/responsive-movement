package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** All 57 real Bank captures: copied geometry/authority, not guessed open-ground fixtures. */
@RunWith(Parameterized.class)
public class GeApproachReplayTest
{
    @Parameterized.Parameters(name = "click {0}")
    public static Collection<Object[]> captures() throws Exception
    {
        return lines("ge-2314-approaches.txt").stream().map(line -> new Object[] {line.split(" ")[0], line}).collect(Collectors.toList());
    }

    private final String capture;
    public GeApproachReplayTest(String id, String capture) { this.capture = capture; }

    @Test
    public void followsCapturedServerRouteContinuouslyAtNormalRunPace() throws Exception
    {
        String[] fields = capture.split(" ");
        LocalPoint origin = tile(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
        long published = Long.parseLong(fields[3]), withdrawn = Long.parseLong(fields[4]);
        List<long[]> authority = new ArrayList<>();
        for (String update : fields[5].split(";"))
        {
            authority.add(Arrays.stream(update.split(",")).mapToLong(Long::parseLong).toArray());
        }
        for (String option : new String[] {"Bank", "Exchange"})
        {
            for (long cadence : new long[] {8_333, 20_000, 33_333})
            {
                Fixture f = fixture(origin);
                f.speed = 1.1;
                BiPredicate<LocalPoint, LocalPoint> collision = collision(f);
                // Expected geometry comes only from recorded authoritative pairs.
                List<LocalPoint> waypoints = new ArrayList<>();
                waypoints.add(origin);
                LocalPoint previous = origin;
                for (long[] update : authority)
                {
                    LocalPoint end = tile((int) update[1], (int) update[2]);
                    List<LocalPoint> steps = MovementPath.checkedRoute(previous, end, collision, 2);
                    assertNotNull("captured authority must be walkable", steps);
                    if (MovementPath.clearKnight(previous, end, collision)) { waypoints.add(end); }
                    else { waypoints.addAll(steps); }
                    previous = end;
                }
                TreeSet<Long> times = new TreeSet<>();
                long endTime = authority.get(authority.size() - 1)[0] + 2_000_000;
                for (long t = 0; t <= endTime; t += cadence) { times.add(t); }
                for (long[] update : authority) { times.add(update[0]); }
                if (published >= 0) { times.add(published); times.add(withdrawn); }
                f.click(option);
                int nextAuthority = 0;
                for (long t : times)
                {
                    while (nextAuthority < authority.size() && t >= authority.get(nextAuthority)[0])
                    {
                        long[] update = authority.get(nextAuthority++);
                        f.authority = tile((int) update[1], (int) update[2]);
                    }
                    f.destination = published >= 0 && t >= published && t < withdrawn ? tile(58, 57) : null;
                    f.now = 1_000_000_000L + t * 1000; f.cycle = 100 + (int) (t / 20_000);
                    f.controller.update();
                    LocalPoint expected = travel(waypoints, t * 0.00044);
                    assertTrue(option + " at " + t + "us expected " + expected + " got " + f.controller.position(),
                        MovementPath.distance(expected, f.controller.position()) <= 1);
                }
                assertEquals(previous, f.controller.position());
                f.controller.close();
            }
        }
    }

    static Fixture fixture(LocalPoint origin) throws Exception
    {
        Fixture f = new Fixture(origin.getX(), origin.getY(), tile(59, 57).getX(), tile(59, 57).getY());
        for (int[] column : f.flags) { Arrays.fill(column, CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
        for (String line : lines("ge-2314-collision.txt"))
        {
            String[] parts = line.split(" "), values = parts[1].split(",");
            int x = Integer.parseInt(parts[0]);
            for (int y = 0; y < values.length; ++y) { f.flags[x][48 + y] = Integer.parseUnsignedInt(values[y], 16); }
        }
        return f;
    }

    static BiPredicate<LocalPoint, LocalPoint> collision(Fixture f)
    {
        return (a, b) -> new WorldPoint(3200 + a.getSceneX(), 3200 + a.getSceneY(), 0).toWorldArea()
            .canTravelInDirection(f.view, Integer.signum(b.getX() - a.getX()), Integer.signum(b.getY() - a.getY()));
    }

    private static LocalPoint travel(List<LocalPoint> points, double distance)
    {
        for (int i = 1; i < points.size(); ++i)
        {
            LocalPoint from = points.get(i - 1), to = points.get(i);
            int length = MovementPath.distance(from, to);
            if (distance <= length)
            {
                return new LocalPoint((int) Math.round(from.getX() + (to.getX() - from.getX()) * distance / length),
                    (int) Math.round(from.getY() + (to.getY() - from.getY()) * distance / length), 0);
            }
            distance -= length;
        }
        return points.get(points.size() - 1);
    }

    static LocalPoint tile(int x, int y) { return new LocalPoint(x * 128 + 64, y * 128 + 64, 0); }
    private static List<String> lines(String name) throws Exception
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            GeApproachReplayTest.class.getResourceAsStream(name), StandardCharsets.UTF_8)))
        {
            return reader.lines().filter(line -> !line.startsWith("#") && !line.isEmpty()).collect(Collectors.toList());
        }
    }
}
