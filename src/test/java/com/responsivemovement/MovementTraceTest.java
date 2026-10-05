package com.responsivemovement;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MovementTraceTest
{
    private static final long START = 1789980943765L;
    private static final LocalPoint POINT = new LocalPoint(5952, 5952, 0);

    private static void record(MovementTrace trace, int cycle)
    {
        record(trace, cycle, true, true, 45000L, true);
    }

    private static void record(MovementTrace trace, int cycle, boolean canContinue, boolean pending, long age, boolean spot)
    {
        trace.record(cycle, 0, POINT, POINT, POINT, null, 0, -1, 0, -1, true,
            null, cycle, "started", false, "WALK", -1, canContinue, pending, age, spot);
    }

    @Test
    public void oneToggleRecordsTheWholeSixteenMinuteReportWindow()
    {
        AtomicLong clock = new AtomicLong();
        List<MovementTrace.Entry> written = new ArrayList<>();
        MovementTrace trace = new MovementTrace(clock::get, () -> START, batch -> { written.addAll(batch); return true; });
        trace.enabled(true);
        for (int cycle = 0; cycle <= 48000; ++cycle)
        {
            clock.set(cycle * 20_000_000L);
            record(trace, cycle);
            record(trace, cycle); // Multiple render frames cannot duplicate a client cycle.
            trace.poll();
        }
        trace.enabled(false);
        assertEquals(48001, written.size());
        MovementTrace.Entry last = written.get(written.size() - 1);
        assertEquals(48000, last.sequence);
        assertEquals(960_000_000L, last.micros);
        String line = last.line();
        String time = line.substring(line.indexOf(" time=") + 6).trim();
        assertEquals(START + 960_000L, OffsetDateTime.parse(time).toInstant().toEpochMilli());
        assertTrue(line.contains("clickAction=WALK clickTarget=-1"));
        assertTrue(line.contains("trueTileIndicator=5952,5952"));
        assertTrue(line.contains("canContinue=true inputPending=true inputAgeUs=45000 spot=true"));
        record(trace, 48001);
        trace.poll();
        assertEquals(48001, written.size());
    }

    @Test
    public void closingFlushesAndAllowsRecordingToResumeAfterLogin()
    {
        AtomicLong clock = new AtomicLong();
        List<MovementTrace.Entry> written = new ArrayList<>();
        MovementTrace trace = new MovementTrace(clock::get, () -> START + clock.get() / 1_000_000L,
            batch -> { written.addAll(batch); return true; });
        trace.enabled(true);
        record(trace, 1);
        clock.set(1_000_000_000L);
        trace.poll();
        assertEquals(1, written.size());
        record(trace, 2);
        trace.close();
        assertEquals(2, written.size());
        clock.set(2_000_000_000L);
        trace.enabled(true);
        record(trace, 3);
        trace.close();
        assertEquals(3, written.size());
        assertEquals(0, written.get(2).sequence);
        assertEquals(START + 2000, written.get(2).session);
    }

    @Test
    public void temporaryBackpressureDoesNotSilentlyEndTheSession()
    {
        AtomicLong clock = new AtomicLong();
        List<MovementTrace.Entry> written = new ArrayList<>();
        boolean[] blocked = {true};
        MovementTrace trace = new MovementTrace(clock::get, () -> START, batch ->
        {
            if (blocked[0]) { return false; }
            written.addAll(batch); return true;
        });
        trace.enabled(true);
        for (int i = 0; i < 128; ++i) { record(trace, i); }
        assertTrue(written.isEmpty());
        blocked[0] = false;
        clock.set(130_000_000_000L); // Beyond the former two-minute cutoff.
        record(trace, 128);
        trace.poll();
        assertEquals(1, written.size());
        assertEquals(128, written.get(0).sequence);
        trace.close();
    }
}
