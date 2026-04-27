package org.bxteam.divinemc.util.tps;

public final class TPSCalculator {

    public static final int MAX_TPS = 20;
    public static final int FULL_TICK_MILLIS = 50;
    private static final int HISTORY_LIMIT = 40;

    private final double[] tpsHistory = new double[HISTORY_LIMIT];
    private int historyIndex;
    private int historySize;
    private long lastTickNanos = Long.MIN_VALUE;
    private double allMissedTicks;

    public synchronized void doTick() {
        final long now = System.nanoTime();
        if (this.lastTickNanos == Long.MIN_VALUE) {
            this.lastTickNanos = now;
            this.addToHistory(MAX_TPS);
            return;
        }

        final long elapsedNanos = Math.max(1L, now - this.lastTickNanos);
        this.lastTickNanos = now;
        final double mspt = elapsedNanos / 1.0E6D;
        this.addToHistory(Math.min(MAX_TPS, 1000.0D / mspt));
        this.clearMissedTicks();
        final double missedTicks = (mspt / FULL_TICK_MILLIS) - 1.0D;
        if (missedTicks > 0.0D) {
            this.allMissedTicks += missedTicks;
        }
    }

    public synchronized double getAverageTPS() {
        if (this.historySize == 0) {
            return MAX_TPS;
        }
        double sum = 0.0D;
        for (int i = 0; i < this.historySize; i++) {
            sum += this.tpsHistory[i];
        }
        return sum / this.historySize;
    }

    public synchronized double getTPS() {
        if (this.historySize == 0) {
            return MAX_TPS;
        }
        final int previousIndex = Math.floorMod(this.historyIndex - 1, HISTORY_LIMIT);
        return this.tpsHistory[previousIndex];
    }

    public synchronized double getMostAccurateTPS() {
        return Math.min(this.getTPS(), this.getAverageTPS());
    }

    public synchronized int applicableMissedTicks() {
        return (int)Math.floor(this.allMissedTicks);
    }

    public synchronized void clearMissedTicks() {
        this.allMissedTicks -= this.applicableMissedTicks();
    }

    public synchronized void resetMissedTicks() {
        this.allMissedTicks = 0.0D;
    }

    private void addToHistory(final double tps) {
        this.tpsHistory[this.historyIndex] = tps;
        this.historyIndex = (this.historyIndex + 1) % HISTORY_LIMIT;
        if (this.historySize < HISTORY_LIMIT) {
            this.historySize++;
        }
    }
}
