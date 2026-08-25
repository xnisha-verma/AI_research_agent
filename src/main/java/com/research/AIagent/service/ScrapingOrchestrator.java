package com.research.AIagent.service;

import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import com.research.AIagent.scraper.PlatformScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.research.AIagent.model.Platform;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScrapingOrchestrator {
    private final List<PlatformScraper> scrapers;
    private final ScrapedPostRepository postRepository;
    public Map<Platform, Integer> scrapeAll(){
        final Map<Platform, Integer> results = new EnumMap<>(Platform.class);
        for(final PlatformScraper scraper: scrapers){
            try{
                final List<ScrapedPost> scrapedPosts = scraper.scrape();
                final List<ScrapedPost> saved = this.postRepository.saveAll(scrapedPosts);

                results.put(scraper.getPlatform(), saved.size());
                log.info("Scraped {} new posts from {}", saved.size(), scraper.getPlatform());
            }catch (final Exception e){
                log.error("Failed to scrape {}", scraper.getPlatform(), e.getMessage());
//                results.put(scraper.getPlatform(),0);
            }
        }
        return results;
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
