package com.research.AIagent.service;

import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import com.research.AIagent.reposistory.TrendAnalysisRepository;
import com.research.AIagent.reposistory.TrendTopicRepository;
import com.research.AIagent.scraper.PlatformScraper;
import com.research.AIagent.model.TrendAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.research.AIagent.model.Platform;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScrapingOrchestrator {
    private final List<PlatformScraper> scrapers;
    private final ScrapedPostRepository postRepository;
    private final TrendAnalysisRepository analysisRepository;
    private final TrendTopicRepository topicRepository;
    private final AtomicBoolean isScraping = new AtomicBoolean(false);

    public boolean isCurrentlyScraping() {
        return isScraping.get();
    }

    public Map<Platform, Integer> scrapeAll(){
        if (!isScraping.compareAndSet(false, true)) {
            log.warn("Scrape cycle requested, but one is already in progress.");
            throw new IllegalStateException("Scrape cycle already in progress");
        }
        try {
            // Prune old data to keep database size under Supabase 500MB free-tier limits
            pruneOldData();

            final Map<Platform, Integer> results = new EnumMap<>(Platform.class);
            for(final PlatformScraper scraper: scrapers){
                try{
                    final List<ScrapedPost> scrapedPosts = scraper.scrape();
                    final List<ScrapedPost> saved = this.postRepository.saveAll(scrapedPosts);

                    results.put(scraper.getPlatform(), saved.size());
                    log.info("Scraped {} new posts from {}", saved.size(), scraper.getPlatform());
                }catch (final Exception e){
                    log.error("Failed to scrape {}", scraper.getPlatform(), e);
                }
            }
            return results;
        } finally {
            isScraping.set(false);
        }
    }

    private void pruneOldData() {
        try {
            log.info("Starting database pruning to respect free tier storage limits...");
            
            // 1. Keep only the last 3 days of scraped posts
            final LocalDateTime postCutoff = LocalDateTime.now().minusDays(3);
            this.postRepository.deletePostsOlderThan(postCutoff);
            log.info("Pruned scraped posts older than {}", postCutoff);

            // 2. Keep only the 5 most recent trend analysis cycles and their topics
            final List<TrendAnalysis> top5 = this.analysisRepository.findTop5ByOrderByAnalyzedAtDesc();
            if (top5.size() >= 5) {
                final LocalDateTime analysisCutoff = top5.get(top5.size() - 1).getAnalyzedAt();
                this.topicRepository.deleteTopicsOlderThan(analysisCutoff);
                this.analysisRepository.deleteAnalysisOlderThan(analysisCutoff);
                log.info("Pruned analysis cycles and topics older than {}", analysisCutoff);
            }
        } catch (final Exception e) {
            log.error("Failed to prune old data: {}", e.getMessage(), e);
        }
    }

    public List<ScrapedPost> scrapedPlatform(final Platform platform ){
        return this.scrapers.stream()
                .filter(scraper -> scraper.getPlatform()==platform)
                .findFirst()
                .map(PlatformScraper::scrape)
                .map(this.postRepository::saveAll)
                .orElse(List.of());
    }
}
