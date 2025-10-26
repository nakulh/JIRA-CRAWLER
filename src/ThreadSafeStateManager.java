import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import models.CrawlState;

/**
 * Thread-safe state manager that handles concurrent updates from multiple worker threads
 * Prevents race conditions and ensures state consistency
 */
public class ThreadSafeStateManager {
    private static final Logger logger = Logger.getLogger(ThreadSafeStateManager.class.getName());
    private static final String STATE_DIR = "crawl_state";
    private static final String STATE_FILE_SUFFIX = "_state.properties";
    private static final String PROCESSED_ISSUES_SUFFIX = "_processed.txt";
    
    // Thread-safe collections for tracking state
    private final ConcurrentHashMap<String, CrawlState> stateCache;
    private final ConcurrentHashMap<String, ReentrantReadWriteLock> stateLocks;
    private final ConcurrentHashMap<String, Set<String>> processedIssues;
    
    public ThreadSafeStateManager() {
        this.stateCache = new ConcurrentHashMap<>();
        this.stateLocks = new ConcurrentHashMap<>();
        this.processedIssues = new ConcurrentHashMap<>();
        createStateDirectory();
    }
    
    /**
     * Loads the crawling state for a project (thread-safe)
     */
    public CrawlState loadState(String projectKey) {
        return stateCache.computeIfAbsent(projectKey, this::loadStateFromDisk);
    }
    
