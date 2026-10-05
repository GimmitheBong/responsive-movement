package com.responsivemovement;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Test-only API doubles; production code uses no reflection or model internals. */
public class NativeModelObjectTest
{
    @Test
    public void bodyAndEffectsRebuildAtDrawTimeInsteadOfRetainingSharedScratchModels()
    {
        Fixture f = new Fixture();
        AtomicInteger scratchPose = new AtomicInteger(), bodyReads = new AtomicInteger(), effectReads = new AtomicInteger();
        Model scratch = proxy(Model.class, (method, args) -> {
            if (method.equals("getModelHeight")) { return scratchPose.get(); }
            throw new AssertionError("Model must not be transformed or animated: " + method);
        });
        Renderable player = source(() -> { bodyReads.incrementAndGet(); scratchPose.set(101); return scratch; });
        Renderable effect = source(() -> { effectReads.incrementAndGet(); scratchPose.set(202); return scratch; });
        NativeModelObject body = new NativeModelObject(f.client), spot = new NativeModelObject(f.client);
        assertTrue(body.prepare(player, true));
        assertTrue(spot.prepare(effect, true));
        // Effect preparation reused the same buffer. The body's draw must ask
        // its own native builder again rather than return the cached effect pose.
        assertEquals(202, scratchPose.get());
        assertEquals(101, body.getModel().getModelHeight());
        assertEquals(202, spot.getModel().getModelHeight());
        assertEquals(101, body.getModel().getModelHeight());
        assertEquals(3, bodyReads.get());
        assertEquals(2, effectReads.get());
    }

    @Test
    public void missingPreparationModelClearsThePreviousBodyForNativeFallback()
    {
        Fixture f = new Fixture();
        Model[] model = {model()};
        NativeModelObject body = new NativeModelObject(f.client);
        Renderable player = source(() -> model[0]);
        assertTrue(body.prepare(player, true));
        assertNotNull(body.getModel());
        model[0] = null;
        assertFalse(body.prepare(player, true));
        assertFalse(body.ready());
        assertNull(body.getModel());
        model[0] = model();
        assertNull("availability must be republished by preparation", body.getModel());
        assertTrue(body.prepare(player, true));
        assertSame(model[0], body.getModel());
        assertFalse(body.prepare(null, true));
        assertNull(body.getModel());
    }

    @Test
    public void aModelDisappearingAtDrawIsNotReplacedByAnOldSharedPose()
    {
        Fixture f = new Fixture();
        Model[] model = {model()};
        NativeModelObject body = new NativeModelObject(f.client);
        assertTrue(body.prepare(source(() -> model[0]), true));
        model[0] = null;
        assertNull(body.getModel());
    }

    @Test
    public void offThreadRequestsNeverReadNativeActorOrEffectState() throws Exception
    {
        Fixture f = new Fixture();
        AtomicInteger reads = new AtomicInteger();
        Model model = model();
        NativeModelObject body = new NativeModelObject(f.client);
        assertTrue(body.prepare(source(() -> { reads.incrementAndGet(); return model; }), true));
        assertNull(CompletableFuture.supplyAsync(body::getModel).get(2, TimeUnit.SECONDS));
        assertEquals(1, reads.get());
        assertSame(model, body.getModel());
        assertEquals(2, reads.get());
    }

    @Test
    public void alignedHandoffKeepsRegistrationButHidesTheCustomBodyAndShutdownClearsIt()
    {
        Fixture f = new Fixture();
        AtomicInteger reads = new AtomicInteger();
        Model model = model();
        Renderable player = source(() -> { reads.incrementAndGet(); return model; });
        NativeModelObject body = new NativeModelObject(f.client);
        body.setLocation(new LocalPoint(1344, 1344, 0), 0);
        assertTrue(body.prepare(player, true));
        body.setActive(true);
        assertTrue(body.isActive());
        assertTrue(body.prepare(player, false));
        int beforeDraw = reads.get();
        assertNull(body.getModel());
        assertEquals(beforeDraw, reads.get());
        assertTrue(body.isActive());
        assertTrue(body.prepare(player, true));
        assertSame(model, body.getModel());
        body.setActive(false);
        assertFalse(body.isActive());
        assertFalse(body.ready());
        assertNull(body.getModel());
        assertTrue(f.registered.isEmpty());
    }

    @Test
    public void viewTransferPreservesPreparedSourceAndExplicitPresentationTransform()
    {
        Fixture f = new Fixture();
        NativeModelObject object = new NativeModelObject(f.client);
        Model model = model();
        object.setLocation(new LocalPoint(1344, 1344, 0), 0);
        object.setZ(-80);
        object.setOrientation(1536);
        object.prepare(source(() -> model), true);
        object.setActive(true);
        LocalPoint otherView = new LocalPoint(1600, 1728, 7);
        object.setLocation(otherView, 1);
        assertEquals(List.of("add:0", "remove:0", "add:7"), f.events);
        assertTrue(object.isActive());
        assertTrue(object.ready());
        assertSame(model, object.getModel());
        assertEquals(otherView, object.getLocation());
        assertEquals(1, object.getLevel());
        assertEquals(-80, object.getZ());
        assertEquals(1536, object.getOrientation());
        object.hide();
        assertNull(object.getModel());
    }

