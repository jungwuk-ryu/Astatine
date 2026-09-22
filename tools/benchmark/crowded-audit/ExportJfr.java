import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;
import java.util.*;

/** Stream only needed fields; jfr print recursively expands large object graphs. */
public final class ExportJfr {
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner out = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> out.add(json(key.toString()) + ":" + json(item)));
            return out.toString();
        }
        if (value instanceof Collection<?> list) {
            StringJoiner out = new StringJoiner(",", "[", "]");
            list.forEach(item -> out.add(json(item)));
            return out.toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch < 32) out.append(String.format("\\u%04x", (int) ch));
            else out.append(ch);
        }
        return out.append('"').toString();
    }

    public static void main(String[] args) throws Exception {
        Set<String> selected = Set.of("jdk.ExecutionSample", "jdk.ObjectAllocationSample", "jdk.ThreadPark",
            "jdk.JavaMonitorEnter", "jdk.GCPhasePause", "jdk.ThreadAllocationStatistics", "jdk.CPULoad");
        try (RecordingFile input = new RecordingFile(Path.of(args[0]))) {
            while (input.hasMoreEvents()) {
                RecordedEvent event = input.readEvent();
                String type = event.getEventType().getName();
                boolean tick = type.endsWith("RegionTickEvent"), scheduler = type.endsWith("RegionSchedulerEvent");
                boolean lockWait = type.endsWith("RegionLockWaitEvent"), networkBatch = type.endsWith("NetworkBatchMetrics$Summary");
                if (!selected.contains(type) && !tick && !scheduler && !lockWait && !networkBatch) continue;
                RecordedThread thread = type.equals("jdk.CPULoad") ? null : type.equals("jdk.ExecutionSample") ? event.getThread("sampledThread")
                    : type.equals("jdk.ThreadAllocationStatistics") ? event.getThread("thread") : event.getThread();
                String name = thread == null || thread.getJavaName() == null ? "unknown" : thread.getJavaName();
                if ((type.equals("jdk.ThreadPark") || type.equals("jdk.JavaMonitorEnter"))
                    && !name.startsWith("AstatineRegion") && !name.equals("Server thread")) continue;
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("thread", name);
                values.put("time", event.getStartTime().toEpochMilli());
                values.put("durationMs", event.getDuration().toNanos() / 1e6);
                List<String> stack = new ArrayList<>();
                if (event.getStackTrace() != null) {
                    for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                        stack.add(frame.getMethod().getType().getName() + "." + frame.getMethod().getName());
                    }
                }
                values.put("frames", stack);
                if (type.equals("jdk.ObjectAllocationSample")) {
                    values.put("objectClass", Map.of("name", event.getClass("objectClass").getName()));
                    values.put("weight", event.getLong("weight"));
                }
                if (type.equals("jdk.ThreadAllocationStatistics")) values.put("allocated", event.getLong("allocated"));
                if (type.equals("jdk.CPULoad")) {
                    for (String key : List.of("jvmUser", "jvmSystem", "machineTotal")) values.put(key, event.getFloat(key));
                }
                if (tick || scheduler || lockWait || networkBatch) {
                    for (String key : List.of("world", "regionX", "regionZ", "loadClass", "scheduledStartNanos",
                        "actualStartNanos", "wallNanos", "scheduleLagNanos", "workerLane", "sourceQueue", "sampleCount",
                        "workerWaitNanos", "workerBusyNanos", "wakeupLatencyNanos", "normalQueueDepth", "degradedQueueDepth",
                        "originalScheduledNanos", "firstFailedNanos", "waitNanos", "overdueNanos", "failedAttempts", "outcome",
                        "observedBlocker", "blockerX", "blockerZ", "scheduledBatches", "drainedBatches", "drainedPackets",
                        "totalQueueNanos", "maxQueueNanos", "enqueuedPackets")) {
                        if (event.hasField(key)) values.put(key, event.getValue(key));
                    }
                }
                System.out.println(json(Map.of("type", type, "values", values)));
            }
        }
    }
}
