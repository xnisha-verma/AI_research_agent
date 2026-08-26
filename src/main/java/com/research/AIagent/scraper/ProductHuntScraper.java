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
public class ProductHuntScraper extends AbstractScarper implements PlatformScraper {

    private final ScrapedPostRepository postRepository;

    @Value("${scraping.producthunt.posts-count}")
    private int postsCount;

    public ProductHuntScraper(
            final ProxyConfig proxyConfig,
            final ScrapedPostRepository postRepository) {

        super(proxyConfig);
        this.postRepository = postRepository;
    }

    @Override
    public Platform getPlatform() {
        return Platform.PRODUCTHUNT;
    }

    @Override
    public List<ScrapedPost> scrape() {
        final List<ScrapedPost> allPosts = new ArrayList<>();

        log.info("Product Hunt scraper started");
        log.info("Product Hunt count configured: {}", this.postsCount);

        // Detect proxy only once
        final String proxyIp = detectProxyIp();

        try {
            final String url = "https://www.producthunt.com/feed";

            log.info("Fetching Product Hunt Atom feed");
            final String xml = fetch(url, "application/atom+xml, text/xml");

            final Document document = parseXml(xml);
            final NodeList entries = document.getElementsByTagName("entry");

            int scraped = 0;

            for (int i = 0; i < entries.getLength(); i++) {
                if (scraped >= this.postsCount) {
                    break;
                }

                final Element entry = (Element) entries.item(i);

                // Title
                final String title = getElementText(entry, "title");
                if (title == null || title.isBlank()) {
                    continue;
                }

                // Link
                final String phUrl = getLinkHref(entry);

                // ID
                String externalId = getElementText(entry, "id");
                if (externalId == null || externalId.isBlank()) {
                    externalId = phUrl;
                }

                if (externalId == null || externalId.isBlank()) {
                    continue;
                }

                // Clean the ID (e.g. tag:www.producthunt.com,2005:Post/1209635 -> 1209635)
                if (externalId.contains("Post/")) {
                    externalId = externalId.substring(externalId.indexOf("Post/") + 5);
                }

                // Duplicate check
                if (this.postRepository.existsByPlatformAndExternalId(getPlatform(), externalId)) {
                    continue;
                }

                // Content
                String content = getElementText(entry, "content");
                if (content == null || content.isBlank()) {
                    content = title;
                }

                // Clean HTML
                content = removeHtml(content).trim();
                if (content.length() > 5000) {
                    content = content.substring(0, 5000);
                }

                // Author
                String author = getNestedText(entry, "author", "name");

                // Date
                final String publishedDate = getElementText(entry, "published");
                final LocalDateTime postedAt = parseDate(publishedDate);

                // Build post
                final ScrapedPost post = ScrapedPost.builder()
                        .platform(getPlatform())
                        .externalId(externalId)
                        .title(title)
                        .content(content)
                        .proxyIpUsed(proxyIp)
                        .url(phUrl)
                        .author(author)
                        .score(0) // Feed doesn't supply upvotes directly
                        .commentCount(0)
                        .postedAt(postedAt)
                        .build();

                allPosts.add(post);
                scraped++;
            }

            log.info("Product Hunt scraped: {} new posts", allPosts.size());

        } catch (final IOException e) {
            log.error("Failed to scrape Product Hunt", e);
        } catch (final Exception e) {
            log.error("Unexpected error scraping Product Hunt", e);
        }

        return allPosts;
    }

    private Document parseXml(final String xml) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);

        final DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String getElementText(final Element parent, final String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagNameNS("*", tagName);
        }
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String getLinkHref(final Element entry) {
        final NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            final Element link = (Element) links.item(i);
            final String rel = link.getAttribute("rel");
            if ("alternate".equals(rel) || rel == null || rel.isEmpty()) {
                final String href = link.getAttribute("href");
                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }
        return null;
    }

    private String getNestedText(final Element parent, final String outerTag, final String innerTag) {
        final NodeList outerNodes = parent.getElementsByTagName(outerTag);
        if (outerNodes.getLength() == 0) {
            return null;
        }
        final Element outer = (Element) outerNodes.item(0);
        return getElementText(outer, innerTag);
    }

    private String removeHtml(final String text) {
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

    private LocalDateTime parseDate(final String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME);
            return zonedDateTime.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (final Exception e) {
            return null;
        }
    }
}
