package io.multipaper.shreddedpaper.commands;

import io.multipaper.shreddedpaper.threading.region.RegionTickScheduler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RegionCommand extends Command {

    public RegionCommand(final String command) {
        super(command);
        this.setPermission("shreddedpaper.command.region");
        this.setUsage("/region top|dump|inspect <world> <regionX> <regionZ>");
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
            sender.sendMessage("Independent region scheduler has not started yet.");
            return;
        }

        final List<RegionTickScheduler.RegionTickSnapshot> snapshots = scheduler.snapshots();
        sender.sendMessage("Top ShreddedPaper regions by EWMA MSPT:");
        snapshots.stream().limit(10L).forEach(snapshot -> sender.sendMessage(this.format(snapshot)));
    }

    private void sendDump(final CommandSender sender) {
        final RegionTickScheduler scheduler = RegionTickScheduler.getIfStarted();
        if (scheduler == null) {
            sender.sendMessage("Independent region scheduler has not started yet.");
            return;
        }

        scheduler.snapshots().forEach(snapshot -> sender.sendMessage(this.format(snapshot)));
    }

    private void sendInspect(final CommandSender sender, final String[] args) {
        if (args.length != 4) {
            sender.sendMessage(this.getUsage());
            return;
        }

        final int regionX;
        final int regionZ;
        try {
            regionX = Integer.parseInt(args[2]);
            regionZ = Integer.parseInt(args[3]);
        } catch (final NumberFormatException ignored) {
            sender.sendMessage("Region coordinates must be integers.");
            return;
        }

        final RegionTickScheduler scheduler = RegionTickScheduler.getIfStarted();
        if (scheduler == null) {
            sender.sendMessage("Independent region scheduler has not started yet.");
            return;
        }

        scheduler.snapshots().stream()
                .filter(snapshot -> snapshot.world().equals(args[1]))
                .filter(snapshot -> snapshot.regionPos().x == regionX && snapshot.regionPos().z == regionZ)
                .findFirst()
                .ifPresentOrElse(
                        snapshot -> sender.sendMessage(this.format(snapshot)),
                        () -> sender.sendMessage("No active region snapshot found for %s %d %d.".formatted(args[1], regionX, regionZ))
                );
    }

    private String format(final RegionTickScheduler.RegionTickSnapshot snapshot) {
        return "%s %s %s mspt=%.2f lag=%.2fms mailbox=%d rejected=%d".formatted(
                snapshot.world(),
                snapshot.regionPos(),
                snapshot.loadClass(),
                snapshot.ewmaMspt(),
                snapshot.ewmaScheduleLagMs(),
                snapshot.mailboxDepth(),
                snapshot.rejectedTasks()
        );
    }
}
