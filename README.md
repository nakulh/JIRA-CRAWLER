# Jira Web Scraping and Transformation Pipeline

A robust, fault-tolerant Java application that uses web scraping to extract public issue data from Apache's Jira instance and transforms it into a structured JSONL format suitable for training Large Language Models (LLMs).

## Features

### 🚀 Core Capabilities
- **Web scraping approach**: Uses HTML parsing instead of API calls for unrestricted access
- **Multi-project scraping**: Extracts data from Apache Kafka, Spark, and Hadoop projects
- **Comprehensive data extraction**: Issues, comments, metadata, status, priority, assignee, labels, timestamps
- **Intelligent pagination**: Handles large datasets with automatic page navigation
- **Rate limiting**: Respects server limits with configurable delays between requests
- **Fault tolerance**: Robust retry logic with exponential backoff for failed requests
- **Resumable crawling**: State management allows resumption after interruptions
- **Multiple training formats**: Generates summarization, classification, Q&A, and conversation tasks

### 🛡️ Reliability Features
- **HTML parsing resilience**: Multiple CSS selectors for robust data extraction
- **Request failure handling**: Automatic retries for network issues
- **HTTP error handling**: Proper handling of 429 (rate limit) and 5xx responses
- **Data validation**: Handles missing or malformed HTML gracefully
- **State persistence**: Saves progress to enable recovery from interruptions
- **User-Agent rotation**: Mimics real browser requests to avoid blocking

### 📊 Data Transformation
- **Clean JSONL output**: Structured format optimized for LLM training
- **Multiple task types**: 
  - Summarization tasks (issue description → summary)
  - Classification tasks (content → priority/status)
  - Q&A tasks (context → question/answer pairs)
  - Conversation tasks (issue discussions)
- **Text cleaning**: Removes HTML tags, Jira markup, normalizes whitespace
- **Rich metadata**: Preserves all relevant issue information

## Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6 or higher
- Internet connection for web scraping
- Jsoup library (automatically managed by Maven)

### Installation & Running

1. **Clone and build the project:**
```bash
git clone <repository-url>
cd jira-crawler
mvn clean compile
```

2. **Run the crawler:**
```bash
# Full crawl of all configured projects
mvn exec:java

# Or with specific commands
mvn exec:java -Dexec.args="crawl"
mvn exec:java -Dexec.args="status"
mvn exec:java -Dexec.args="reset KAFKA"
```

3. **Alternative: Build and run JAR:**
```bash
mvn clean package
java -jar target/jira-crawler-1.0.0-jar-with-dependencies.jar
```

## Project Structure

```
src/
├── Main.java              # Entry point with CLI interface
├── JiraCrawler.java       # Main orchestrator
├── JiraApiClient.java     # API interaction with retry logic
├── DataTransformer.java   # JSONL transformation engine
├── DataWriter.java        # Output file management
├── StateManager.java      # Crawl state persistence
├── CrawlerConfig.java     # Configuration constants
├── JiraIssue.java        # Issue data model
├── JiraComment.java      # Comment data model
└── CrawlState.java       # State tracking model
```

## Configuration

Edit `src/CrawlerConfig.java` to customize:

```java
// Target projects (modify as needed)
public static final List<String> TARGET_PROJECTS = Arrays.asList(
    "KAFKA", "SPARK", "HADOOP"
);

// Performance settings
public static final int BATCH_SIZE = 50;
public static final int THREAD_POOL_SIZE = 5;
public static final int RATE_LIMIT_DELAY_MS = 1000;
```

## Output Format

The pipeline generates JSONL files in the `output/` directory with multiple training task formats:

### Summarization Task
```json
{
  "metadata": {
    "issue_key": "KAFKA-12345",
    "project": "KAFKA",
    "task_type": "summarization",
    "created": "2023-01-15T10:30:00.000Z",
    "reporter": "john.doe",
    "labels": ["bug", "performance"]
  },
  "input": "Full issue description and comments...",
  "output": "Concise issue summary",
  "instruction": "Summarize the following software issue in one concise sentence:"
}
```

### Classification Task
```json
{
  "metadata": { ... },
  "input": "Issue title and description...",
  "output": {
    "priority": "Major",
    "status": "Open"
  },
  "instruction": "Classify this software issue by priority and current status:"
}
```

### Q&A Task
```json
{
  "metadata": { ... },
  "context": "Full issue information...",
  "question": "What is the main problem described in this issue?",
  "answer": "Issue summary",
  "instruction": "Answer the question based on the provided context:"
}
```

### Conversation Task
```json
{
  "metadata": { ... },
  "conversation": [
    {
      "role": "system",
      "content": "Issue: Title\nDescription: ..."
    },
    {
      "role": "user",
      "author": "developer1",
      "content": "Comment text...",
      "timestamp": "2023-01-15T11:00:00.000Z"
    }
  ],
  "instruction": "Continue this technical discussion about the software issue:"
}
```

## Command Line Interface

```bash
# Run full crawling pipeline
java Main crawl

# Show current status and statistics
java Main status

# Reset state for specific project
java Main reset KAFKA

# Show help
java Main help
```

## Error Handling & Recovery

### Automatic Recovery
- **Network failures**: Exponential backoff retry (up to 3 attempts)
- **Rate limiting**: Automatic delay when receiving HTTP 429
- **Server errors**: Retry logic for 5xx responses
- **Malformed data**: Graceful handling with logging

### Manual Recovery
- **State files**: Located in `crawl_state/` directory
- **Resume crawling**: Automatically resumes from last successful position
- **Reset state**: Use `java Main reset <PROJECT>` to start fresh

### Monitoring
- **Detailed logging**: Comprehensive logs for debugging
- **Progress tracking**: Real-time progress updates
- **Statistics**: Output file and processing statistics

## Performance Optimization

### Implemented Optimizations
- **Concurrent processing**: Multi-threaded architecture
- **Batch processing**: Processes multiple issues efficiently  
- **Connection reuse**: HTTP client connection pooling
- **Memory management**: Streaming processing for large datasets
- **Rate limiting**: Prevents API throttling

### Scalability Features
- **Configurable threading**: Adjust thread pool size
- **Batch size tuning**: Optimize API call efficiency
- **State checkpointing**: Enables distributed processing
- **Incremental updates**: Only processes new/changed issues

## Troubleshooting

### Common Issues

**Connection timeouts:**
```bash
# Increase timeout in CrawlerConfig.java
public static final int REQUEST_TIMEOUT_SECONDS = 60;
```

**Rate limiting:**
```bash
# Increase delay between requests
public static final int RATE_LIMIT_DELAY_MS = 2000;
```

**Memory issues:**
```bash
# Run with more memory
java -Xmx4g -jar jira-crawler-1.0.0-jar-with-dependencies.jar
```

### Logs and Debugging
- Enable debug logging in `CrawlerConfig.java`
- Check `crawl_state/` for state information
- Monitor `output/` directory for generated files

## Architecture Decisions

### Design Principles
- **Fault tolerance**: Graceful handling of all failure modes
- **Resumability**: State persistence enables recovery
- **Scalability**: Multi-threaded, configurable architecture
- **Data quality**: Comprehensive cleaning and validation
- **Extensibility**: Modular design for easy enhancement

### Technology Choices
- **Java 11**: Modern language features, excellent HTTP client
- **Jackson**: Robust JSON processing
- **Maven**: Standard build and dependency management
- **No external databases**: File-based state for simplicity

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.