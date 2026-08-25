//package com.research.AIagent.scraper;
//
//import com.research.AIagent.config.ProxyConfig;
//import com.research.AIagent.model.Platform;
//import com.research.AIagent.model.ScrapedPost;
//import com.research.AIagent.reposistory.ScrapedPostRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import tools.jackson.databind.JsonNode;
//import tools.jackson.databind.ObjectMapper;
//
//import java.io.IOException;
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.ArrayList;
//import java.util.List;
//
//@Component
//@Slf4j
//public class RedditScraper extends AbstractScarper implements PlatformScraper {
//    private final ScrapedPostRepository postRepository;
//    private final ObjectMapper objectMapper;
//    public RedditScraper(final ProxyConfig proxyConfig,
//        final ScrapedPostRepository postRepository,
//                final ObjectMapper objectMapper){
//
//        super(proxyConfig);
//        this.objectMapper=objectMapper;
//        this.postRepository=postRepository;
//    }
//
//    @Value("${scraping.reddit.subreddits}")
//    private List<String> subreddits;
//
//    @Value("${scraping.reddit.posts-per-subreddit}")
//    private int postsPerSubreddit;
//    @Override
//    public Platform getPlatform() {
//        return Platform.REDDIT;
//    }
//
//    public List<ScrapedPost> scrape(){
//        final List<ScrapedPost> posts = new ArrayList<>();
//        log.info("Reddit scraper started");
//        log.info("Reddit scraper using subreddits: {}", this.subreddits);
////        final String proxyIp = detectProxyIp();
//
//        for(final String subreddit: this.subreddits){
//            try{
//                final String url =
//                        "https://www.reddit.com/r/"
//                                + subreddit
//                                + "/hot.rss";
//                final String json = fetch(url);
//                final String proxyIp =  detectProxyIp();
//                final JsonNode root = this.objectMapper.readTree(json);
//                final JsonNode children = root.path("data")
//                        .path("children");
//                for(final  JsonNode  child: children){
//                    final JsonNode data = child.path("data");
//
//                    final String extrenalId = data.path("id")
//                            .asText("");
//                    if(extrenalId.isBlank()){
//                        continue;
//                    }
//                    if(this.postRepository.existsByPlatformAndExternalId(getPlatform(), extrenalId)){
//                        continue;
//                    }
//                    final String title = data.path("title")
//                            .asText("");
//                    if(title.isBlank()){
//                        continue;
//                    }
//                    final String selftext =  data.path("selftext")
//                            .asText("")
//                            .trim();
//                    final String content = selftext.isBlank()? title.substring(0, Math.min(title.length(), 500)): selftext;
//
//                    final long postedAtEpoch = (long)data.path("created_utc").asDouble();
//                    final LocalDateTime postedAt = data.has("created_utc")
//                            ? LocalDateTime.ofInstant(Instant.ofEpochSecond(postedAtEpoch),
//                            ZoneId.systemDefault()): null;
//
//                    final String redditurl = data.path("url").asText(null);
//                    final String author=  data.path("author").asText(null);
//                    final int score = data.path("score").asInt(0);
//                    final int commentCount =  data.path("num_comments").asInt(0);
//                    final String subredditName = data.path("subreddit").asText(subreddit);
//                    final ScrapedPost post = ScrapedPost.builder()
//                            .platform(getPlatform())
//                            .externalId(extrenalId)
//                            .title(title)
//                            .content(content)
//                            .proxyIpUsed(proxyIp)
//                            .url(redditurl)
//                            .author(author)
//                            .score(score)
//                            .commentCount(commentCount)
//                            .subReddit(subredditName)
//                            .postedAt(postedAt)
//                            .build();
//                    posts.add(post);
//                }
//                log.info("Reddit r/{} scraped: {} new posts", subreddit, posts.size());
//                Thread.sleep(500);
//            }catch (final InterruptedException e){
//                Thread.currentThread().interrupt();
//            }
//            catch ( final IOException e) {
//                log.error("Failed to scrape Reddit r/{}", subreddit, e.getMessage());
//            }
//        }
//        return posts;
//    }
//}
package com.research.AIagent.scraper;

