package io.multipaper.shreddedpaper.commands;

import io.multipaper.shreddedpaper.threading.region.RegionTickScheduler;
import io.multipaper.shreddedpaper.threading.region.RegionRuntimeState;
import io.multipaper.shreddedpaper.threading.ownership.ShreddedPaperAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class RegionCommand extends Command {

    public RegionCommand(final String command) {
        super(command);
        this.setPermission("astatine.command.region;shreddedpaper.command.region");
        this.setUsage("/region top|dump|ownership|inspect <world> <regionX> <regionZ>");
    }

    @Override
    public boolean execute(
            @NotNull final CommandSender sender,
            @NotNull final String commandLabel,
            @NotNull final String[] args
    ) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("top")) {
            this.sendTop(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("dump")) {
            this.sendDump(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("ownership")) {
            this.sendOwnership(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("inspect")) {
            this.sendInspect(sender, args);
            return true;
        }

        sender.sendMessage(this.getUsage());
        return true;
    }

    private void sendTop(final CommandSender sender) {
        final RegionTickScheduler scheduler = RegionTickScheduler.getIfStarted();
        if (scheduler == null) {
            sender.sendMessage(Component.text("Independent region scheduler has not started yet.", NamedTextColor.RED));
            return;
        }

        final List<RegionTickScheduler.RegionTickSnapshot> snapshots = scheduler.snapshots();
        this.sendHeader(sender, "Astatine Regions", "top by MSPT, lag, mailbox pressure, and chunk IO pressure");
        if (snapshots.isEmpty()) {
            sender.sendMessage(Component.text("No active region snapshots.", NamedTextColor.GRAY));
            return;
        }
        int index = 1;
        for (final RegionTickScheduler.RegionTickSnapshot snapshot : snapshots.stream().limit(10L).toList()) {
            sender.sendMessage(this.summaryLine(index++, snapshot));
            sender.sendMessage(this.compactDetailLine(snapshot));
        }
        sender.sendMessage(Component.text("Use /region inspect <world> <regionX> <regionZ> for full counters.", NamedTextColor.DARK_GRAY));
    }

    private void sendDump(final CommandSender sender) {
        final RegionTickScheduler scheduler = RegionTickScheduler.getIfStarted();
        if (scheduler == null) {
            sender.sendMessage(Component.text("Independent region scheduler has not started yet.", NamedTextColor.RED));
            return;
        }

        final List<RegionTickScheduler.RegionTickSnapshot> snapshots = scheduler.snapshots();
        this.sendHeader(sender, "Astatine Region Dump", "all active independent tick regions");
        if (snapshots.isEmpty()) {
            sender.sendMessage(Component.text("No active region snapshots.", NamedTextColor.GRAY));
            return;
        }
        int index = 1;
        for (final RegionTickScheduler.RegionTickSnapshot snapshot : snapshots) {
            sender.sendMessage(this.summaryLine(index++, snapshot));
            sender.sendMessage(this.compactDetailLine(snapshot));
        }
    }

    private void sendOwnership(final CommandSender sender) {
        this.sendHeader(sender, "Astatine Ownership", "global async ownership guard counters");
        sender.sendMessage(this.metricLine(
                "ownership",
                this.metric("loadedReadFallbacks", Long.toString(ShreddedPaperAccess.loadedReadFallbacks()), this.countColor(ShreddedPaperAccess.loadedReadFallbacks())),
                this.metric("ownerHandoffs", Long.toString(ShreddedPaperAccess.ownerHandoffs()), this.countColor(ShreddedPaperAccess.ownerHandoffs())),
                this.metric("ownerHandoffRequeues", Long.toString(ShreddedPaperAccess.ownerHandoffRequeues()), this.countColor(ShreddedPaperAccess.ownerHandoffRequeues())),
                this.metric("ownerHandoffRejections", Long.toString(ShreddedPaperAccess.ownerHandoffRejections()), this.countColor(ShreddedPaperAccess.ownerHandoffRejections())),
                this.metric("prefetchFailures", Long.toString(ShreddedPaperAccess.prefetchFailures()), this.countColor(ShreddedPaperAccess.prefetchFailures()))
        ));
        final RegionRuntimeState.RuntimeStateDiagnostics runtimeStates = RegionRuntimeState.diagnostics();
        sender.sendMessage(this.metricLine(
                "runtime",
                this.metric("states", Integer.toString(runtimeStates.totalStates()), NamedTextColor.GRAY),
                this.metric("attached", Integer.toString(runtimeStates.attachedStates()), NamedTextColor.GRAY),
                this.metric("detachedPending", Integer.toString(runtimeStates.detachedPendingStates()), this.countColor(runtimeStates.detachedPendingStates())),
                this.metric("orphan", Integer.toString(runtimeStates.orphanStates()), this.countColor(runtimeStates.orphanStates()))
        ));
        sender.sendMessage(this.metricLine(
                "runtime lifecycle",
                this.metric("created", Long.toString(runtimeStates.createdRegions()), NamedTextColor.GRAY),
                this.metric("split", Long.toString(runtimeStates.createdSplits()), NamedTextColor.GRAY),
                this.metric("lookup", Long.toString(runtimeStates.createdCellLookups()), NamedTextColor.GRAY),
                this.metric("removedEmpty", Long.toString(runtimeStates.removedEmptyRegions()), NamedTextColor.GRAY),
                this.metric("removedMerged", Long.toString(runtimeStates.removedMergedRegions()), NamedTextColor.GRAY),
                this.metric("removedIdle", Long.toString(runtimeStates.removedDetachedIdle()), NamedTextColor.GRAY)
        ));
        final List<String> loadedReadFallbackSamples = ShreddedPaperAccess.loadedReadFallbackSamples();
        if (!loadedReadFallbackSamples.isEmpty()) {
            sender.sendMessage(Component.text("recent loaded-read fallbacks:", NamedTextColor.YELLOW));
            for (final String sample : loadedReadFallbackSamples) {
                sender.sendMessage(Component.text("  " + sample, NamedTextColor.GRAY));
            }
        }
    }

    private void sendInspect(final CommandSender sender, final String[] args) {
        if (args.length != 4) {
            sender.sendMessage(Component.text(this.getUsage(), NamedTextColor.YELLOW));
            return;
        }

        final int regionX;
        final int regionZ;
        try {
            regionX = Integer.parseInt(args[2]);
            regionZ = Integer.parseInt(args[3]);
        } catch (final NumberFormatException ignored) {
            sender.sendMessage(Component.text("Region coordinates must be integers.", NamedTextColor.RED));
            return;
        }

        final RegionTickScheduler scheduler = RegionTickScheduler.getIfStarted();
        if (scheduler == null) {
            sender.sendMessage(Component.text("Independent region scheduler has not started yet.", NamedTextColor.RED));
            return;
        }

        scheduler.snapshots().stream()
                .filter(snapshot -> snapshot.world().equals(args[1]))
                .filter(snapshot -> snapshot.regionPos().x == regionX && snapshot.regionPos().z == regionZ)
                .findFirst()
                .ifPresentOrElse(
                        snapshot -> this.sendInspectSnapshot(sender, snapshot),
                        () -> sender.sendMessage(Component.text("No active region snapshot found for %s %d %d.".formatted(args[1], regionX, regionZ), NamedTextColor.RED))
                );
    }

    private void sendInspectSnapshot(final CommandSender sender, final RegionTickScheduler.RegionTickSnapshot snapshot) {
        this.sendHeader(sender, "Astatine Region Inspect", "%s [%d, %d]".formatted(snapshot.world(), snapshot.regionPos().x, snapshot.regionPos().z));
        sender.sendMessage(this.summaryLine(1, snapshot));
        sender.sendMessage(this.metricLine(
                "mailbox",
                this.metric("depth", Integer.toString(snapshot.mailboxDepth()), this.pressureColor(snapshot.mailboxClassPressure())),
                this.metric("class", this.percent(snapshot.mailboxClassPressure()), this.pressureColor(snapshot.mailboxClassPressure())),
                this.metric("deferred", Long.toString(snapshot.deferredWork()), this.countColor(snapshot.deferredWork())),
                this.metric("rejected", Long.toString(snapshot.rejectedTasks()), this.countColor(snapshot.rejectedTasks()))
        ));
        sender.sendMessage(this.metricLine(
                "chunk requests",
                this.metric("inFlight", this.ratio(snapshot.chunkIoInFlight(), snapshot.chunkIoCapacity()), this.pressureColor(snapshot.chunkIoPressure())),
                this.metric("pressure", this.percent(snapshot.chunkIoPressure()), this.pressureColor(snapshot.chunkIoPressure())),
                this.metric("deferred", Integer.toString(snapshot.chunkIoDeferred()), this.countColor(snapshot.chunkIoDeferred())),
                this.metric("downgraded", Long.toString(snapshot.chunkIoDowngraded()), this.countColor(snapshot.chunkIoDowngraded())),
                this.metric("rejected", Long.toString(snapshot.chunkIoRejected()), this.countColor(snapshot.chunkIoRejected()))
        ));
        sender.sendMessage(this.metricLine(
                "executor",
                this.metric("exec", this.ratio(snapshot.chunkIoExecutorInFlight(), snapshot.chunkIoExecutorCapacity()), this.pressureColor(snapshot.chunkIoExecutorPressure())),
                this.metric("waiting", Integer.toString(snapshot.chunkIoExecutorWaiting()), this.countColor(snapshot.chunkIoExecutorWaiting())),
                this.metric("pressure", this.percent(snapshot.chunkIoExecutorPressure()), this.pressureColor(snapshot.chunkIoExecutorPressure())),
                this.metric("deferred", Integer.toString(snapshot.chunkIoExecutorDeferred()), this.countColor(snapshot.chunkIoExecutorDeferred())),
                this.metric("fallback", Long.toString(snapshot.chunkIoExecutorRejected()), this.countColor(snapshot.chunkIoExecutorRejected()))
        ));
        sender.sendMessage(this.metricLine(
                "backlog",
                this.metric("queued", this.ratio(snapshot.chunkIoExecutorBacklogQueued(), snapshot.chunkIoExecutorBacklogCapacity()), this.pressureColor(snapshot.chunkIoExecutorBacklogPressure())),
                this.metric("pressure", this.percent(snapshot.chunkIoExecutorBacklogPressure()), this.pressureColor(snapshot.chunkIoExecutorBacklogPressure())),
                this.metric("backpressure", Long.toString(snapshot.chunkIoExecutorBacklogBackpressure()), this.countColor(snapshot.chunkIoExecutorBacklogBackpressure())),
                this.metric("deferred", Long.toString(snapshot.chunkIoExecutorBacklogDeferred()), this.countColor(snapshot.chunkIoExecutorBacklogDeferred()))
        ));
        sender.sendMessage(this.metricLine(
                "emergency",
                this.metric("inFlight", Integer.toString(snapshot.chunkIoExecutorBacklogEmergencyInFlight()), this.countColor(snapshot.chunkIoExecutorBacklogEmergencyInFlight())),
                this.metric("admitted", Long.toString(snapshot.chunkIoExecutorBacklogEmergency()), this.countColor(snapshot.chunkIoExecutorBacklogEmergency())),
                this.metric("retries", Integer.toString(snapshot.chunkIoExecutorBacklogEmergencyRetries()), this.countColor(snapshot.chunkIoExecutorBacklogEmergencyRetries())),
                this.metric("rejected", Long.toString(snapshot.chunkIoExecutorBacklogEmergencyRejected()), this.countColor(snapshot.chunkIoExecutorBacklogEmergencyRejected()))
        ));
        sender.sendMessage(this.metricLine(
                "overflow",
                this.metric("inFlight", this.ratio(snapshot.chunkIoExecutorOverflowInFlight(), snapshot.chunkIoExecutorOverflowCapacity()), this.pressureColor(snapshot.chunkIoExecutorOverflowPressure())),
                this.metric("pressure", this.percent(snapshot.chunkIoExecutorOverflowPressure()), this.pressureColor(snapshot.chunkIoExecutorOverflowPressure())),
                this.metric("admitted", Long.toString(snapshot.chunkIoExecutorOverflowAdmitted()), this.countColor(snapshot.chunkIoExecutorOverflowAdmitted())),
                this.metric("backpressure", Long.toString(snapshot.chunkIoExecutorOverflowBackpressure()), this.countColor(snapshot.chunkIoExecutorOverflowBackpressure()))
        ));
        sender.sendMessage(this.metricLine(
                "waiters",
                this.metric("backpressure", this.ratio(snapshot.chunkIoExecutorBackpressureWaiters(), snapshot.chunkIoExecutorBackpressureCapacity()), this.pressureColor(snapshot.chunkIoExecutorBackpressurePressure())),
                this.metric("pressure", this.percent(snapshot.chunkIoExecutorBackpressurePressure()), this.pressureColor(snapshot.chunkIoExecutorBackpressurePressure())),
                this.metric("execDowngraded", Long.toString(snapshot.chunkIoExecutorDowngraded()), this.countColor(snapshot.chunkIoExecutorDowngraded()))
        ));
    }

    private void sendHeader(final CommandSender sender, final String title, final String subtitle) {
        sender.sendMessage(Component.empty()
                .append(Component.text("== ", NamedTextColor.DARK_GRAY))
                .append(this.gradient(title, 0x55FFFF, 0xFF55FF))
                .append(Component.text(" ==", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(Component.text(subtitle, NamedTextColor.GRAY));
    }

    private Component summaryLine(final int index, final RegionTickScheduler.RegionTickSnapshot snapshot) {
        return Component.empty()
                .append(Component.text("#%02d ".formatted(index), NamedTextColor.DARK_GRAY))
                .append(Component.text(snapshot.world(), NamedTextColor.AQUA))
                .append(Component.text(" [%d, %d] ".formatted(snapshot.regionPos().x, snapshot.regionPos().z), NamedTextColor.GRAY))
                .append(Component.text(snapshot.loadClass().name(), this.loadClassColor(snapshot)))
                .append(Component.text("  "))
                .append(this.metric("MSPT", this.decimal(snapshot.ewmaMspt()), this.msptColor(snapshot.ewmaMspt())))
                .append(Component.text("  "))
                .append(this.metric("lag", this.decimal(snapshot.ewmaScheduleLagMs()) + "ms", this.lagColor(snapshot.ewmaScheduleLagMs())))
                .append(Component.text("  "))
                .append(this.metric("mail", "%d/%s".formatted(snapshot.mailboxDepth(), this.percent(snapshot.mailboxClassPressure())), this.pressureColor(snapshot.mailboxClassPressure())))
                .append(Component.text("  "))
                .append(this.metric("IO", this.ratio(snapshot.chunkIoInFlight(), snapshot.chunkIoCapacity()), this.pressureColor(snapshot.chunkIoPressure())));
    }

    private Component compactDetailLine(final RegionTickScheduler.RegionTickSnapshot snapshot) {
        return Component.empty()
                .append(Component.text("     "))
                .append(this.metric("exec", this.ratio(snapshot.chunkIoExecutorInFlight(), snapshot.chunkIoExecutorCapacity()), this.pressureColor(snapshot.chunkIoExecutorPressure())))
                .append(Component.text("  "))
                .append(this.metric("wait", Integer.toString(snapshot.chunkIoExecutorWaiting()), this.countColor(snapshot.chunkIoExecutorWaiting())))
                .append(Component.text("  "))
                .append(this.metric("backlog", this.ratio(snapshot.chunkIoExecutorWaiting() + snapshot.chunkIoExecutorBacklogQueued(), snapshot.chunkIoExecutorBacklogCapacity()), this.pressureColor(snapshot.chunkIoExecutorBacklogPressure())))
                .append(Component.text("  "))
                .append(this.metric("overflow", this.ratio(snapshot.chunkIoExecutorOverflowInFlight(), snapshot.chunkIoExecutorOverflowCapacity()), this.pressureColor(snapshot.chunkIoExecutorOverflowPressure())))
                .append(Component.text("  "))
                .append(this.metric("bp", this.ratio(snapshot.chunkIoExecutorBackpressureWaiters(), snapshot.chunkIoExecutorBackpressureCapacity()), this.pressureColor(snapshot.chunkIoExecutorBackpressurePressure())))
                .append(Component.text("  "))
                .append(this.metric("defer", Long.toString(snapshot.deferredWork()), this.countColor(snapshot.deferredWork())))
                .append(Component.text("  "))
                .append(this.metric("rej", Long.toString(snapshot.rejectedTasks() + snapshot.chunkIoRejected() + snapshot.chunkIoExecutorRejected()), this.countColor(snapshot.rejectedTasks() + snapshot.chunkIoRejected() + snapshot.chunkIoExecutorRejected())))
                .append(Component.text("  "))
                .append(this.metric("downgrade", Long.toString(snapshot.chunkIoDowngraded() + snapshot.chunkIoExecutorDowngraded()), this.countColor(snapshot.chunkIoDowngraded() + snapshot.chunkIoExecutorDowngraded())));
    }

    private Component metricLine(final String label, final Component... metrics) {
        Component component = Component.empty()
                .append(Component.text("  " + label, NamedTextColor.DARK_AQUA))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY));
        for (int i = 0; i < metrics.length; i++) {
            if (i > 0) {
                component = component.append(Component.text("  "));
            }
            component = component.append(metrics[i]);
        }
        return component;
    }

    private Component metric(final String label, final String value, final TextColor valueColor) {
        return Component.empty()
                .append(Component.text(label + "=", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor));
    }

    private Component gradient(final String text, final int startRgb, final int endRgb) {
        Component component = Component.empty();
        final int length = Math.max(1, text.length() - 1);
        final int startRed = (startRgb >> 16) & 0xFF;
        final int startGreen = (startRgb >> 8) & 0xFF;
        final int startBlue = startRgb & 0xFF;
        final int endRed = (endRgb >> 16) & 0xFF;
        final int endGreen = (endRgb >> 8) & 0xFF;
        final int endBlue = endRgb & 0xFF;
        for (int i = 0; i < text.length(); i++) {
            final double ratio = (double) i / (double) length;
            final int red = (int) Math.round(startRed + (endRed - startRed) * ratio);
            final int green = (int) Math.round(startGreen + (endGreen - startGreen) * ratio);
            final int blue = (int) Math.round(startBlue + (endBlue - startBlue) * ratio);
            component = component.append(Component.text(String.valueOf(text.charAt(i)), TextColor.color((red << 16) | (green << 8) | blue)));
        }
        return component;
    }

    private TextColor loadClassColor(final RegionTickScheduler.RegionTickSnapshot snapshot) {
        return switch (snapshot.loadClass()) {
            case GLOBAL -> NamedTextColor.AQUA;
            case NORMAL -> NamedTextColor.GREEN;
            case DEGRADED -> NamedTextColor.YELLOW;
            case QUARANTINED -> NamedTextColor.RED;
        };
    }

    private TextColor msptColor(final double mspt) {
        if (mspt <= 10.0D) {
            return NamedTextColor.GREEN;
        }
        if (mspt <= 35.0D) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private TextColor lagColor(final double lagMs) {
        if (lagMs <= 5.0D) {
            return NamedTextColor.GREEN;
        }
        if (lagMs <= 50.0D) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private TextColor pressureColor(final double pressure) {
        if (pressure <= 0.35D) {
            return NamedTextColor.GREEN;
        }
        if (pressure <= 0.75D) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private TextColor countColor(final long count) {
        return count == 0L ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
    }

    private String ratio(final long value, final long capacity) {
        return value + "/" + capacity;
    }

    private String decimal(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String percent(final double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100.0D);
    }
}
