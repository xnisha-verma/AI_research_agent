package com.research.AIagent.scraper;

import com.research.AIagent.config.ProxyConfig;
import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class HackerNewsScraper extends AbstractScarper implements PlatformScraper {

    private final ScrapedPostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Value("${scraping.hackernews.top-stories-count}")
    private int topStoriesCount;

    public HackerNewsScraper(
            final ProxyConfig proxyConfig,
            final ScrapedPostRepository postRepository,
            final ObjectMapper objectMapper) {

        super(proxyConfig);
        this.postRepository = postRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Platform getPlatform() {
        return Platform.HACKERNEWS;
    }

    @Override
    public List<ScrapedPost> scrape() {

        final List<ScrapedPost> posts = new ArrayList<>();

        log.info("Hacker News scraper started");
        log.info("Fetching top {} stories", topStoriesCount);

        try {

            // Get IDs of top stories
            final String topStoriesUrl =
                    "https://hacker-news.firebaseio.com/v0/topstories.json";

            final String json = fetch(topStoriesUrl);

            final JsonNode storyIds = objectMapper.readTree(json);

            int count = 0;

            for (final JsonNode storyIdNode : storyIds) {

                if (count >= topStoriesCount) {
                    break;
                }

                final String externalId = storyIdNode.asText();

                // Avoid duplicate posts
                if (postRepository.existsByPlatformAndExternalId(
                        getPlatform(), externalId)) {
                    continue;
                }

                try {

                    // Fetch individual story
                    final String storyUrl =
                            "https://hacker-news.firebaseio.com/v0/item/"
                                    + externalId + ".json";

                    final String storyJson = fetch(storyUrl);

                    final JsonNode data =
                            objectMapper.readTree(storyJson);

                    // Make sure this is actually a story
                    if (!"story".equals(data.path("type").asText())) {
                        continue;
                    }

                    final String title =
                            data.path("title").asText("");

                    if (title.isBlank()) {
                        continue;
                    }

                    final String content =
                            data.path("text").asText("").trim();

                    final String url =
                            data.path("url").asText(
                                    "https://news.ycombinator.com/item?id="
                                            + externalId);

                    final String author =
                            data.path("by").asText(null);

                    final int score =
                            data.path("score").asInt(0);

                    final int commentCount =
                            data.path("descendants").asInt(0);

                    final long postedAtEpoch =
                            data.path("time").asLong(0);

                    final LocalDateTime postedAt =
                            postedAtEpoch > 0
                                    ? LocalDateTime.ofInstant(
                                    Instant.ofEpochSecond(postedAtEpoch),
                                    ZoneId.systemDefault())
                                    : null;

                    final String proxyIp = detectProxyIp();

                    final ScrapedPost post = ScrapedPost.builder()
                            .platform(getPlatform())
                            .externalId(externalId)
                            .title(title)
                            .content(content.isBlank() ? title : content)
                            .proxyIpUsed(proxyIp)
                            .url(url)
                            .author(author)
                            .score(score)
                            .commentCount(commentCount)
                            .postedAt(postedAt)
                            .build();

                    posts.add(post);
                    count++;

                    log.debug("Hacker News story scraped: {}", title);

                    Thread.sleep(300);

                } catch (final InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;

                } catch (final IOException e) {

                    log.error(
                            "Failed to fetch Hacker News story {}",
                            externalId,
                            e
                    );
                }
            }

            log.info(
                    "Hacker News scraped: {} new posts",
                    posts.size()
            );

        } catch (final IOException e) {

            log.error("Failed to fetch Hacker News top stories", e);
        }

        return posts;
    }
}