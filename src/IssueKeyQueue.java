import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Thread-safe queue for managing issue keys that need to be scraped
 * Runs on a separate thread to continuously populate the queue
 */
public class IssueKeyQueue {
    private static final Logger logger = Logger.getLogger(IssueKeyQueue.class.getName());
    
    private final BlockingQueue<IssueKeyTask> queue;
    private final AtomicBoolean isRunning;
    private final AtomicInteger totalQueued;
    private final AtomicInteger totalCompleted;
    
    // Queue capacity to prevent memory issues
    private static final int QUEUE_CAPACITY = 1000;
    
    public IssueKeyQueue() {
        this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.isRunning = new AtomicBoolean(false);
        this.totalQueued = new AtomicInteger(0);
        this.totalCompleted = new AtomicInteger(0);
    }
    
    /**
     * Adds an issue key task to the queue
     */
    public boolean addIssueKey(String projectKey, String issueKey) {
        if (!isRunning.get()) {
            return false;
        }
        
        try {
            IssueKeyTask task = new IssueKeyTask(projectKey, issueKey);
            boolean added = queue.offer(task);
            
            if (added) {
                totalQueued.incrementAndGet();
                logger.fine("Added issue key to queue: " + issueKey);
            } else {
                logger.warning("Queue is full, could not add issue key: " + issueKey);
            }
            
            return added;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error adding issue key to queue: " + issueKey, e);
            return false;
        }
    }
    
    /**
     * Gets the next issue key task from the queue (blocking)
     */
    public IssueKeyTask getNextIssueKey() throws InterruptedException {
        return queue.take();
    }
    
    /**
     * Gets the next issue key task from the queue (non-blocking)
     */
    public IssueKeyTask pollNextIssueKey() {
        return queue.poll();
    }
    
    /**
     * Marks an issue as completed
     */
    public void markCompleted() {
        totalCompleted.incrementAndGet();
    }
    
    /**
     * Starts the queue
     */
    public void start() {
        isRunning.set(true);
        logger.info("Issue key queue started");
    }
    
    /**
     * Stops the queue
     */
    public void stop() {
        isRunning.set(false);
        logger.info("Issue key queue stopped");
    }
    
    /**
     * Checks if the queue is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }
    
    /**
     * Gets the current queue size
     */
    public int getQueueSize() {
        return queue.size();
    }
    
    /**
     * Gets queue statistics
     */
    public QueueStats getStats() {
        return new QueueStats(
            totalQueued.get(),
            totalCompleted.get(),
            queue.size(),
            isRunning.get()
        );
    }
    
    /**
     * Clears the queue
     */
    public void clear() {
        queue.clear();
        totalQueued.set(0);
        totalCompleted.set(0);
        logger.info("Issue key queue cleared");
    }
    
    /**
     * Task representing an issue key to be scraped
     */
    public static class IssueKeyTask {
        private final String projectKey;
        private final String issueKey;
        private final long timestamp;
        
        public IssueKeyTask(String projectKey, String issueKey) {
            this.projectKey = projectKey;
            this.issueKey = issueKey;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getProjectKey() { return projectKey; }
        public String getIssueKey() { return issueKey; }
        public long getTimestamp() { return timestamp; }
        
        @Override
        public String toString() {
            return "IssueKeyTask{" +
                    "projectKey='" + projectKey + '\'' +
                    ", issueKey='" + issueKey + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * Statistics about the queue
     */
    public static class QueueStats {
        private final int totalQueued;
        private final int totalCompleted;
        private final int currentSize;
        private final boolean isRunning;
        
        public QueueStats(int totalQueued, int totalCompleted, int currentSize, boolean isRunning) {
            this.totalQueued = totalQueued;
            this.totalCompleted = totalCompleted;
            this.currentSize = currentSize;
            this.isRunning = isRunning;
        }
        
        public int getTotalQueued() { return totalQueued; }
        public int getTotalCompleted() { return totalCompleted; }
        public int getCurrentSize() { return currentSize; }
        public boolean isRunning() { return isRunning; }
        public int getPending() { return totalQueued - totalCompleted; }
        
        @Override
        public String toString() {
            return String.format("QueueStats{queued=%d, completed=%d, pending=%d, currentSize=%d, running=%s}",
                totalQueued, totalCompleted, getPending(), currentSize, isRunning);
        }
    }
}