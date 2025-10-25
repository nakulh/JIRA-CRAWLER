import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Handles web scraping of Jira issues from Apache's public Jira instance
 * Implements retry logic, rate limiting, and HTML parsing
 */
public class JiraWebScraper {
    private static final Logger logger = Logger.getLogger(JiraWebScraper.class.getName());
    private static final String JIRA_BASE_URL = CrawlerConfig.JIRA_BASE_URL;
    private static final int MAX_RETRIES = CrawlerConfig.MAX_RETRIES;
    private static final int CRAWL_DELAY_MS = CrawlerConfig.CRAWL_DELAY_MS;
    private final HttpClient httpClient;
    private long lastRequestTime = 0;
    
    // Patterns for extracting data from HTML
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public JiraWebScraper() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CrawlerConfig.REQUEST_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Gets issue keys from a search page URL
     */
    public List<String> getIssueKeysFromSearchPage(String searchUrl) throws IOException, InterruptedException {
        Document doc = fetchDocument(searchUrl);
        if (doc == null) {
            return new ArrayList<>();
        }
        return extractIssueKeysFromSearchPage(doc);
    }

    /**
     * Scrapes detailed information for a specific issue including comments
     */
    public JiraIssue scrapeIssueDetails(String issueKey) throws IOException, InterruptedException {
        String issueUrl = String.format("%s/browse/%s", JIRA_BASE_URL, issueKey);
        Document doc = fetchDocument(issueUrl);
        
        if (doc == null) {
            return null;
        }
        
        return parseIssueFromHtml(doc, issueKey);
    }

