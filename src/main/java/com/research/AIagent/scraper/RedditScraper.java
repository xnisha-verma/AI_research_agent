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

        final List<ScrapedPost> allPosts = new ArrayList<>();

        log.info("Reddit scraper started");
        log.info(
                "Reddit scraper using subreddits: {}",
                this.subreddits
        );

        // Detect proxy only once
        final String proxyIp = detectProxyIp();

        for (final String subreddit : this.subreddits) {

            try {

                /*
                 * Use /.rss (Atom feed) — this is the only
                 * unauthenticated Reddit endpoint that works
                 * reliably. The .json API returns 403.
                 */
                final String url =
                        "https://www.reddit.com/r/"
                                + subreddit
                                + "/.rss";

                log.info(
                        "Fetching Reddit Atom feed for r/{}",
                        subreddit
                );

                final String xml = fetch(
                        url,
                        "application/atom+xml, text/xml"
                );

                final Document document = parseXml(xml);

                /*
                 * Reddit RSS returns Atom format:
                 * <feed> → <entry> (not <item>)
                 */
                final NodeList entries =
                        document.getElementsByTagName("entry");

                int scrapedForSubreddit = 0;

                for (int i = 0;
                     i < entries.getLength();
                     i++) {

                    if (scrapedForSubreddit >= postsPerSubreddit) {
                        break;
                    }

                    final Element entry =
                            (Element) entries.item(i);

                    // -----------------------------
                    // Title
                    // -----------------------------

                    final String title =
                            getElementText(entry, "title");

                    if (title == null || title.isBlank()) {
                        continue;
                    }

                    // -----------------------------
                    // Link (from <link href="..."/>)
                    // -----------------------------

                    final String redditUrl =
                            getLinkHref(entry);

                    // -----------------------------
                    // ID (e.g. "t3_1vxxb3n")
                    // -----------------------------

                    String externalId =
                            getElementText(entry, "id");

                    if (externalId == null ||
                            externalId.isBlank()) {

                        externalId = redditUrl;
                    }

                    if (externalId == null ||
                            externalId.isBlank()) {

                        continue;
                    }

                    // Strip "t3_" prefix if present
                    if (externalId.startsWith("t3_")) {
                        externalId =
                                externalId.substring(3);
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
                    // Content
                    // -----------------------------

                    String content =
                            getElementText(
                                    entry,
                                    "content"
                            );

                    if (content == null ||
                            content.isBlank()) {

                        content = title;
                    }

                    // Strip HTML from content
                    content = removeHtml(content).trim();

                    if (content.length() > 5000) {
                        content =
                                content.substring(0, 5000);
                    }

                    // -----------------------------
                    // Author (Atom: <author><name>)
                    // -----------------------------

                    String author = getNestedText(
                            entry, "author", "name"
                    );

                    // Strip "/u/" prefix if present
                    if (author != null &&
                            author.startsWith("/u/")) {

                        author = author.substring(3);
                    }

                    // -----------------------------
                    // Published date (Atom: <updated>)
                    // -----------------------------

                    final String updatedDate =
                            getElementText(
                                    entry,
                                    "updated"
                            );

                    final LocalDateTime postedAt =
                            parseDate(updatedDate);

                    // -----------------------------
                    // Atom feed doesn't provide
                    // score / comment counts
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

                    allPosts.add(post);
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

                // Delay between subreddits to respect
                // Reddit rate limits
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
                allPosts.size()
        );

        return allPosts;
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
         * Disable external entity processing for safety.
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

        // Enable namespace awareness for Atom parsing
        factory.setNamespaceAware(true);

        final DocumentBuilder builder =
                factory.newDocumentBuilder();

        return builder.parse(
                new ByteArrayInputStream(
                        xml.getBytes(StandardCharsets.UTF_8)
                )
        );
    }

    // ============================================================
    // GET ELEMENT TEXT BY TAG NAME
    // ============================================================

    private String getElementText(
            final Element parent,
            final String tagName
    ) {

        // Try namespace-unaware first
        NodeList nodes =
                parent.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            // Try with wildcard namespace
            nodes = parent.getElementsByTagNameNS(
                    "*", tagName
            );
        }

        if (nodes.getLength() == 0) {
            return null;
        }

        return nodes.item(0)
                .getTextContent()
                .trim();
    }

    // ============================================================
    // GET <link href="..."/> FROM ATOM ENTRY
    // ============================================================

    private String getLinkHref(final Element entry) {

        final NodeList links =
                entry.getElementsByTagName("link");

        for (int i = 0; i < links.getLength(); i++) {

            final Element link =
                    (Element) links.item(i);

            final String rel =
                    link.getAttribute("rel");

            // Prefer "alternate" link
            if ("alternate".equals(rel) ||
                    rel == null || rel.isEmpty()) {

                final String href =
                        link.getAttribute("href");

                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }

        // Fallback: any link with href
        for (int i = 0; i < links.getLength(); i++) {

            final Element link =
                    (Element) links.item(i);

            final String href =
                    link.getAttribute("href");

            if (href != null && !href.isBlank()) {
                return href;
            }
        }

        return null;
    }

    // ============================================================
    // GET NESTED TEXT (e.g. <author><name>...</name></author>)
    // ============================================================

    private String getNestedText(
            final Element parent,
            final String outerTag,
            final String innerTag
    ) {

        final NodeList outerNodes =
                parent.getElementsByTagName(outerTag);

        if (outerNodes.getLength() == 0) {
            return null;
        }

        final Element outer =
                (Element) outerNodes.item(0);

        return getElementText(outer, innerTag);
    }

    // ============================================================
    // REMOVE HTML FROM CONTENT
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
    // PARSE ATOM DATE (ISO 8601)
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
                            DateTimeFormatter.ISO_DATE_TIME
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