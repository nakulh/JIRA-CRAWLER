import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main orchestrator for the multi-threaded Jira data scraping and transformation pipeline
 */
public class JiraCrawler {
    private static final Logger logger = Logger.getLogger(JiraCrawler.class.getName());
    
    private final JiraWebScraper webScraper;
    private final DataTransformer transformer;
    private final StateManager stateManager;
    private final DataWriter dataWriter;
    private final IssueKeyQueue issueQueue;
    private final DomainRateLimiter rateLimiter;
    private final ExecutorService executorService;
    
    private final List<IssueKeyProducer> producers;
    private final List<IssueScrapingWorker> workers;

    // Get target projects from configuration
    private static final List<String> TARGET_PROJECTS = CrawlerConfig.TARGET_PROJECTS;
    
    public JiraCrawler() {
        this.webScraper = new JiraWebScraper();
        this.transformer = new DataTransformer();
        this.stateManager = new StateManager();
        this.dataWriter = new DataWriter();
        this.issueQueue = new IssueKeyQueue();
        this.rateLimiter = new DomainRateLimiter(CrawlerConfig.CRAWL_DELAY_MS);
        
        // Create thread pool for producers and workers
        int totalThreads = CrawlerConfig.PRODUCER_THREAD_COUNT + CrawlerConfig.WORKER_THREAD_COUNT;
        this.executorService = Executors.newFixedThreadPool(totalThreads);
        
        this.producers = new ArrayList<>();
        this.workers = new ArrayList<>();
    }
    
    public void crawlAndTransform() {
        logger.info("Starting multi-threaded Jira crawling pipeline for projects: " + TARGET_PROJECTS);
        
        try {
            // Print initial state summary
            stateManager.printStateSummary();
            
            // Start the queue
            issueQueue.start();
            
            // Start producer threads for each project
            startProducers();
            
            // Start worker threads
            startWorkers();
            
            // Monitor progress
            monitorProgress();
            
            // Print final statistics
            dataWriter.printStatistics();
            
        } finally {
            shutdown();
        }
    }
    
    /**
     * Starts producer threads for each project
     */
    private void startProducers() {
        logger.info("Starting producer threads for projects: " + TARGET_PROJECTS);
        
        for (String projectKey : TARGET_PROJECTS) {
            IssueKeyProducer producer = new IssueKeyProducer(
                issueQueue, webScraper, rateLimiter, stateManager, projectKey);
            
            producers.add(producer);
            executorService.submit(producer);
            
            logger.info("Started producer for project: " + projectKey);
        }
    }
    
    /**
     * Starts worker threads for scraping issues
     */
    private void startWorkers() {
        logger.info("Starting " + CrawlerConfig.WORKER_THREAD_COUNT + " worker threads");
        
        for (int i = 0; i < CrawlerConfig.WORKER_THREAD_COUNT; i++) {
            IssueScrapingWorker worker = new IssueScrapingWorker(
                i + 1, issueQueue, webScraper, rateLimiter, 
                transformer, dataWriter, stateManager);
            
            workers.add(worker);
            executorService.submit(worker);
            
            logger.info("Started worker thread: " + (i + 1));
        }
    }
    
    /**
     * Monitors the progress of the crawling operation
     */
    private void monitorProgress() {
        logger.info("Starting progress monitoring");
        
        try {
            while (issueQueue.isRunning()) {
                Thread.sleep(30000); // Check every 30 seconds
                
                // Print queue statistics
                IssueKeyQueue.QueueStats queueStats = issueQueue.getStats();
                logger.info("Queue Status: " + queueStats);
                
                // Print rate limiter statistics
                Map<String, DomainRateLimiter.DomainStats> rateLimiterStats = rateLimiter.getAllStats();
                for (Map.Entry<String, DomainRateLimiter.DomainStats> entry : rateLimiterStats.entrySet()) {
                    logger.info("Rate Limiter: " + entry.getValue());
                }
                
                // Print worker statistics
                int totalProcessed = workers.stream().mapToInt(IssueScrapingWorker::getProcessedCount).sum();
                logger.info("Total issues processed by all workers: " + totalProcessed);
                
                // Check if all producers are done and queue is empty
                boolean allProducersDone = producers.stream().allMatch(IssueKeyProducer::shouldStop);
                if (allProducersDone && queueStats.getCurrentSize() == 0) {
                    logger.info("All producers finished and queue is empty. Stopping crawling.");
                    break;
                }
            }
            
        } catch (InterruptedException e) {
            logger.info("Progress monitoring interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in progress monitoring", e);
        }
    }
    
    /**
     * Shuts down all threads and resources
     */
    private void shutdown() {
        logger.info("Shutting down crawler");
        
        try {
            // Stop producers
            for (IssueKeyProducer producer : producers) {
                producer.stop();
            }
            
            // Stop workers
            for (IssueScrapingWorker worker : workers) {
                worker.stop();
            }
            
            // Stop queue
            issueQueue.stop();
            
            // Shutdown executor service
            executorService.shutdown();
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("Executor service did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
            
            // Shutdown rate limiter
            rateLimiter.shutdown();
            
            // Close data writer
            dataWriter.close();
            
            logger.info("Crawler shutdown complete");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during shutdown", e);
        }
    }
    
    public static void main(String[] args) {
        new JiraCrawler().crawlAndTransform();
    }
}