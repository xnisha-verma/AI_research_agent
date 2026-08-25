package com.research.AIagent.controller;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.TrendTopic;
import com.research.AIagent.service.TrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trends")
@RequiredArgsConstructor
public class TrendController {
    private final TrendService trendService;
    @PostMapping
    public ResponseEntity<List<TrendTopic>> getTopTrends(){
        return ResponseEntity.ok(this.trendService.getToptrends());
    }
    @GetMapping("/latest")
    public ResponseEntity<List<TrendTopic>> getLatestTrends(){
        return ResponseEntity.ok(this.trendService.getLatestTrends());
    }
    @GetMapping("/category/{category}")
    public ResponseEntity<List<TrendTopic>> getTrendsByCategory(
            @PathVariable
            final String category
    ){
        return ResponseEntity.ok(this.trendService.getTrendsByCategory(category));
    }
    @GetMapping("/platform/{platform}")
    public ResponseEntity<List<TrendTopic>> getTrendsByPlatform(
            @PathVariable
            final Platform platform
    ){
        return ResponseEntity.ok(this.trendService.getTrendsByPlatform(platform));
    }
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats(){
        return ResponseEntity.ok(this.trendService.getDashboardStats());
    }
}
