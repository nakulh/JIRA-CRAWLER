import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main entry point for the Jira Data Scraping and Transformation Pipeline
 */
public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    
    public static void main(String[] args) {
        System.out.println("Apache Jira Data Scraping Pipeline");
        System.out.println("==================================");
        
        try {
            // Validate configuration
            CrawlerConfig.validateConfig();
            CrawlerConfig.printConfig();
            
            // Initialize and run the crawler
            JiraCrawler crawler = new JiraCrawler();
            
            // Handle command line arguments
            if (args.length > 0) {
                handleCommands(args, crawler);
            } else {
                // Default: run full crawl
                crawler.crawlAndTransform();
            }
            
            System.out.println("Pipeline completed successfully!");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Pipeline failed", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Handles command line arguments for different operations
     */
    private static void handleCommands(String[] args, JiraCrawler crawler) {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "crawl":
                crawler.crawlAndTransform();
                break;
                
            case "status":
                ThreadSafeStateManager stateManager = new ThreadSafeStateManager();
                stateManager.printStateSummary();
                
                DataWriter dataWriter = new DataWriter();
                dataWriter.printStatistics();
                dataWriter.close();
                stateManager.shutdown();
                break;
                
            case "reset":
                if (args.length > 1) {
                    String project = args[1].toUpperCase();
                    ThreadSafeStateManager resetManager = new ThreadSafeStateManager();
                    resetManager.resetState(project);
                    resetManager.shutdown();
                    System.out.println("Reset state for project: " + project);
                } else {
                    System.out.println("Usage: java Main reset <PROJECT_KEY>");
                }
                break;
                
            case "help":
            default:
                printUsage();
                break;
        }
    }
    
    /**
     * Prints usage information
     */
    private static void printUsage() {
        System.out.println("Usage: java Main [command] [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  crawl          - Run the full crawling pipeline (default)");
        System.out.println("  status         - Show current crawling status and output statistics");
        System.out.println("  reset <project> - Reset crawling state for a specific project");
        System.out.println("  help           - Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java Main                    # Run full crawl");
        System.out.println("  java Main status             # Show status");
        System.out.println("  java Main reset KAFKA        # Reset KAFKA project state");
    }
}