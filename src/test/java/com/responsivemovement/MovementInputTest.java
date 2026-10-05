package com.responsivemovement;

import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementInputTest
{
    @Test
    public void smoothedWalkObservesImmediatelyButReleasesOnlyAfterTheClickWindow()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 20_000_000L, 1, 0));
        assertTrue(input.settling());
        assertFalse(input.takeReplacement());
        assertNull(input.destination(target, 49_000_000L, 1, 0));
        assertEquals(target, input.destination(target, 50_000_000L, 1, 0));
        assertFalse(input.settling());
        assertTrue(input.takeReplacement());
        assertFalse(input.takeReplacement());
        // The original response expiry is still measured from the click.
        assertNull(input.destination(target, 901_000_000L, 1, 0));
        assertFalse(input.pending());
    }

    @Test
    public void aReplacementClickObservesItsOwnDestinationDuringSettling()
    {
        LocalPoint east = new LocalPoint(1600, 1344, 0), north = new LocalPoint(1344, 1600, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(east, 10_000_000L, 1, 0));
        input.click(east, 20_000_000L, 1, 0, 50);
        assertNull(input.destination(north, 40_000_000L, 1, 0));
        assertNull(input.destination(north, 60_000_000L, 1, 0));
        assertEquals(north, input.destination(north, 70_000_000L, 1, 0));
        assertTrue(input.takeReplacement());
    }

    @Test
    public void repeatedSmoothedIdleClickKeepsItsAlreadyObservedDestination()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 10_000_000L, 1, 0));
        input.click(target, 20_000_000L, 1, 0, 50);
        assertNull(input.destination(target, 60_000_000L, 1, 0));
        assertEquals(target, input.destination(target, 70_000_000L, 1, 0));
        assertEquals("a carried repeat is also latched once released", target,
            input.destination(new LocalPoint(1344, 1600, 0), 80_000_000L, 1, 0));
    }

    @Test
    public void delayIsFromTheClickNotFromDestinationPublicationAndObservationStillExpires()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertEquals(target, input.destination(target, 70_000_000L, 1, 0));
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 101_000_000L, 1, 0));
        assertFalse(input.pending());
        input.click(null, 0, 1, 0, 500);
        assertEquals("out-of-range settings cannot add a long wait", target,
            input.destination(target, 60_000_000L, 1, 0));
        input.click(null, 0, 1, 0, -50);
        assertEquals(target, input.destination(target, 0, 1, 0));
    }

    @Test
    public void scenePlaneAndCancellationDiscardADestinationCapturedDuringSettling()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 20_000_000L, 1, 0));
        assertNull(input.destination(target, 60_000_000L, 2, 0));
        assertFalse(input.pending());
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 20_000_000L, 1, 0));
        assertNull(input.destination(target, 60_000_000L, 1, 1));
        assertFalse(input.pending());
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 20_000_000L, 1, 0));
        input.clear();
        assertNull(input.destination(target, 60_000_000L, 1, 0));
        assertFalse(input.takeReplacement());
    }

    @Test
    public void aRedClickCannotInheritAPendingSmoothedWalkDestination()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0, 50);
        assertNull(input.destination(target, 20_000_000L, 1, 0));
        input.click(target, 30_000_000L, 1, 0);
        assertNull(input.destination(target, 40_000_000L, 1, 0));
        assertFalse(input.takeReplacement());
    }

    @Test
    public void confirmedMovementContinuesDuringSettlingAndRetargetKeepsItsBudgetAndDeadline()
    {
        LocalPoint start = new LocalPoint(1344, 1344, 0), east = new LocalPoint(1600, 1344, 0);
        LocalPoint north = new LocalPoint(1344, 1600, 0);
        MovementPath path = MovementPath.idle(start, 0, 1, (a, b) -> true);
        assertTrue(path.accept(east, true));
        path.advance(100_000_000L);
        long deadline = path.predictionDeadlineNanos();
        MovementInput input = new MovementInput();
        input.click(east, 100_000_000L, 1, 0, 50);
        for (int ms = 110; ms < 150; ms += 10)
        {
            assertNull(input.destination(north, ms * 1_000_000L, 1, 0));
            assertFalse(input.takeReplacement());
            int x = path.position().getX();
            path.advance(ms * 1_000_000L);
            assertEquals(x + 4, path.position().getX());
        }
        LocalPoint before = path.position();
        assertEquals(north, input.destination(north, 150_000_000L, 1, 0));
        assertTrue(input.takeReplacement());
        MovementPath retarget = path.retargetWalk(north, true, 150_000_000L);
        assertNotNull(retarget);
        assertEquals(before, retarget.position());
        assertEquals(deadline, retarget.predictionDeadlineNanos());
        assertEquals(east, retarget.confirmed());
        retarget.advance(150_000_000L);
        assertTrue(MovementPath.distance(before, retarget.position()) <= 4);
        assertTrue(retarget.clear());
        assertNull(retarget.retargetWalk(start, true, deadline));
    }

    @Test
    public void repeatWalkCanReplaceADifferentPreviewAfterNativeInputProcessing()
    {
        // Capture cycle 128742: destination remained 6336, while the last
        // visual click had predicted 7232. Repeating 6336 must not be dropped.
        LocalPoint current = new LocalPoint(6336, 9024, 0), predicted = new LocalPoint(7232, 9024, 0);
        MovementInput input = new MovementInput();
        input.click(current, 0, 1, 0);
        assertNull(input.destination(current, 0, 1, 0));
        assertEquals(current, input.destination(current, 20_000_000L, 1, 0, predicted));
        assertTrue(input.takeReplacement());
        assertFalse(input.takeReplacement());

        input.click(current, 40_000_000L, 1, 0);
        assertNull(input.destination(current, 60_000_000L, 1, 0, current));
        assertFalse(input.takeReplacement());
        assertNull(input.destination(current, 150_000_000L, 1, 0, predicted));
        assertFalse(input.pending());
    }

    @Test
    public void repeatDestinationCannotSurviveSceneOrPlaneChanges()
    {
        LocalPoint current = new LocalPoint(6336, 9024, 0), predicted = new LocalPoint(7232, 9024, 0);
        MovementInput input = new MovementInput();
        input.click(current, 0, 1, 0);
        assertNull(input.destination(current, 20_000_000L, 2, 0, predicted));
        input.click(current, 0, 1, 0);
        assertNull(input.destination(current, 20_000_000L, 1, 1, predicted));
    }

    @Test
    public void minimapDestinationAlreadyPublishedDuringInterfaceProcessingIsRecognized()
    {
        LocalPoint previousTick = new LocalPoint(1344, 1344, 0);
        LocalPoint current = new LocalPoint(1856, 1600, 0);
        MovementInput input = new MovementInput();
        input.click(previousTick, 0, 1, 0);
        assertEquals(current, input.destination(current, 1_000_000L, 1, 0));
        assertTrue(input.takeReplacement());
        input.clear();
        input.click(current, 20_000_000L, 1, 0);
        assertNull(input.destination(current, 21_000_000L, 1, 0));
        assertFalse(input.takeReplacement());
    }

    @Test
    public void repeatedYellowClicksKeepTheExistingPreviewUntilDestinationChanges()
    {
        LocalPoint start = new LocalPoint(1344, 1344, 0), target = new LocalPoint(2624, 1344, 0);
        MovementPath path = MovementPath.anticipate(start, start, target, true, 0, 1,
            MovementPath.freshDeadline(0), (a, b) -> true);
        MovementInput input = new MovementInput();
        for (int ms = 20; ms <= 800; ms += 20)
        {
            input.click(target, ms * 1_000_000L, 1, 0);
            assertNull(input.destination(target, ms * 1_000_000L, 1, 0));
            assertFalse(input.takeReplacement());
            if (ms == 400) { assertTrue(path.accept(new LocalPoint(1600, 1344, 0), true)); }
            path.advance(ms * 1_000_000L);
            assertTrue(path.moving());
        }
        assertEquals(start.getX() + 320, path.position().getX());
        LocalPoint other = new LocalPoint(1344, 1600, 0);
        assertEquals(other, input.destination(other, 820_000_000L, 1, 0));
        assertTrue(input.takeReplacement());
        assertFalse(input.takeReplacement());
        input.clear();
        assertFalse(input.takeReplacement());
    }

    @Test
    public void onlyANewlyPublishedDestinationBelongsToTheLatestClick()
    {
        LocalPoint a = new LocalPoint(1344, 1344, 0), b = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(a, 0, 1, 0);
        assertNull(input.destination(a, 10_000_000L, 1, 0));
        assertEquals(b, input.destination(b, 20_000_000L, 1, 0));
        assertEquals(b, input.destination(null, 600_000_000L, 1, 0));
        input.click(b, 620_000_000L, 1, 0);
        assertNull(input.destination(b, 630_000_000L, 1, 0));
        assertEquals(a, input.destination(a, 640_000_000L, 1, 0));
    }

    @Test
    public void sceneChangesAndExpiredClicksCannotStartAnUnrelatedRoute()
    {
        LocalPoint target = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0);
        assertNull(input.destination(target, 101_000_000L, 1, 0));
        input.click(null, 0, 1, 0);
        assertNull(input.destination(target, 20_000_000L, 2, 0));
        input.click(null, 0, 1, 0);
        assertEquals(target, input.destination(target, 20_000_000L, 1, 0));
        assertNull(input.destination(target, 901_000_000L, 1, 0));
    }

    @Test
    public void interactionWithoutANewDestinationCannotReuseThePreviousWalk()
    {
        LocalPoint oldWalk = new LocalPoint(1600, 1344, 0);
        MovementInput input = new MovementInput();
        input.click(null, 0, 1, 0);
        assertEquals(oldWalk, input.destination(oldWalk, 20_000_000L, 1, 0));
        input.clear();
        input.click(oldWalk, 30_000_000L, 1, 0);
        assertNull(input.destination(oldWalk, 40_000_000L, 1, 0));
        assertNull(input.destination(null, 60_000_000L, 1, 0));
        assertNull(input.destination(oldWalk, 140_000_000L, 1, 0));
        assertFalse(input.pending());
    }
}