    @Test
    public void actionGaitBuilderIdleIsDrawScopedAndWorkerRequestsCannotTouchItsSelectors() throws Exception
    {
        Fixture f = new Fixture();
        AtomicInteger idle = new AtomicInteger(824), reads = new AtomicInteger();
        Model model = model();
        Player player = proxy(Player.class, (method, args) -> {
            reads.incrementAndGet();
            if (method.equals("getIdlePoseAnimation")) { return idle.get(); }
            if (method.equals("setIdlePoseAnimation")) { idle.set((int) args[0]); return null; }
            if (method.equals("getModel")) { assertEquals(808, idle.get()); return model; }
            throw new AssertionError("only idle selector may be scoped: " + method);
        });
        NativeModelObject body = new NativeModelObject(f.client);
        assertTrue(body.prepare(player, true, 808)); assertEquals(824, idle.get());
        assertSame(model, body.getModel()); assertEquals(824, idle.get());
        int before = reads.get();
        assertNull(CompletableFuture.supplyAsync(body::getModel).get(2, TimeUnit.SECONDS));
        assertEquals(before, reads.get()); assertEquals(824, idle.get());
        body.hide(); assertNull(body.getModel()); assertEquals(before, reads.get());
    }

    @Test
    public void modelBuildFailureStillRestoresTheNativeTickIdleSelector()
    {
        Fixture f = new Fixture();
        int[] idle = {824}; boolean[] fail = {false};
        Model model = model();
        Player player = proxy(Player.class, (method, args) -> {
            if (method.equals("getIdlePoseAnimation")) { return idle[0]; }
            if (method.equals("setIdlePoseAnimation")) { idle[0] = (int) args[0]; return null; }
            if (method.equals("getModel"))
            {
                assertEquals(808, idle[0]);
                if (fail[0]) { throw new IllegalStateException("native build unavailable"); }
                return model;
            }
            throw new AssertionError(method);
        });
        NativeModelObject body = new NativeModelObject(f.client);
        assertTrue(body.prepare(player, true, 808)); fail[0] = true;
        assertThrows(IllegalStateException.class, body::getModel);
        assertEquals(824, idle[0]);
    }

    @Test
    public void missingScopedModelsAndLaterDefaultSourcesCannotRetainABuilderOverride()
    {
        Fixture f = new Fixture();
        int[] idle = {824}; Model[] current = {model()};
        Player player = proxy(Player.class, (method, args) -> {
            if (method.equals("getIdlePoseAnimation")) { return idle[0]; }
            if (method.equals("setIdlePoseAnimation")) { idle[0] = (int) args[0]; return null; }
            if (method.equals("getModel")) { assertEquals(808, idle[0]); return current[0]; }
            throw new AssertionError(method);
        });
        NativeModelObject body = new NativeModelObject(f.client);
        assertTrue(body.prepare(player, true, 808)); current[0] = null;
        assertNull(body.getModel()); assertEquals(824, idle[0]);
        assertFalse(body.prepare(player, true, 808)); assertNull(body.getModel()); assertEquals(824, idle[0]);
        Model unscoped = model();
        assertTrue(body.prepare(source(() -> unscoped), true)); assertSame(unscoped, body.getModel());
        body.setActive(false); assertNull(body.getModel());
    }

    private static Renderable source(java.util.function.Supplier<Model> builder)
    {
        return proxy(Renderable.class, (method, args) -> {
            if (method.equals("getModel")) { return builder.get(); }
            throw new AssertionError("Unexpected native state access: " + method);
        });
    }

    private static Model model()
    {
        return proxy(Model.class, (method, args) -> { throw new AssertionError("Unexpected model mutation: " + method); });
    }

    private interface Call { Object invoke(String name, Object[] args); }
    private static <T> T proxy(Class<T> type, Call call)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (object, method, args) -> {
                if (method.getName().equals("hashCode")) { return System.identityHashCode(object); }
                if (method.getName().equals("equals")) { return object == args[0]; }
                if (method.getName().equals("toString")) { return type.getSimpleName(); }
                return call.invoke(method.getName(), args);
            }));
    }

    private static final class Fixture
    {
        final Thread clientThread = Thread.currentThread();
        final Set<RuneLiteObjectController> registered = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<String> events = new ArrayList<>();
        final Client client = proxy(Client.class, (method, args) -> {
            if (method.equals("isClientThread")) { return Thread.currentThread() == clientThread; }
            RuneLiteObjectController object = (RuneLiteObjectController) args[0];
            switch (method)
            {
                case "isRuneLiteObjectRegistered": return registered.contains(object);
                case "registerRuneLiteObject":
                    events.add("add:" + object.getWorldView()); registered.add(object); return null;
                case "removeRuneLiteObject":
                    events.add("remove:" + object.getWorldView()); registered.remove(object); return null;
                default: throw new AssertionError("Unexpected client access: " + method);
            }
        });
    }
}
