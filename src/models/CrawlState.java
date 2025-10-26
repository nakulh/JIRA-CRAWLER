package models;

/**
 * Represents the current state of crawling for a project
 */
public class CrawlState {
    private final String projectKey;
    private int lastProcessedIndex;
    private String lastProcessedIssue;
    private int totalProcessed;
    private long lastUpdateTime;
    
    public CrawlState(String projectKey) {
        this.projectKey = projectKey;
        this.lastProcessedIndex = 0;
        this.lastProcessedIssue = "";
        this.totalProcessed = 0;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    // Getters
    public String getProjectKey() { return projectKey; }
    public int getLastProcessedIndex() { return lastProcessedIndex; }
    public String getLastProcessedIssue() { return lastProcessedIssue; }
    public int getTotalProcessed() { return totalProcessed; }
    public long getLastUpdateTime() { return lastUpdateTime; }
    
    // Setters
    public void setLastProcessedIndex(int lastProcessedIndex) {
        this.lastProcessedIndex = lastProcessedIndex;
    }
    
    public void setLastProcessedIssue(String lastProcessedIssue) {
        this.lastProcessedIssue = lastProcessedIssue != null ? lastProcessedIssue : "";
    }
    
    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }
    
    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    @Override
    public String toString() {
        return "CrawlState{" +
                "projectKey='" + projectKey + '\'' +
                ", lastProcessedIndex=" + lastProcessedIndex +
                ", lastProcessedIssue='" + lastProcessedIssue + '\'' +
                ", totalProcessed=" + totalProcessed +
                ", lastUpdateTime=" + lastUpdateTime +
                '}';
    }
}