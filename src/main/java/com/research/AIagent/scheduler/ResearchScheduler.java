package com.research.AIagent.scheduler;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import com.research.AIagent.service.LlmAnalysisService;
import com.research.AIagent.service.ScrapingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j

public class ResearchScheduler {
    private final ScrapingOrchestrator scrapingOrchestrator;
    private final ScrapedPostRepository postRepository;
    private final LlmAnalysisService analysisService;

    @Scheduled(cron = "${scraping.cron}")
    public void runResearchCycle() {

        log.info("===== Research cycle started =====");

        if (this.scrapingOrchestrator.isCurrentlyScraping()) {
            log.info("Scraping already in progress. Skipping scheduled cycle.");
            return;
        }

        try {

            final Map<Platform, Integer> results =
                    this.scrapingOrchestrator.scrapeAll();

            log.info("Scraping completed. Results: {}", results);

            final LocalDateTime since =
                    LocalDateTime.now().minusHours(6);

            final List<ScrapedPost> recentPosts =
                    this.postRepository
                            .findRecentPostsOrderByScoreDesc(since);

            log.info(
                    "Recent posts available for LLM analysis: {}",
                    recentPosts.size()
            );

            if (!recentPosts.isEmpty()) {

                log.info("Starting LLM analysis...");

                this.analysisService.analyze(recentPosts);

                log.info(
                        "LLM analysis completed for {} posts.",
                        recentPosts.size()
                );
            }

        } catch (final Exception e) {

            log.error("Scheduled research cycle failed", e);
        }
    }

}