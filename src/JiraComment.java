import java.util.Objects;

/**
 * Data model representing a comment on a Jira issue
 */
public class JiraComment {
    private final String author;
    private final String body;
    private final String created;
    
    private JiraComment(Builder builder) {
        this.author = builder.author;
        this.body = builder.body;
        this.created = builder.created;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public String getCreated() { return created; }
    
    public static class Builder {
        private String author;
        private String body;
        private String created;
        
        public Builder author(String author) { this.author = author; return this; }
        public Builder body(String body) { this.body = body; return this; }
        public Builder created(String created) { this.created = created; return this; }
        
        public JiraComment build() {
            return new JiraComment(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JiraComment that = (JiraComment) o;
        return Objects.equals(author, that.author) &&
               Objects.equals(body, that.body) &&
               Objects.equals(created, that.created);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(author, body, created);
    }
    
    @Override
    public String toString() {
        return "JiraComment{" +
                "author='" + author + '\'' +
                ", created='" + created + '\'' +
                ", bodyLength=" + (body != null ? body.length() : 0) +
                '}';
    }
}