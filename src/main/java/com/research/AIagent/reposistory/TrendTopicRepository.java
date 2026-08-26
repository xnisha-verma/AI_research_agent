package com.research.AIagent.reposistory;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.TrendTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TrendTopicRepository extends JpaRepository<TrendTopic, Long> {

    List<TrendTopic> findByDetectedAtAfterOrderByTrendScoreDesc(
            LocalDateTime since
    );

    List<TrendTopic> findTop20ByOrderByTrendScoreDesc();

    List<TrendTopic> findByCategoryOrderByTrendScoreDesc(
            String category
    );

    List<TrendTopic> findByPlatformOrderByTrendScoreDesc(
            Platform platform
    );

    List<TrendTopic> findByAnalysisIdOrderByTrendScoreDesc(
            long analysisId
    );
}
