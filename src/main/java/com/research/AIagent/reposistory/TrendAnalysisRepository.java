package com.research.AIagent.reposistory;

import com.research.AIagent.model.TrendAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrendAnalysisRepository  extends JpaRepository<TrendAnalysis, Long> {
    Optional<TrendAnalysis> findTopByOrderByAnalyzedAtDesc();

    java.util.List<TrendAnalysis> findTop5ByOrderByAnalyzedAtDesc();

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TrendAnalysis a WHERE a.analyzedAt < :cutoff")
    void deleteAnalysisOlderThan(@org.springframework.data.repository.query.Param("cutoff") java.time.LocalDateTime cutoff);
}
