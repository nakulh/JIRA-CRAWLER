import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Domain-based rate limiter that runs on a separate thread
 * Manages rate limiting per domain to respect server resources
 */
public class DomainRateLimiter {
    private static final Logger logger = Logger.getLogger(DomainRateLimiter.class.getName());
    
    private final ConcurrentHashMap<String, DomainTracker> domainTrackers;
    private final ScheduledExecutorService scheduler;
    private final long defaultDelayMs;
    
    public DomainRateLimiter(long defaultDelayMs) {
        this.domainTrackers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.defaultDelayMs = defaultDelayMs;
        
        // Start cleanup task to remove old domain trackers
        startCleanupTask();
    }
    
    /**
     * Waits for the appropriate delay before allowing a request to the domain
     */
    public void waitForPermission(String domain) throws InterruptedException {
        DomainTracker tracker = domainTrackers.computeIfAbsent(domain, 
            k -> new DomainTracker(domain, defaultDelayMs));
        
        tracker.waitForPermission();
    }
    
    /**
     * Records a request to the domain
     */
    public void recordRequest(String domain) {
        DomainTracker tracker = domainTrackers.computeIfAbsent(domain, 
            k -> new DomainTracker(domain, defaultDelayMs));
        
        tracker.recordRequest();
    }
    
    /**
     * Gets statistics for all domains
     */
    public ConcurrentHashMap<String, DomainStats> getAllStats() {
        ConcurrentHashMap<String, DomainStats> stats = new ConcurrentHashMap<>();
        domainTrackers.forEach((domain, tracker) -> stats.put(domain, tracker.getStats()));
        return stats;
    }
    
    /**
     * Shuts down the rate limiter
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Domain rate limiter shut down");
    }
    
    /**
     * Starts a cleanup task to remove inactive domain trackers
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long cutoffTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
                
                domainTrackers.entrySet().removeIf(entry -> {
                    DomainTracker tracker = entry.getValue();
                    if (tracker.getLastRequestTime() < cutoffTime) {
                        logger.fine("Removing inactive domain tracker: " + entry.getKey());
                        return true;
                    }
                    return false;
                });
                
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during domain tracker cleanup", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * Tracks rate limiting for a specific domain
     */
    private static class DomainTracker {
        private final String domain;
        private volatile long delayMs;
        private final AtomicLong lastRequestTime;
        private final AtomicLong requestCount;
        
        public DomainTracker(String domain, long delayMs) {
            this.domain = domain;
            this.delayMs = delayMs;
            this.lastRequestTime = new AtomicLong(0);
            this.requestCount = new AtomicLong(0);
        }
        
        public synchronized void waitForPermission() throws InterruptedException {
            long currentTime = System.currentTimeMillis();
            long timeSinceLastRequest = currentTime - lastRequestTime.get();
            
            if (timeSinceLastRequest < delayMs) {
                long sleepTime = delayMs - timeSinceLastRequest;
                logger.fine(String.format("Rate limiting domain %s: sleeping %d ms", domain, sleepTime));
                Thread.sleep(sleepTime);
            }
        }
        
        public void recordRequest() {
            lastRequestTime.set(System.currentTimeMillis());
            requestCount.incrementAndGet();
        }

        public long getLastRequestTime() {
            return lastRequestTime.get();
        }
        
        public DomainStats getStats() {
            return new DomainStats(
                domain,
                delayMs,
                requestCount.get(),
                lastRequestTime.get()
            );
        }
    }
    
    /**
     * Statistics for a domain
     */
    public static class DomainStats {
        private final String domain;
        private final long delayMs;
        private final long requestCount;
        private final long lastRequestTime;
        
        public DomainStats(String domain, long delayMs, long requestCount, long lastRequestTime) {
            this.domain = domain;
            this.delayMs = delayMs;
            this.requestCount = requestCount;
            this.lastRequestTime = lastRequestTime;
        }
        
        public String getDomain() { return domain; }
        public long getDelayMs() { return delayMs; }
        public long getRequestCount() { return requestCount; }
        public long getLastRequestTime() { return lastRequestTime; }
        
        @Override
        public String toString() {
            return String.format("DomainStats{domain='%s', delayMs=%d, requests=%d, lastRequest=%d}",
                domain, delayMs, requestCount, lastRequestTime);
        }
    }
}