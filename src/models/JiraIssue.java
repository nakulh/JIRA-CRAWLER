package models;

import java.util.List;
import java.util.Objects;

/**
 * Data model representing a Jira issue with all relevant fields
 */
public class JiraIssue {
    private final String key;
    private final String summary;
    private final String description;
    private final String status;
    private final String priority;
    private final String assignee;
    private final String reporter;
    private final String created;
    private final String updated;
    private final List<String> labels;
    private final List<JiraComment> comments;
    
    private JiraIssue(Builder builder) {
        this.key = builder.key;
        this.summary = builder.summary;
        this.description = builder.description;
        this.status = builder.status;
        this.priority = builder.priority;
        this.assignee = builder.assignee;
        this.reporter = builder.reporter;
        this.created = builder.created;
        this.updated = builder.updated;
        this.labels = builder.labels;
        this.comments = builder.comments;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return new Builder()
            .key(key)
            .summary(summary)
            .description(description)
            .status(status)
            .priority(priority)
            .assignee(assignee)
            .reporter(reporter)
            .created(created)
            .updated(updated)
            .labels(labels)
            .comments(comments);
    }
    
    // Getters
    public String getKey() { return key; }
    public String getSummary() { return summary; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public String getAssignee() { return assignee; }
    public String getReporter() { return reporter; }
    public String getCreated() { return created; }
    public String getUpdated() { return updated; }
    public List<String> getLabels() { return labels; }
    public List<JiraComment> getComments() { return comments; }
    
    public static class Builder {
        private String key;
        private String summary;
        private String description;
        private String status;
        private String priority;
        private String assignee;
        private String reporter;
        private String created;
        private String updated;
        private List<String> labels;
        private List<JiraComment> comments;
        
        public Builder key(String key) { this.key = key; return this; }
        public Builder summary(String summary) { this.summary = summary; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder priority(String priority) { this.priority = priority; return this; }
        public Builder assignee(String assignee) { this.assignee = assignee; return this; }
        public Builder reporter(String reporter) { this.reporter = reporter; return this; }
        public Builder created(String created) { this.created = created; return this; }
        public Builder updated(String updated) { this.updated = updated; return this; }
        public Builder labels(List<String> labels) { this.labels = labels; return this; }
        public Builder comments(List<JiraComment> comments) { this.comments = comments; return this; }
        
        public JiraIssue build() {
            return new JiraIssue(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JiraIssue jiraIssue = (JiraIssue) o;
        return Objects.equals(key, jiraIssue.key);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
    
    @Override
    public String toString() {
        return "JiraIssue{" +
                "key='" + key + '\'' +
                ", summary='" + summary + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}