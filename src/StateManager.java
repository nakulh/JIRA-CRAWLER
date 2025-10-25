import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages crawling state to enable resumption after interruptions
 */
public class StateManager {
    private static final Logger logger = Logger.getLogger(StateManager.class.getName());
    private static final String STATE_DIR = "crawl_state";
    private static final String STATE_FILE_SUFFIX = "_state.properties";
    
    public StateManager() {
        createStateDirectory();
    }
    
    /**
     * Loads the crawling state for a project
     */
    public CrawlState loadState(String projectKey) {
        Path stateFile = getStateFilePath(projectKey);
        
        if (!Files.exists(stateFile)) {
            logger.info("No existing state found for project " + projectKey + ", starting fresh");
            return new CrawlState(projectKey);
        }
        
        try {
            Properties props = new Properties();
            try (InputStream input = Files.newInputStream(stateFile)) {
                props.load(input);
            }
            
            CrawlState state = new CrawlState(projectKey);
            state.setLastProcessedIndex(Integer.parseInt(props.getProperty("lastProcessedIndex", "0")));
            state.setLastProcessedIssue(props.getProperty("lastProcessedIssue", ""));
            state.setTotalProcessed(Integer.parseInt(props.getProperty("totalProcessed", "0")));
            state.setLastUpdateTime(Long.parseLong(props.getProperty("lastUpdateTime", "0")));
            
            logger.info(String.format("Loaded state for %s: last index=%d, last issue=%s", 
                projectKey, state.getLastProcessedIndex(), state.getLastProcessedIssue()));
            
            return state;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading state for " + projectKey + ", starting fresh", e);
            return new CrawlState(projectKey);
        }
    }
    
    /**
     * Updates the crawling state for a project
     */
    public void updateState(String projectKey, String lastProcessedIssue) {
        try {
            CrawlState currentState = loadState(projectKey);
            currentState.setLastProcessedIssue(lastProcessedIssue);
            currentState.setTotalProcessed(currentState.getTotalProcessed() + 1);
            currentState.setLastUpdateTime(System.currentTimeMillis());
            
            saveState(currentState);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error updating state for " + projectKey, e);
        }
    }
    
    /**
     * Saves the crawling state to disk
     */
    public void saveState(CrawlState state) throws IOException {
        Properties props = new Properties();
        props.setProperty("projectKey", state.getProjectKey());
        props.setProperty("lastProcessedIndex", String.valueOf(state.getLastProcessedIndex()));
        props.setProperty("lastProcessedIssue", state.getLastProcessedIssue());
        props.setProperty("totalProcessed", String.valueOf(state.getTotalProcessed()));
        props.setProperty("lastUpdateTime", String.valueOf(state.getLastUpdateTime()));
        
        Path stateFile = getStateFilePath(state.getProjectKey());
        try (OutputStream output = Files.newOutputStream(stateFile)) {
            props.store(output, "Crawl state for project " + state.getProjectKey());
        }
        
        logger.fine("Saved state for project " + state.getProjectKey());
    }
    
    /**
     * Resets the state for a project (useful for full re-crawl)
     */
    public void resetState(String projectKey) {
        try {
            Path stateFile = getStateFilePath(projectKey);
            Files.deleteIfExists(stateFile);
            logger.info("Reset state for project " + projectKey);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error resetting state for " + projectKey, e);
        }
    }
    
    /**
     * Gets the file path for storing state
     */
    private Path getStateFilePath(String projectKey) {
        return Paths.get(STATE_DIR, projectKey + STATE_FILE_SUFFIX);
    }
    
    /**
     * Creates the state directory if it doesn't exist
     */
    private void createStateDirectory() {
        try {
            Path stateDir = Paths.get(STATE_DIR);
            if (!Files.exists(stateDir)) {
                Files.createDirectories(stateDir);
                logger.info("Created state directory: " + stateDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error creating state directory", e);
        }
    }
    
    /**
     * Gets summary of all project states
     */
    public void printStateSummary() {
        try {
            Path stateDir = Paths.get(STATE_DIR);
            if (!Files.exists(stateDir)) {
                System.out.println("No crawl states found.");
                return;
            }
            
            System.out.println("Crawl State Summary:");
            System.out.println("===================");
            
            Files.list(stateDir)
                .filter(path -> path.toString().endsWith(STATE_FILE_SUFFIX))
                .forEach(path -> {
                    String projectKey = path.getFileName().toString()
                        .replace(STATE_FILE_SUFFIX, "");
                    CrawlState state = loadState(projectKey);
                    System.out.printf("Project: %s | Processed: %d issues | Last: %s%n",
                        projectKey, state.getTotalProcessed(), state.getLastProcessedIssue());
                });
                
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading state summary", e);
        }
    }
}