import com.research.AIagent.config.ProxyConfig;
import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RedditScraper extends AbstractScarper implements PlatformScraper {

    private final ScrapedPostRepository postRepository;

    public RedditScraper(
            final ProxyConfig proxyConfig,
            final ScrapedPostRepository postRepository) {

        super(proxyConfig);
        this.postRepository = postRepository;
    }

    @Value("${scraping.reddit.subreddits}")
    private List<String> subreddits;

    @Value("${scraping.reddit.posts-per-subreddit}")
    private int postsPerSubreddit;

    @Override
    public Platform getPlatform() {
        return Platform.REDDIT;
    }

    @Override
    public List<ScrapedPost> scrape() {

        final List<ScrapedPost> posts = new ArrayList<>();

        log.info("Reddit scraper started");
        log.info(
                "Reddit scraper using subreddits: {}",
                this.subreddits
        );

        // Detect proxy only once
        final String proxyIp = detectProxyIp();

        for (final String subreddit : this.subreddits) {

            try {

                final String url =
                        "https://old.reddit.com/r/"
                                + subreddit
                                + "/hot.rss";

                log.info(
                        "Fetching Reddit RSS for r/{}",
                        subreddit
                );

                final String xml = fetch(
                        url,
                        "text/xml, application/rss+xml, application/xml"
                );

                final Document document = parseXml(xml);

                final NodeList items =
                        document.getElementsByTagName("item");

                int scrapedForSubreddit = 0;

                for (int i = 0;
                     i < items.getLength();
                     i++) {

                    if (scrapedForSubreddit >= postsPerSubreddit) {
                        break;
                    }

                    final Element item =
                            (Element) items.item(i);

                    // -----------------------------
                    // Title
                    // -----------------------------

                    final String title =
                            getElementText(item, "title");

                    if (title == null || title.isBlank()) {
                        continue;
                    }

                    // -----------------------------
                    // Link
                    // -----------------------------

                    final String redditUrl =
                            getElementText(item, "link");

                    // -----------------------------
                    // GUID / External ID
                    // -----------------------------

                    String externalId =
                            getElementText(item, "guid");

                    if (externalId == null ||
                            externalId.isBlank()) {

                        externalId = redditUrl;
                    }

                    if (externalId == null ||
                            externalId.isBlank()) {

                        continue;
                    }

                    /*
                     * Reddit RSS guid can sometimes contain
                     * a complete Reddit URL.
                     *
                     * We only need a stable unique value.
                     */
                    if (externalId.contains("/comments/")) {

                        final String[] parts =
                                externalId.split("/");

                        if (parts.length > 0) {
                            externalId =
                                    parts[parts.length - 1];
                        }
                    }

                    // -----------------------------
                    // Duplicate check
                    // -----------------------------

                    if (postRepository
                            .existsByPlatformAndExternalId(
                                    getPlatform(),
                                    externalId)) {

                        continue;
                    }

                    // -----------------------------
                    // Description / Content
                    // -----------------------------

                    String content =
                            getElementText(
                                    item,
                                    "description"
                            );

                    if (content == null ||
                            content.isBlank()) {

                        content = title;
                    }

                    /*
                     * RSS description may contain HTML.
                     * Remove basic HTML tags.
                     */
                    content = removeHtml(content).trim();

                    if (content.length() > 5000) {
                        content =
                                content.substring(0, 5000);
                    }

                    // -----------------------------
                    // Author
                    // -----------------------------

                    String author =
                            getElementText(
                                    item,
                                    "dc:creator"
                            );

                    if (author == null ||
                            author.isBlank()) {

                        author =
                                getElementText(
                                        item,
                                        "author"
                                );
                    }

                    // -----------------------------
                    // Published date
                    // -----------------------------

                    final String publishedDate =
                            getElementText(
                                    item,
                                    "pubDate"
                            );

                    final LocalDateTime postedAt =
                            parseDate(publishedDate);

                    // -----------------------------
                    // Reddit RSS doesn't reliably
                    // provide score/comments.
                    // -----------------------------

                    final int score = 0;
                    final int commentCount = 0;

                    // -----------------------------
                    // Build ScrapedPost
                    // -----------------------------

                    final ScrapedPost post =
                            ScrapedPost.builder()
                                    .platform(getPlatform())
                                    .externalId(externalId)
                                    .title(title)
                                    .content(content)
                                    .proxyIpUsed(proxyIp)
                                    .url(redditUrl)
                                    .author(author)
                                    .score(score)
                                    .commentCount(commentCount)
                                    .subReddit(subreddit)
                                    .postedAt(postedAt)
                                    .build();

                    posts.add(post);

                    scrapedForSubreddit++;

                    log.debug(
                            "Reddit post scraped: {}",
                            title
                    );
                }

                log.info(
                        "Reddit r/{} scraped: {} new posts",
                        subreddit,
                        scrapedForSubreddit
                );

                Thread.sleep(10_000);

            } catch (final InterruptedException e) {

                Thread.currentThread().interrupt();
                break;

            } catch (final IOException e) {

                log.error(
                        "Failed to scrape Reddit r/{}",
                        subreddit,
                        e
                );
            } catch (final Exception e) {

                log.error(
                        "Unexpected error scraping Reddit r/{}",
                        subreddit,
                        e
                );
            }
        }

        log.info(
                "Reddit scraper finished. Total new posts: {}",
                posts.size()
        );

        return posts;
    }

    // ============================================================
    // XML PARSER
    // ============================================================

    private Document parseXml(
            final String xml
    ) throws Exception {

        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        /*
         * Disable external entity processing.
         * This makes the XML parser safer.
         * Note: We do NOT disallow-doctype-decl because
         * Reddit RSS may include a DOCTYPE declaration.
         */

        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );

        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        final DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(
                new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    // ============================================================
    // GET XML ELEMENT TEXT
    // ============================================================

    private String getElementText(
            final Element parent,
            final String tagName
    ) {

        NodeList nodes =
                parent.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return null;
        }

        return nodes.item(0)
                .getTextContent()
                .trim();
    }

    // ============================================================
    // REMOVE HTML FROM RSS DESCRIPTION
    // ============================================================

    private String removeHtml(
            final String text
    ) {

        return text
                .replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ============================================================
    // PARSE REDDIT PUB DATE
    // ============================================================

    private LocalDateTime parseDate(
            final String date
    ) {

        if (date == null || date.isBlank()) {
            return null;
        }

        try {

            final ZonedDateTime zonedDateTime =
                    ZonedDateTime.parse(
                            date,
                            DateTimeFormatter.RFC_1123_DATE_TIME
                    );

            return zonedDateTime
                    .withZoneSameInstant(
                            ZoneId.systemDefault()
                    )
                    .toLocalDateTime();

        } catch (final Exception e) {

            log.debug(
                    "Could not parse Reddit date: {}",
                    date
            );

            return null;
        }
    }
}