    /**
     * Fetches HTML document with retry logic and rate limiting
     */
    private Document fetchDocument(String url) throws IOException, InterruptedException {
        enforceRateLimit();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return Jsoup.parse(response.body(), url);
                } else if (response.statusCode() == 429) {
                    // Rate limited - wait longer
                    logger.warning("Rate limited, waiting before retry...");
                    Thread.sleep(5000 * attempt);
                    continue;
                } else if (response.statusCode() >= 500) {
                    // Server error - retry
                    logger.warning(String.format("Server error %d, attempt %d/%d", 
                        response.statusCode(), attempt, MAX_RETRIES));
                    Thread.sleep(2000 * attempt);
                    continue;
                } else {
                    logger.severe(String.format("HTTP error %d for URL: %s", 
                        response.statusCode(), url));
                    return null;
                }
                
            } catch (Exception e) {
                logger.log(Level.WARNING, String.format("Request failed, attempt %d/%d", 
                    attempt, MAX_RETRIES), e);
                
                if (attempt == MAX_RETRIES) {
                    throw e;
                }
                
                Thread.sleep(1000 * attempt);
            }
        }
        
        return null;
    }

    /**
     * Extracts issue keys from search results page
     */
    private List<String> extractIssueKeysFromSearchPage(Document doc) {
        final List<String> issueKeys = new ArrayList<>();
        
        // Look for issue links in search results
        Elements issueLinks = doc.select("a[href*='/browse/']");
        
        for (Element link : issueLinks) {
            final String href = link.attr("href");
            final Matcher matcher = ISSUE_KEY_PATTERN.matcher(href);
            if (matcher.find()) {
                String issueKey = matcher.group(1);
                if (!issueKeys.contains(issueKey)) {
                    issueKeys.add(issueKey);
                }
            }
        }
        
        // Alternative: look for issue keys in text content
        if (issueKeys.isEmpty()) {
            Elements issueElements = doc.select(".issue-link, .issuekey, [data-issue-key]");
            for (Element element : issueElements) {
                final String text = element.text();
                final String dataKey = element.attr("data-issue-key");
                
                if (!dataKey.isEmpty()) {
                    issueKeys.add(dataKey);
                } else {
                    final Matcher matcher = ISSUE_KEY_PATTERN.matcher(text);
                    if (matcher.find()) {
                        String issueKey = matcher.group(1);
                        if (!issueKeys.contains(issueKey)) {
                            issueKeys.add(issueKey);
                        }
                    }
                }
            }
        }
        
        return issueKeys;
    }
    
    /**
     * Parses issue details from HTML document
     */
    private JiraIssue parseIssueFromHtml(Document doc, String issueKey) {
        try {
            // Extract basic information
            String summary = extractText(doc, "#summary-val, .summary, h1");
            String description = extractText(doc, "#description-val, .description, .user-content-block");
            String status = extractText(doc, "#status-val, .status, [data-field-id='status']");
            String priority = extractText(doc, "#priority-val, .priority, [data-field-id='priority']");
            String assignee = extractText(doc, "#assignee-val, .assignee, [data-field-id='assignee']");
            String reporter = extractText(doc, "#reporter-val, .reporter, [data-field-id='reporter']");
            
            // Extract dates
            String created = extractText(doc, "#created-val, .created, [data-field-id='created']");
            String updated = extractText(doc, "#updated-val, .updated, [data-field-id='updated']");
            
            // Extract labels
            List<String> labels = extractLabels(doc);
            
            // Extract comments
            List<JiraComment> comments = extractComments(doc);
            
            return JiraIssue.builder()
                .key(issueKey)
                .summary(cleanText(summary))
                .description(cleanText(description))
                .status(cleanText(status))
                .priority(cleanText(priority))
                .assignee(cleanText(assignee))
                .reporter(cleanText(reporter))
                .created(cleanText(created))
                .updated(cleanText(updated))
                .labels(labels)
                .comments(comments)
                .build();
                
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing issue " + issueKey, e);
            return null;
        }
    }
    
    /**
     * Extracts text from HTML using multiple selectors
     */
    private String extractText(Document doc, String selectors) {
        String[] selectorArray = selectors.split(",");
        
        for (String selector : selectorArray) {
            Elements elements = doc.select(selector.trim());
            if (!elements.isEmpty()) {
                return elements.first().text();
            }
        }
        
        return "";
    }
    
    /**
     * Extracts labels from the issue page
     */
    private List<String> extractLabels(Document doc) {
        List<String> labels = new ArrayList<>();
        
        // Try different selectors for labels
        Elements labelElements = doc.select(".labels .lozenge, .label, [data-field-id='labels'] .lozenge");
        
        for (Element label : labelElements) {
            String labelText = label.text().trim();
            if (!labelText.isEmpty() && !labels.contains(labelText)) {
                labels.add(labelText);
            }
        }
        
        return labels;
    }
    
    /**
     * Extracts comments from the issue page
     */
    private List<JiraComment> extractComments(Document doc) {
        List<JiraComment> comments = new ArrayList<>();
        
        // Look for comment sections
        Elements commentElements = doc.select(".activity-comment, .comment, .issue-data-block");
        
        for (Element commentElement : commentElements) {
            try {
                String author = extractTextFromElement(commentElement, ".author, .user-hover, .comment-author");
                String body = extractTextFromElement(commentElement, ".comment-body, .user-content-block, .activity-comment-content");
                String created = extractTextFromElement(commentElement, ".comment-date, .date, .activity-date");
                
                if (!body.isEmpty()) {
                    comments.add(JiraComment.builder()
                        .author(cleanText(author))
                        .body(cleanText(body))
                        .created(cleanText(created))
                        .build());
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Error parsing comment", e);
            }
        }
        
        return comments;
    }
    
    /**
     * Extracts text from a specific element using multiple selectors
     */
    private String extractTextFromElement(Element element, String selectors) {
        String[] selectorArray = selectors.split(",");
        
        for (String selector : selectorArray) {
            Elements elements = element.select(selector.trim());
            if (!elements.isEmpty()) {
                return elements.first().text();
            }
        }
        
        return "";
    }
    
    /**
     * Cleans extracted text
     */
    private String cleanText(String text) {
        if (text == null) return "";
        
        return text.trim()
                  .replaceAll("\\s+", " ")
                  .replaceAll("[\r\n]+", " ");
    }
    
    private void enforceRateLimit() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        if (timeSinceLastRequest < CRAWL_DELAY_MS) {
            Thread.sleep(CRAWL_DELAY_MS - timeSinceLastRequest);
        }
        
        lastRequestTime = System.currentTimeMillis();
    }
}