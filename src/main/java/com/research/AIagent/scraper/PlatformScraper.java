package com.research.AIagent.scraper;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;

import java.util.List;

public interface PlatformScraper {
    Platform getPlatform();

    List<ScrapedPost> scrape();
}
