package com.research.AIagent.controller;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.model.TrendAnalysis;
import com.research.AIagent.reposistory.ScrapedPostRepository;
import com.research.AIagent.service.LlmAnalysisService;
import com.research.AIagent.service.ScrapingOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scrape")
@RequiredArgsConstructor
public class ScrapedController {
    private final ScrapingOrchestrator orchestrator;
    private final LlmAnalysisService analysisService;
    private final ScrapedPostRepository postRepository;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> triggerFullCycle() {
        final Map<Platform, Integer> scrapedResults = this.orchestrator.scrapeAll();
        final LocalDateTime since = LocalDateTime.now().minusHours(6);
        final List<ScrapedPost> posts = this.postRepository.findRecentPostsOrderByScoreDesc(since);
        TrendAnalysis analysis = null;
        if (!posts.isEmpty()) {
            analysis = this.analysisService.analyze(posts);

        }
        return ResponseEntity.ok(
                Map.of("scrapeResults", scrapedResults,
                        "postAnyalzed", posts.size(),
                        "trendAnalysis", analysis != null ? analysis.getId() : "none")

        );
    }

        @PostMapping("/plaform/{platform}")
                public ResponseEntity<List<ScrapedPost>> scrapePlatform(
                        @PathVariable final Platform platform){
            return ResponseEntity.ok(this.orchestrator.scrapedPlatform(platform));
        }

    @GetMapping("/posts")
    public ResponseEntity<List<ScrapedPost>> getRecentPosts(
            @RequestParam(required = false)
            final Platform platform
    ){
    if(platform!=null){
        return ResponseEntity.ok(this.postRepository.findByPlatformOrderByScrapedAtDesc(platform));
        }
        return ResponseEntity.ok(this.postRepository.findTop200ByOrderByScrapedAtDesc());
    }

}
