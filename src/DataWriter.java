import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handles writing transformed data to JSONL files with proper organization
 */
public class DataWriter {
    private static final Logger logger = Logger.getLogger(DataWriter.class.getName());
    private static final String OUTPUT_DIR = "output";
    private static final String JSONL_EXTENSION = ".jsonl";
    
    private final ConcurrentHashMap<String, BufferedWriter> writers;
    private final DateTimeFormatter timestampFormatter;
    
    public DataWriter() {
        this.writers = new ConcurrentHashMap<>();
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        createOutputDirectory();
    }
    
    /**
     * Writes a JSONL record to the appropriate project file
     */
    public synchronized void writeRecord(String projectKey, String jsonlRecord) {
        try {
            BufferedWriter writer = getOrCreateWriter(projectKey);
            writer.write(jsonlRecord);
            writer.flush(); // Ensure data is written immediately
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing record for project " + projectKey, e);
        }
    }
    
    /**
     * Gets or creates a writer for the specified project
     */
    private BufferedWriter getOrCreateWriter(String projectKey) throws IOException {
        return writers.computeIfAbsent(projectKey, key -> {
            try {
                Path outputFile = getOutputFilePath(key);
                return Files.newBufferedWriter(outputFile, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error creating writer for project " + key, e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Generates the output file path for a project
     */
    private Path getOutputFilePath(String projectKey) {
        String timestamp = LocalDateTime.now().format(timestampFormatter);
        String filename = String.format("%s_%s%s", projectKey.toLowerCase(), timestamp, JSONL_EXTENSION);
        return Paths.get(OUTPUT_DIR, filename);
    }
    
    /**
     * Creates the output directory if it doesn't exist
     */
    private void createOutputDirectory() {
        try {
            Path outputDir = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
                logger.info("Created output directory: " + outputDir.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error creating output directory", e);
        }
    }
    
    /**
     * Closes all writers and releases resources
     */
    public void close() {
        writers.values().forEach(writer -> {
            try {
                writer.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing writer", e);
            }
        });
        writers.clear();
        logger.info("Closed all data writers");
    }
    
    /**
     * Forces flush of all writers
     */
    public void flush() {
        writers.values().forEach(writer -> {
            try {
                writer.flush();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error flushing writer", e);
            }
        });
    }
    
    /**
     * Gets statistics about written data
     */
    public void printStatistics() {
        try {
            Path outputDir = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputDir)) {
                System.out.println("No output files found.");
                return;
            }
            
            System.out.println("Output File Statistics:");
            System.out.println("======================");
            
            Files.list(outputDir)
                .filter(path -> path.toString().endsWith(JSONL_EXTENSION))
                .forEach(path -> {
                    try {
                        long lineCount = Files.lines(path).count();
                        long fileSize = Files.size(path);
                        System.out.printf("File: %s | Lines: %d | Size: %d bytes%n",
                            path.getFileName(), lineCount, fileSize);
                    } catch (IOException e) {
                        logger.log(Level.WARNING, "Error reading file stats for " + path, e);
                    }
                });
                
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading output statistics", e);
        }
    }
}