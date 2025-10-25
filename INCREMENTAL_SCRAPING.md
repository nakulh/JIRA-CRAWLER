# Incremental Web Scraping with State Persistence

## Overview

The Jira web scraper now implements incremental scraping with immediate state and data persistence after each successful webpage scrape. This ensures maximum fault tolerance and allows resumption from any interruption point.

## Key Features

### 🔄 Incremental Processing
- **Issue-by-issue processing**: Each issue is scraped, transformed, and saved individually
- **Immediate state saving**: Progress is saved to disk after each successful issue
- **Immediate JSON writing**: JSONL data is written and flushed after each issue
- **Resume capability**: Can restart from the exact last processed issue

### 💾 State Persistence Strategy

#### State File Structure
```
crawl_state/
├── KAFKA_state.properties
├── SPARK_state.properties
└── HADOOP_state.properties
```

#### State Properties Saved
```properties
projectKey=KAFKA
lastProcessedIndex=150
lastProcessedIssue=KAFKA-12345
totalProcessed=150
lastUpdateTime=1698234567890
```

### 📊 Data Persistence Strategy

#### Output File Structure
```
output/
├── kafka_20241025_181130.jsonl    # Incremental JSONL data
├── spark_20241025_181145.jsonl
└── hadoop_20241025_181200.jsonl
```

#### Immediate Writing Process
1. **Scrape Issue**: Extract data from HTML page
2. **Transform**: Convert to JSONL training format
3. **Write**: Append to output file immediately
4. **Flush**: Force write to disk
5. **Update State**: Save progress to state file
6. **Log**: Record successful completion

## Implementation Details

### JiraCrawler.scrapeProjectIncrementally()

```java
private void scrapeProjectIncrementally(String projectKey, CrawlState state) {
    // For each search page
    while (hasMore) {
        List<String> issueKeys = webScraper.getIssueKeysFromSearchPage(searchUrl);
        
        // Process each issue individually
        for (String issueKey : issueKeys) {
            // 1. Scrape issue details
            JiraIssue issue = webScraper.scrapeIssueDetails(issueKey);
            
            // 2. Transform to JSONL
            String jsonlRecord = transformer.transformToJsonl(issue);
            
            // 3. Write immediately
            writeToOutput(projectKey, jsonlRecord);
            
            // 4. Update state
            state.setLastProcessedIssue(issueKey);
            state.setTotalProcessed(state.getTotalProcessed() + 1);
            
            // 5. Save state to disk
            stateManager.saveState(state);
            
            // 6. Flush output
            dataWriter.flush();
        }
    }
}
```

### Resume Logic

```java
// Skip already processed issues
if (issueKey.equals(state.getLastProcessedIssue())) {
    logger.info("Skipping already processed issue: " + issueKey);
    continue;
}
```

## Fault Tolerance Benefits

### 🛡️ Interruption Recovery
- **Network failures**: Resume from last successful issue
- **Application crashes**: No data loss, exact resume point
- **Manual stops**: Graceful resumption on restart
- **System reboots**: Complete state preservation

### 📈 Progress Tracking
- **Real-time progress**: Live updates in state files
- **Detailed logging**: Per-issue success/failure tracking
- **Statistics**: Running totals and timestamps
- **Monitoring**: Easy to check current status

### 🔧 Operational Advantages
- **No batch loss**: Individual issue failures don't affect others
- **Incremental delivery**: Data available immediately as scraped
- **Resource efficiency**: Memory usage stays constant
- **Debugging**: Easy to identify problematic issues

## Usage Examples

### Starting Fresh
```bash
mvn exec:java
# Creates new state files, starts from beginning
```

### Resuming After Interruption
```bash
mvn exec:java
# Automatically detects existing state, resumes from last issue
```

### Checking Progress
```bash
mvn exec:java -Dexec.args="status"
# Shows current state for all projects
```

### Resetting Specific Project
```bash
mvn exec:java -Dexec.args="reset KAFKA"
# Clears state for KAFKA project, will restart from beginning
```

## Performance Characteristics

### Throughput
- **Rate limited**: 1 second between requests (configurable)
- **Concurrent projects**: Multiple projects can run in parallel
- **Efficient parsing**: Jsoup HTML parsing with CSS selectors
- **Minimal memory**: Streaming processing, no large collections

### Reliability
- **Exponential backoff**: Automatic retry on failures
- **Multiple selectors**: Robust data extraction
- **Graceful degradation**: Continues on individual failures
- **State consistency**: Atomic state updates

## Monitoring and Debugging

### Log Output Example
```
INFO: Starting incremental scrape for project KAFKA from index 0
INFO: Scraping issue KAFKA-12345 (1/50 in batch)
INFO: Successfully processed and saved issue KAFKA-12345 (total: 1)
INFO: Scraping issue KAFKA-12346 (2/50 in batch)
INFO: Successfully processed and saved issue KAFKA-12346 (total: 2)
```

### State File Monitoring
```bash
# Watch state files for real-time progress
tail -f crawl_state/KAFKA_state.properties
```

### Output File Monitoring
```bash
# Count lines in output (each line = one training example)
wc -l output/kafka_*.jsonl
```

## Configuration Options

### Rate Limiting
```java
// In CrawlerConfig.java
public static final int RATE_LIMIT_DELAY_MS = 1000; // 1 second between requests
```

### Batch Size
```java
public static final int BATCH_SIZE = 50; // Issues per search page
```

### Retry Logic
```java
public static final int MAX_RETRIES = 3; // Retry attempts per failed request
```

This incremental approach ensures that the web scraping pipeline is highly fault-tolerant and can handle long-running scraping jobs with confidence, saving progress continuously and enabling seamless resumption from any interruption point.