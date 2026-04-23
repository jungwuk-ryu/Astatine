package org.bxteam.divinemc.chunk;

import java.util.Locale;

public enum ChunkSystemAlgorithm {
    MOONRISE {
        @Override
        public int evalWorkers(final int configuredWorkers) {
            if (configuredWorkers > 0) {
                return configuredWorkers;
            }

            final int cpus = cpuCount();
            if (cpus <= 3) {
                return 1;
            }
            if (cpus <= 4) {
                return 2;
            }
            return Math.max(1, cpus / 2);
        }
    },
    C2ME {
        @Override
        public int evalWorkers(final int configuredWorkers) {
            if (configuredWorkers > 0) {
                return configuredWorkers;
            }

            final double cpus = cpuCount();
            final double memoryGb = maxMemoryGb();
            final double cpuBudget = isWindows() ? cpus / 1.6D - 2.0D : cpus / 1.2D - 2.0D;
            final double memoryBudget = isOpenJ9() ? (memoryGb - 0.2D) / 0.4D : (memoryGb - 0.6D) / 0.6D;
            return Math.max(1, (int) Math.floor(Math.min(cpuBudget, memoryBudget)));
        }
    },
    C2ME_NEW {
        @Override
        public int evalWorkers(final int configuredWorkers) {
            if (configuredWorkers > 0) {
                return configuredWorkers;
            }

            final double cpus = cpuCount();
            final double memoryGb = maxMemoryGb();
            final double cpuBudget = isWindows() ? cpus / 1.6D : cpus / 1.3D;
            final double memoryBudget = (memoryGb - 0.5D) / 0.6D;
            return Math.max(1, (int) Math.floor(Math.min(cpuBudget, memoryBudget)));
        }
    };

    public abstract int evalWorkers(int configuredWorkers);

    public int evalWorkers(final int configuredWorkers, final int configuredIoThreads) {
        return this.evalWorkers(configuredWorkers);
    }

    public int evalIO(final int configuredIoThreads) {
        return Math.max(1, configuredIoThreads);
    }

    public int evalIO(final int configuredWorkers, final int configuredIoThreads) {
        return this.evalIO(configuredIoThreads);
    }

    public String asDebugString(final int configuredWorkers, final int configuredIoThreads) {
        return String.format(Locale.ROOT, "%s(workerThreads=%d, ioThreads=%d)", this.name(), this.evalWorkers(configuredWorkers), this.evalIO(configuredIoThreads));
    }

    private static int cpuCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    private static double maxMemoryGb() {
        return Math.max(0.25D, Runtime.getRuntime().maxMemory() / 1024.0D / 1024.0D / 1024.0D);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isOpenJ9() {
        return System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT).contains("openj9");
    }
}