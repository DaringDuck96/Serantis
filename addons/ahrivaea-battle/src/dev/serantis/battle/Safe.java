package dev.serantis.battle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A safety net: one of our per-tick or per-sync jobs going wrong logs the error (with what and for whom) and carries on,
 * instead of taking the whole server down. Each job's error is logged in full the first time, then counted.
 */
public final class Safe {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("serantis_battle");
    private static final Map<String, Integer> SEEN = new ConcurrentHashMap<>();

    private Safe() {}

    public static void run(String what, Runnable job) {
        try {
            job.run();
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError && !(t instanceof StackOverflowError)) throw (VirtualMachineError) t;   // out of memory: nothing to save
            int n = SEEN.merge(what, 1, Integer::sum);
            if (n == 1) LOG.error("Serantis: {} failed (it was skipped, the server keeps running)", what, t);
            else if (n % 200 == 0) LOG.error("Serantis: {} has failed {} times: {}", what, n, t.toString());
        }
    }
}
