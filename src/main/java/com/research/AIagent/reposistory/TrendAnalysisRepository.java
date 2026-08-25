package com.research.AIagent.reposistory;

import com.research.AIagent.model.TrendAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrendAnalysisRepository  extends JpaRepository<TrendAnalysis, Long> {
    Optional<TrendAnalysis> findTopByOrderByAnalyzedAtDesc();
}
