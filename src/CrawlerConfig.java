import java.util.Arrays;
import java.util.List;

/**
 * Configuration settings for the Jira crawler
 */
public class CrawlerConfig {
    
    // Web Scraping Configuration
    public static final String JIRA_BASE_URL = "https://issues.apache.org/jira";
    public static final int MAX_RETRIES = 3;
    public static final int CRAWL_DELAY_MS = 2000;
    public static final int REQUEST_TIMEOUT_SECONDS = 30;
    
    // Threading Configuration
    public static final int WORKER_THREAD_COUNT = 4;
    public static final int PRODUCER_THREAD_COUNT = 1;
    public static final int QUEUE_CAPACITY = 1000;
    
    // Target Projects (can be modified to scrape different projects)
    public static final List<String> TARGET_PROJECTS = Arrays.asList(
        "ACE",
        "SPARK",
        "HADOOP"
    );
    
    // Output Configuration
    public static final String OUTPUT_DIR = "output";
    public static final String STATE_DIR = "crawl_state";
    
    // Data Processing Configuration
    public static final boolean INCLUDE_COMMENTS = true;
    public static final boolean CLEAN_HTML_MARKUP = true;
    public static final int MAX_COMMENT_LENGTH = 5000;
    
    // Logging Configuration
    public static final boolean ENABLE_DEBUG_LOGGING = false;
    
    private CrawlerConfig() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates the configuration settings
     */
    public static void validateConfig() {
        if (TARGET_PROJECTS.isEmpty()) {
            throw new IllegalStateException("At least one target project must be specified");
        }
    }
    
    /**
     * Prints current configuration
     */
    public static void printConfig() {
        System.out.println("Jira Web Scraper Configuration:");
        System.out.println("===============================");
        System.out.println("Scraping Method: Multi-threaded HTML Web Scraping");
        System.out.println("Target Projects: " + TARGET_PROJECTS);
        System.out.println("Worker Threads: " + WORKER_THREAD_COUNT);
        System.out.println("Producer Threads: " + PRODUCER_THREAD_COUNT);
        System.out.println("Queue Capacity: " + QUEUE_CAPACITY);
        System.out.println("Crawl Delay: " + CRAWL_DELAY_MS + "ms");
        System.out.println("Max Retries: " + MAX_RETRIES);
        System.out.println("Include Comments: " + INCLUDE_COMMENTS);
        System.out.println("Output Directory: " + OUTPUT_DIR);
        System.out.println("State Directory: " + STATE_DIR);
        System.out.println();
    }
}