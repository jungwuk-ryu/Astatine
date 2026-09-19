package io.multipaper.audit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

/** For a disposable, loopback-only server. Never install this plugin on a live server. */
public final class WorldClockSmoke extends JavaPlugin {
    private final AtomicInteger overworldTicks = new AtomicInteger();
    private final AtomicInteger netherTicks = new AtomicInteger();
    private World nether;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("astatine.bugAuditSmoke")) {
            getLogger().severe("This test plugin requires the disposable smoke runner.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> guarded(this::begin), 80);
    }

    private void begin() {
        World overworld = getServer().getWorlds().stream().filter(world -> world.getEnvironment() == World.Environment.NORMAL).findFirst().orElseThrow();
        nether = getServer().getWorlds().stream().filter(world -> world.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow();
        getServer().getRegionScheduler().runAtFixedRate(this, overworld, 0, 0, task -> overworldTicks.incrementAndGet(), 1, 1);
        getServer().getRegionScheduler().runAtFixedRate(this, nether, 0, 0, task -> netherTicks.incrementAndGet(), 1, 1);
        measure(5.0F, 2.0D, 9.0D, () -> measure(40.0F, 25.0D, 55.0D, this::unload));
    }

    private void measure(float rate, double minimum, double maximum, Runnable next) {
        getServer().getServerTickManager().setTickRate(rate);
        // Allow the old deadline to pass before starting the wall-clock measurement.
        getServer().getAsyncScheduler().runDelayed(this, ignored -> {
            final int start = overworldTicks.get();
            final long started = System.nanoTime();
            getServer().getAsyncScheduler().runDelayed(this, task -> guarded(() -> {
                double seconds = (System.nanoTime() - started) / 1.0E9D;
                int ticks = overworldTicks.get() - start;
                double observed = ticks / seconds;
                getLogger().info("AUDIT_CLOCK target=" + rate + " ticks=" + ticks + " seconds=" + seconds + " observed=" + observed);
                if (observed < minimum || observed > maximum) throw new AssertionError("unexpected region cadence " + observed);
                getServer().getGlobalRegionScheduler().run(this, global -> guarded(next));
            }), 2, TimeUnit.SECONDS);
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void unload() {
        getServer().getServerTickManager().setTickRate(20.0F);
        if (netherTicks.get() == 0) throw new AssertionError("nether task never ran");
        if (!getServer().unloadWorld(nether, false)) throw new AssertionError("nether unload failed");
        final int atUnload = netherTicks.get();
        getServer().getAsyncScheduler().runDelayed(this, task -> guarded(() -> {
            int afterUnload = netherTicks.get() - atUnload;
            getLogger().info("AUDIT_UNLOAD callbacks_before=" + atUnload + " callbacks_after=" + afterUnload);
            if (afterUnload != 0) throw new AssertionError("unloaded world continued ticking");
            try {
                getServer().getRegionScheduler().run(this, nether, 0, 0, ignored -> {});
                throw new AssertionError("unloaded world accepted a new region task");
            } catch (java.util.concurrent.RejectedExecutionException expected) {
                getLogger().info("AUDIT_CLOSED_WORLD_REJECTED");
            }
            getServer().getGlobalRegionScheduler().run(this, ignored -> guarded(this::reload));
        }), 1, TimeUnit.SECONDS);
    }

    private void reload() {
        World reloaded = getServer().createWorld(new WorldCreator(nether.getName()).environment(World.Environment.NETHER));
        if (reloaded == null) throw new AssertionError("nether reload failed");
        AtomicInteger calls = new AtomicInteger();
        getServer().getRegionScheduler().runAtFixedRate(this, reloaded, 0, 0, task -> calls.incrementAndGet(), 1, 1);
        getServer().getGlobalRegionScheduler().runDelayed(this, task -> guarded(() -> {
            getLogger().info("AUDIT_RELOAD callbacks=" + calls.get());
            if (calls.get() == 0) throw new AssertionError("reloaded world did not tick");
            getLogger().info("AUDIT_SMOKE_PASS");
            getServer().shutdown();
        }), 20);
    }

    private void guarded(Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "AUDIT_SMOKE_FAIL", failure);
            getServer().getGlobalRegionScheduler().run(this, task -> getServer().shutdown());
        }
    }
}
