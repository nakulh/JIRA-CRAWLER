import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Worker thread that consumes issue keys from the queue and scrapes issue data
 * Multiple workers can run concurrently to improve throughput
 */
public class IssueScrapingWorker implements Runnable {
    private static final Logger logger = Logger.getLogger(IssueScrapingWorker.class.getName());
    
    private final int workerId;
    private final IssueKeyQueue queue;
    private final JiraWebScraper webScraper;
    private final DomainRateLimiter rateLimiter;
    private final DataTransformer transformer;
    private final DataWriter dataWriter;
    private final ThreadSafeStateManager stateManager;
    private final AtomicBoolean shouldStop;
    private final AtomicInteger processedCount;
    
    private static final String JIRA_DOMAIN = "issues.apache.org";
    private static final int POLL_TIMEOUT_MS = 5000;
    
    public IssueScrapingWorker(int workerId, IssueKeyQueue queue, JiraWebScraper webScraper,
                              DomainRateLimiter rateLimiter, DataTransformer transformer,
                              DataWriter dataWriter, ThreadSafeStateManager stateManager) {
        this.workerId = workerId;
        this.queue = queue;
        this.webScraper = webScraper;
        this.rateLimiter = rateLimiter;
        this.transformer = transformer;
        this.dataWriter = dataWriter;
        this.stateManager = stateManager;
        this.shouldStop = new AtomicBoolean(false);
        this.processedCount = new AtomicInteger(0);
    }
    
    @Override
    public void run() {
        logger.info("Starting issue scraping worker " + workerId);
        
        try {
            while (!shouldStop.get() && queue.isRunning()) {
                try {
                    // Get next issue key from queue (with timeout)
                    IssueKeyQueue.IssueKeyTask task = pollWithTimeout();
                    
                    if (task == null) {
                        // No tasks available, continue polling
                        continue;
                    }
                    
                    logger.info(String.format("Worker %d processing issue: %s", 
                        workerId, task.getIssueKey()));
                    
                    // Process the issue
                    boolean success = processIssue(task);
                    
                    if (success) {
                        queue.markCompleted();
                        processedCount.incrementAndGet();
                        
                        logger.info(String.format("Worker %d successfully processed issue %s (total: %d)", 
                            workerId, task.getIssueKey(), processedCount.get()));
                    } else {
                        logger.warning(String.format("Worker %d failed to process issue: %s", 
                            workerId, task.getIssueKey()));
                    }
                    
                } catch (InterruptedException e) {
                    logger.info("Issue scraping worker " + workerId + " interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in issue scraping worker " + workerId, e);
                    
                    // Brief pause before continuing
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fatal error in issue scraping worker " + workerId, e);
        } finally {
            logger.info(String.format("Issue scraping worker %d finished (processed %d issues)", 
                workerId, processedCount.get()));
        }
    }
    
    /**
     * Processes a single issue task
     */
    private boolean processIssue(IssueKeyQueue.IssueKeyTask task) {
        try {
            // Check if already processed (thread-safe, idempotent)
            if (stateManager.isIssueProcessed(task.getProjectKey(), task.getIssueKey())) {
                logger.fine("Issue already processed by another worker: " + task.getIssueKey());
                return true; // Already processed, consider it successful
            }
            
            // Wait for rate limiting permission
            rateLimiter.waitForPermission(JIRA_DOMAIN);
            
            // Scrape issue details
            JiraIssue issue = webScraper.scrapeIssueDetails(task.getIssueKey());
            
            // Record the request for rate limiting
            rateLimiter.recordRequest(JIRA_DOMAIN);
            
            if (issue == null) {
                logger.warning("Failed to scrape issue: " + task.getIssueKey());
                return false;
            }
            
            // Transform to JSONL format
            String jsonlRecord = transformer.transformToJsonl(issue);
            
            if (jsonlRecord.isEmpty()) {
                logger.warning("Failed to transform issue to JSONL: " + task.getIssueKey());
                return false;
            }
            
            // Write to output file immediately
            dataWriter.writeRecord(task.getProjectKey(), jsonlRecord);
            
            // Record as processed (thread-safe, prevents race conditions)
            boolean recorded = stateManager.recordProcessedIssue(task.getProjectKey(), task.getIssueKey());
            
            if (!recorded) {
                logger.fine("Issue was processed by another worker concurrently: " + task.getIssueKey());
                // Still return true as the issue was processed successfully
            }
            
            // Flush output to ensure data is written
            dataWriter.flush();
            
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error processing issue " + task.getIssueKey(), e);
            return false;
        }
    }
    

    
    /**
     * Polls the queue with a timeout to avoid blocking indefinitely
     */
    private IssueKeyQueue.IssueKeyTask pollWithTimeout() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < POLL_TIMEOUT_MS) {
            IssueKeyQueue.IssueKeyTask task = queue.pollNextIssueKey();
            if (task != null) {
                return task;
            }
            
            // Short sleep to avoid busy waiting
            Thread.sleep(100);
        }
        
        return null; // Timeout reached
    }
    
    /**
     * Stops the worker
     */
    public void stop() {
        shouldStop.set(true);
        logger.info("Stopping issue scraping worker " + workerId);
    }
    
    /**
     * Gets the number of issues processed by this worker
     */
    public int getProcessedCount() {
        return processedCount.get();
    }
    
    /**
     * Checks if the worker should stop
     */
    public boolean shouldStop() {
        return shouldStop.get();
    }
}