    /**
     * Records that an issue has been successfully processed (thread-safe)
     * This method ensures proper ordering and prevents race conditions
     */
    public synchronized boolean recordProcessedIssue(String projectKey, String issueKey) {
        try {
            // Get or create the lock for this project
            ReentrantReadWriteLock lock = stateLocks.computeIfAbsent(projectKey, 
                k -> new ReentrantReadWriteLock());
            
            lock.writeLock().lock();
            try {
                // Get processed issues set for this project
                Set<String> processed = processedIssues.computeIfAbsent(projectKey, 
                    k -> loadProcessedIssues(projectKey));
                
                // Check if already processed (idempotent operation)
                if (processed.contains(issueKey)) {
                    logger.fine("Issue already processed: " + issueKey);
                    return false; // Already processed
                }
                
                // Add to processed set
                processed.add(issueKey);
                
                // Update state
                CrawlState state = stateCache.get(projectKey);
                if (state == null) {
                    state = loadStateFromDisk(projectKey);
                    stateCache.put(projectKey, state);
                }
                
                // Update state with latest information
                state.setLastProcessedIssue(issueKey);
                state.setTotalProcessed(processed.size());
                state.setLastUpdateTime(System.currentTimeMillis());
                
                // Save both state and processed issues list
                saveStateToDisc(state);
                saveProcessedIssues(projectKey, processed);
                
                logger.fine(String.format("Recorded processed issue %s for project %s (total: %d)", 
                    issueKey, projectKey, processed.size()));
                
                return true; // Successfully recorded
                
            } finally {
                lock.writeLock().unlock();
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error recording processed issue " + issueKey + " for project " + projectKey, e);
            return false;
        }
    }
    
    /**
     * Checks if an issue has already been processed (thread-safe)
     */
    public boolean isIssueProcessed(String projectKey, String issueKey) {
        ReentrantReadWriteLock lock = stateLocks.computeIfAbsent(projectKey, 
            k -> new ReentrantReadWriteLock());
        
        lock.readLock().lock();
        try {
            Set<String> processed = processedIssues.computeIfAbsent(projectKey, 
                k -> loadProcessedIssues(projectKey));
            return processed.contains(issueKey);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current state for a project (thread-safe)
     */
    public CrawlState getCurrentState(String projectKey) {
        ReentrantReadWriteLock lock = stateLocks.computeIfAbsent(projectKey, 
            k -> new ReentrantReadWriteLock());
        
        lock.readLock().lock();
        try {
            CrawlState state = stateCache.get(projectKey);
            if (state == null) {
                state = loadStateFromDisk(projectKey);
                stateCache.put(projectKey, state);
            }
            return state;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Updates the last processed index (for pagination tracking)
     */
    public synchronized void updateLastProcessedIndex(String projectKey, int newIndex) {
        try {
            ReentrantReadWriteLock lock = stateLocks.computeIfAbsent(projectKey, 
                k -> new ReentrantReadWriteLock());
            
            lock.writeLock().lock();
            try {
                CrawlState state = stateCache.get(projectKey);
                if (state == null) {
                    state = loadStateFromDisk(projectKey);
                    stateCache.put(projectKey, state);
                }
                
                // Only update if the new index is greater (progress forward)
                if (newIndex > state.getLastProcessedIndex()) {
                    state.setLastProcessedIndex(newIndex);
                    state.setLastUpdateTime(System.currentTimeMillis());
                    
                    // Save state to disk
                    saveStateToDisc(state);
                    
                    logger.fine(String.format("Updated lastProcessedIndex for project %s to %d", 
                        projectKey, newIndex));
                }
                
            } finally {
                lock.writeLock().unlock();
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating lastProcessedIndex for project " + projectKey, e);
        }
    }
    
    /**
     * Loads state from disk
     */
    private CrawlState loadStateFromDisk(String projectKey) {
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
            
            logger.info(String.format("Loaded state for %s: last index=%d, last issue=%s, total=%d", 
                projectKey, state.getLastProcessedIndex(), state.getLastProcessedIssue(), state.getTotalProcessed()));
            
            return state;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading state for " + projectKey + ", starting fresh", e);
            return new CrawlState(projectKey);
        }
    }
    
    /**
     * Loads the set of processed issues from disk
     */
    private Set<String> loadProcessedIssues(String projectKey) {
        Set<String> processed = new ConcurrentSkipListSet<>();
        Path processedFile = getProcessedIssuesFilePath(projectKey);
        
        if (!Files.exists(processedFile)) {
            return processed;
        }
        
        try {
            Files.lines(processedFile).forEach(line -> {
                String issueKey = line.trim();
                if (!issueKey.isEmpty()) {
                    processed.add(issueKey);
                }
            });
            
            logger.info(String.format("Loaded %d processed issues for project %s", 
                processed.size(), projectKey));
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading processed issues for " + projectKey, e);
        }
        
        return processed;
    }
    
    /**
     * Saves state to disk
     */
    private void saveStateToDisc(CrawlState state) throws IOException {
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
     * Saves the processed issues list to disk
     */
    private void saveProcessedIssues(String projectKey, Set<String> processed) throws IOException {
        Path processedFile = getProcessedIssuesFilePath(projectKey);
        
        try (BufferedWriter writer = Files.newBufferedWriter(processedFile)) {
            for (String issueKey : processed) {
                writer.write(issueKey);
                writer.newLine();
            }
        }
        
        logger.fine(String.format("Saved %d processed issues for project %s", 
            processed.size(), projectKey));
    }
    
    /**
     * Resets the state for a project (useful for full re-crawl)
     */
    public synchronized void resetState(String projectKey) {
        try {
            ReentrantReadWriteLock lock = stateLocks.computeIfAbsent(projectKey, 
                k -> new ReentrantReadWriteLock());
            
            lock.writeLock().lock();
            try {
                // Remove from caches
                stateCache.remove(projectKey);
                processedIssues.remove(projectKey);
                
                // Delete files
                Path stateFile = getStateFilePath(projectKey);
                Path processedFile = getProcessedIssuesFilePath(projectKey);
                
                Files.deleteIfExists(stateFile);
                Files.deleteIfExists(processedFile);
                
                logger.info("Reset state for project " + projectKey);
                
            } finally {
                lock.writeLock().unlock();
            }
            
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
     * Gets the file path for storing processed issues
     */
    private Path getProcessedIssuesFilePath(String projectKey) {
        return Paths.get(STATE_DIR, projectKey + PROCESSED_ISSUES_SUFFIX);
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
                    CrawlState state = getCurrentState(projectKey);
                    System.out.printf("Project: %s | Processed: %d issues | Last: %s%n",
                        projectKey, state.getTotalProcessed(), state.getLastProcessedIssue());
                });
                
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading state summary", e);
        }
    }
    
    /**
     * Shuts down the state manager and releases resources
     */
    public void shutdown() {
        // Clear caches
        stateCache.clear();
        processedIssues.clear();
        stateLocks.clear();
        
        logger.info("Thread-safe state manager shut down");
    }
}