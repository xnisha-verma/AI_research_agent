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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrendService {
    private final TrendTopicRepository trendTopicRepository;
    private final ScrapedPostRepository scrapedPostRepository;
    private final TrendAnalysisRepository trendAnalysisRepository;

    public List<TrendTopic> getLatestTrends(){
        return this.trendAnalysisRepository.findTopByOrderByAnalyzedAtDesc()
                .map(analysis -> this.trendTopicRepository.findByAnalysisIdOrderByTrendScoreDesc(analysis.getId()))
                .filter(list -> !list.isEmpty())
                .orElseGet(() -> this.trendTopicRepository.findTop20ByOrderByTrendScoreDesc());
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
        final Map<String, Object> stats = new HashMap<>();
        stats.put("totalPosts", this.scrapedPostRepository.count());
        stats.put("redditPosts", this.scrapedPostRepository.countByPlatform(Platform.REDDIT));
        stats.put("hackerNewsPosts", this.scrapedPostRepository.countByPlatform(Platform.HACKERNEWS));
        stats.put("productHuntPosts", this.scrapedPostRepository.countByPlatform(Platform.PRODUCTHUNT));
        stats.put("totalTrends", this.trendTopicRepository.count());
        stats.put("LastAnalysis", this.trendAnalysisRepository.findTopByOrderByAnalyzedAtDesc()
                .map(TrendAnalysis::getAnalyzedAt)
                .orElse(null));
        return stats;
    }
}
