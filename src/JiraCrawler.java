import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main orchestrator for the Jira data scraping and transformation pipeline
 */
public class JiraCrawler {
    private static final Logger logger = Logger.getLogger(JiraCrawler.class.getName());
    
    private final JiraWebScraper webScraper;
    private final DataTransformer transformer;
    private final StateManager stateManager;
    private final DataWriter dataWriter;

    // Get target projects from configuration
    private static final List<String> TARGET_PROJECTS = CrawlerConfig.TARGET_PROJECTS;
    
    public JiraCrawler() {
        this.webScraper = new JiraWebScraper();
        this.transformer = new DataTransformer();
        this.stateManager = new StateManager();
        this.dataWriter = new DataWriter();
    }
    
    public void crawlAndTransform() {
        logger.info("Starting Jira crawling pipeline for projects: " + TARGET_PROJECTS);
        
        try {
            // Print initial state summary
            stateManager.printStateSummary();
            
            for (String project : TARGET_PROJECTS) {
                crawlProject(project);
            }
            
            // Print final statistics
            dataWriter.printStatistics();
            
        } finally {
            dataWriter.close();
        }
    }
    
    private void crawlProject(String projectKey) {
        logger.info("Crawling project: " + projectKey);
        
        try {
            CrawlState state = stateManager.loadState(projectKey);
            
            // Modified approach: scrape issues one by one and save immediately
            scrapeProjectIncrementally(projectKey, state);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error crawling project " + projectKey, e);
        }
    }
    
    /**
     * Scrapes a project incrementally, saving state and JSON after each successful issue scrape
     */
    private void scrapeProjectIncrementally(String projectKey, CrawlState state) throws Exception {
        int startAt = state.getLastProcessedIndex();
        boolean hasMore = true;
        int processedCount = 0;
        
        logger.info(String.format("Starting incremental scrape for project %s from index %d", 
            projectKey, startAt));
        
        while (hasMore) {
            // Build search URL for web scraping
            String searchUrl = String.format("%s/issues/?jql=project%%3D%s&startIndex=%d", 
                CrawlerConfig.JIRA_BASE_URL, projectKey, startAt);
            
            // Get issue keys from search page
            List<String> issueKeys = webScraper.getIssueKeysFromSearchPage(searchUrl);
            
            if (issueKeys.isEmpty()) {
                logger.info("No more issues found for project " + projectKey);
                break;
            }
            int currentBatchProcessedCount = 0;
            
            // Process each issue individually with immediate state saving
            for (String issueKey : issueKeys) {
                try {
                    // Skip if already processed
                    if (issueKey.equals(state.getLastProcessedIssue())) {
                        logger.info("Skipping already processed issue: " + issueKey);
                        continue;
                    }
                    
                    logger.info(String.format("Scraping issue %s (%d/%d in batch)", 
                        issueKey, currentBatchProcessedCount + 1, issueKeys.size()));
                    
                    // Scrape individual issue
                    JiraIssue issue = webScraper.scrapeIssueDetails(issueKey);
                    
                    if (issue != null) {
                        // Transform to JSONL format
                        String jsonlRecord = transformer.transformToJsonl(issue);
                        
                        // Write to output file immediately
                        writeToOutput(projectKey, jsonlRecord);
                        
                        // Update and save state immediately after successful scrape
                        state.setLastProcessedIssue(issueKey);
                        state.setLastProcessedIndex(startAt + processedCount + 1);
                        state.setTotalProcessed(state.getTotalProcessed() + 1);
                        state.setLastUpdateTime(System.currentTimeMillis());
                        
                        // Save state to disk
                        stateManager.saveState(state);
                        
                        // Flush output to ensure data is written
                        dataWriter.flush();
                        
                        logger.info(String.format("Successfully processed and saved issue %s (total: %d)", 
                            issueKey, state.getTotalProcessed()));
                        
                        processedCount++;
                        currentBatchProcessedCount++;
                    } else {
                        logger.warning("Failed to scrape issue: " + issueKey);
                    }
                    
                    // Rate limiting between requests
                    Thread.sleep(CrawlerConfig.CRAWL_DELAY_MS);
                    
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error processing issue " + issueKey, e);
                    
                    // Continue with next issue even if one fails
                    continue;
                }
            }
            
            startAt += issueKeys.size();
            hasMore = issueKeys.size() >= 50; // Assume page size of 50
            
            logger.info(String.format("Completed batch for project %s. Processed %d issues in this batch.", 
                projectKey, processedCount));
        }
        
        logger.info(String.format("Completed scraping project %s. Total issues processed: %d", 
            projectKey, state.getTotalProcessed()));
    }

    private void writeToOutput(String project, String jsonlRecord) {
        dataWriter.writeRecord(project, jsonlRecord);
    }
    
    public static void main(String[] args) {
        new JiraCrawler().crawlAndTransform();
    }
}