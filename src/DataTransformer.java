import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

/**
 * Transforms raw Jira data into structured JSONL format suitable for LLM training
 */
public class DataTransformer {
    private static final Logger logger = Logger.getLogger(DataTransformer.class.getName());
    private final ObjectMapper objectMapper;
    
    // Patterns for cleaning text content
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern JIRA_MARKUP_PATTERN = Pattern.compile("\\{[^}]+\\}");
    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("\\s+");
    
    public DataTransformer() {
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Transforms a JiraIssue into JSONL format with multiple training tasks
     */
    public String transformToJsonl(JiraIssue issue) {
        try {
            List<ObjectNode> trainingRecords = new ArrayList<>();
            
            // Generate different types of training data
            trainingRecords.add(createSummarizationTask(issue));
            trainingRecords.add(createClassificationTask(issue));
            trainingRecords.add(createQnATask(issue));
            
            // If there are comments, create conversation tasks
            if (issue.getComments() != null && !issue.getComments().isEmpty()) {
                trainingRecords.add(createConversationTask(issue));
            }
            
            // Convert each record to JSONL format
            StringBuilder jsonlOutput = new StringBuilder();
            for (ObjectNode record : trainingRecords) {
                jsonlOutput.append(objectMapper.writeValueAsString(record)).append("\n");
            }
            
            return jsonlOutput.toString();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error transforming issue " + issue.getKey(), e);
            return "";
        }
    }
    
    /**
     * Creates a summarization training task
     */
    private ObjectNode createSummarizationTask(JiraIssue issue) {
        ObjectNode record = objectMapper.createObjectNode();
        
        // Metadata
        addMetadata(record, issue, "summarization");
        
        // Input: Full issue description and comments
        String fullText = buildFullIssueText(issue);
        record.put("input", cleanText(fullText));
        
        // Output: Summary (using the issue summary as ground truth)
        record.put("output", cleanText(issue.getSummary()));
        
        // Instruction for the task
        record.put("instruction", "Summarize the following software issue in one concise sentence:");
        
        return record;
    }
    
    /**
     * Creates a classification training task
     */
    private ObjectNode createClassificationTask(JiraIssue issue) {
        ObjectNode record = objectMapper.createObjectNode();
        
        addMetadata(record, issue, "classification");
        
        // Input: Issue title and description
        String inputText = issue.getSummary() + "\n\n" + 
                          (issue.getDescription() != null ? issue.getDescription() : "");
        record.put("input", cleanText(inputText));
        
        // Output: Priority and status
        ObjectNode classification = objectMapper.createObjectNode();
        classification.put("priority", issue.getPriority());
        classification.put("status", issue.getStatus());
        record.set("output", classification);
        
        record.put("instruction", "Classify this software issue by priority and current status:");
        
        return record;
    }
    
    /**
     * Creates a Q&A training task
     */
    private ObjectNode createQnATask(JiraIssue issue) {
        ObjectNode record = objectMapper.createObjectNode();
        
        addMetadata(record, issue, "question_answering");
        
        // Context: Full issue information
        record.put("context", cleanText(buildFullIssueText(issue)));
        
        // Generate questions and answers based on issue content
        record.put("question", "What is the main problem described in this issue?");
        record.put("answer", cleanText(issue.getSummary()));
        
        record.put("instruction", "Answer the question based on the provided context:");
        
        return record;
    }
    
    /**
     * Creates a conversation/dialogue training task from comments
     */
    private ObjectNode createConversationTask(JiraIssue issue) {
        ObjectNode record = objectMapper.createObjectNode();
        
        addMetadata(record, issue, "conversation");
        
        // Build conversation from comments
        ArrayNode conversation = objectMapper.createArrayNode();
        
        // Start with the issue description as system message
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", "Issue: " + cleanText(issue.getSummary()) + 
                                "\nDescription: " + cleanText(issue.getDescription()));
        conversation.add(systemMsg);
        
        // Add comments as conversation turns
        for (JiraComment comment : issue.getComments()) {
            ObjectNode commentMsg = objectMapper.createObjectNode();
            commentMsg.put("role", "user");
            commentMsg.put("author", comment.getAuthor());
            commentMsg.put("content", cleanText(comment.getBody()));
            commentMsg.put("timestamp", comment.getCreated());
            conversation.add(commentMsg);
        }
        
        record.set("conversation", conversation);
        record.put("instruction", "Continue this technical discussion about the software issue:");
        
        return record;
    }
    
    /**
     * Adds common metadata to all training records
     */
    private void addMetadata(ObjectNode record, JiraIssue issue, String taskType) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("issue_key", issue.getKey());
        metadata.put("project", extractProject(issue.getKey()));
        metadata.put("task_type", taskType);
        metadata.put("created", issue.getCreated());
        metadata.put("updated", issue.getUpdated());
        metadata.put("reporter", issue.getReporter());
        metadata.put("assignee", issue.getAssignee());
        
        // Add labels as array
        ArrayNode labelsArray = objectMapper.createArrayNode();
        if (issue.getLabels() != null) {
            for (String label : issue.getLabels()) {
                labelsArray.add(label);
            }
        }
        metadata.set("labels", labelsArray);
        
        record.set("metadata", metadata);
    }
    
    /**
     * Builds full text content from issue and comments
     */
    private String buildFullIssueText(JiraIssue issue) {
        StringBuilder fullText = new StringBuilder();
        
        fullText.append("Title: ").append(issue.getSummary()).append("\n\n");
        
        if (issue.getDescription() != null && !issue.getDescription().trim().isEmpty()) {
            fullText.append("Description: ").append(issue.getDescription()).append("\n\n");
        }
        
        if (issue.getComments() != null && !issue.getComments().isEmpty()) {
            fullText.append("Comments:\n");
            for (JiraComment comment : issue.getComments()) {
                fullText.append("- ").append(comment.getAuthor()).append(": ")
                        .append(comment.getBody()).append("\n");
            }
        }
        
        return fullText.toString();
    }
    
    /**
     * Cleans and normalizes text content
     */
    private String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // Remove HTML tags
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        
        // Remove Jira markup
        text = JIRA_MARKUP_PATTERN.matcher(text).replaceAll("");
        
        // Normalize whitespace
        text = MULTIPLE_WHITESPACE.matcher(text).replaceAll(" ");
        
        // Trim and return
        return text.trim();
    }
    
    /**
     * Extracts project key from issue key (e.g., "KAFKA-123" -> "KAFKA")
     */
    private String extractProject(String issueKey) {
        if (issueKey == null) return "";
        int dashIndex = issueKey.indexOf('-');
        return dashIndex > 0 ? issueKey.substring(0, dashIndex) : issueKey;
    }
}