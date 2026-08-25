package com.research.AIagent.service;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.TrendAnalysis;
import com.research.AIagent.model.TrendTopic;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import com.research.AIagent.reposistory.TrendAnalysisRepository;
import com.research.AIagent.reposistory.TrendTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrendService {
    private final TrendTopicRepository trendTopicRepository;
    private final ScrapedPostRepository scrapedPostRepository;
    private final TrendAnalysisRepository trendAnalysisRepository;

    public List<TrendTopic> getLatestTrends(){
        final LocalDateTime since =  LocalDateTime.now().minusHours(24);
        return this.trendTopicRepository.findByDetectedAtAfterOrderByTrendScoreDesc(since);
    }

    public List<TrendTopic> getToptrends(){
        return this.trendTopicRepository.findTop20ByOrderByTrendScoreDesc();
    }
    public List<TrendTopic> getTrendsByCategory(final String category){
        return this.trendTopicRepository.findByCategoryOrderByTrendScoreDesc(category);
    }
    public List<TrendTopic> getTrendsByPlatform(final Platform platform){
        return this.trendTopicRepository.findByPlatformOrderByTrendScoreDesc(platform);
    }
    public Map<String, Object> getDashboardStats(){
        return Map.of(
                "totalPosts", this.scrapedPostRepository.count(),
                "redditPosts", this.scrapedPostRepository.countByPlatform(Platform.REDDIT),
                "hackerNewsPosts", this.scrapedPostRepository.countByPlatform(Platform.HACKERNEWS),
                "productHuntPosts", this.scrapedPostRepository.countByPlatform(Platform.PRODUCTHUNT),
                "totalTrends", this.trendTopicRepository.count(),
                "LastAnalysis",this.trendAnalysisRepository.findTopByOrderByAnalyzedAtDesc()
                        .map(TrendAnalysis::getAnalyzedAt)
                        .orElse(LocalDateTime.now())
                );
    }
}
