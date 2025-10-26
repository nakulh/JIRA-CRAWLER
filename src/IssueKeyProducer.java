import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Producer thread that generates issue keys and adds them to the queue
 * Runs continuously to discover new issues from search pages
 */
public class IssueKeyProducer implements Runnable {
    private static final Logger logger = Logger.getLogger(IssueKeyProducer.class.getName());
    
    private final IssueKeyQueue queue;
    private final JiraWebScraper webScraper;
    private final DomainRateLimiter rateLimiter;
    private final ThreadSafeStateManager stateManager;
    private final String projectKey;
    private final AtomicBoolean shouldStop;
    
    private static final int ISSUES_PER_PAGE = 50;
    private static final String JIRA_DOMAIN = "issues.apache.org";
    
    public IssueKeyProducer(IssueKeyQueue queue, JiraWebScraper webScraper, 
                           DomainRateLimiter rateLimiter, ThreadSafeStateManager stateManager, 
                           String projectKey) {
        this.queue = queue;
        this.webScraper = webScraper;
        this.rateLimiter = rateLimiter;
        this.stateManager = stateManager;
        this.projectKey = projectKey;
        this.shouldStop = new AtomicBoolean(false);
    }
    
    @Override
    public void run() {
        logger.info("Starting issue key producer for project: " + projectKey);
        
        try {
            CrawlState state = stateManager.getCurrentState(projectKey);
            int startAt = state.getLastProcessedIndex();
            boolean hasMore = true;
            
            while (hasMore && !shouldStop.get() && queue.isRunning()) {
                try {
                    // Wait for rate limiting permission
                    rateLimiter.waitForPermission(JIRA_DOMAIN);
                    
                    // Build search URL
                    String searchUrl = String.format("%s/issues/?jql=project%%3D%s&startIndex=%d", 
                        CrawlerConfig.JIRA_BASE_URL, projectKey, startAt);
                    
                    logger.info(String.format("Producer fetching issue keys for %s from index %d", 
                        projectKey, startAt));
                    
                    // Get issue keys from search page
                    List<String> issueKeys = webScraper.getIssueKeysFromSearchPage(searchUrl);
                    
                    // Record the request for rate limiting
                    rateLimiter.recordRequest(JIRA_DOMAIN);
                    
                    if (issueKeys.isEmpty()) {
                        logger.info("No more issues found for project " + projectKey);
                        hasMore = false;
                        break;
                    }
                    
                    // Add issue keys to queue
                    int addedCount = 0;
                    for (String issueKey : issueKeys) {
                        // Skip if already processed (thread-safe check)
                        if (stateManager.isIssueProcessed(projectKey, issueKey)) {
                            logger.fine("Skipping already processed issue: " + issueKey);
                            continue;
                        }
                        
                        // Add to queue (non-blocking)
                        boolean added = queue.addIssueKey(projectKey, issueKey);
                        if (added) {
                            addedCount++;
                        } else {
                            // Queue is full, wait a bit and try again
                            logger.warning("Queue is full, waiting before adding more issues");
                            Thread.sleep(5000);
                            break;
                        }
                    }
                    
                    logger.info(String.format("Producer added %d issue keys for project %s", 
                        addedCount, projectKey));
                    
                    startAt += issueKeys.size();
                    hasMore = issueKeys.size() >= ISSUES_PER_PAGE;
                    
                    // Small delay between search pages
                    Thread.sleep(1000);
                    
                } catch (InterruptedException e) {
                    logger.info("Issue key producer interrupted for project: " + projectKey);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in issue key producer for project " + projectKey, e);
                    
                    // Wait before retrying
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fatal error in issue key producer for project " + projectKey, e);
        } finally {
            logger.info("Issue key producer finished for project: " + projectKey);
        }
    }
    
    /**
     * Stops the producer
     */
    public void stop() {
        shouldStop.set(true);
        logger.info("Stopping issue key producer for project: " + projectKey);
    }
    
    /**
     * Checks if the producer should stop
     */
    public boolean shouldStop() {
        return shouldStop.get();
    }
}