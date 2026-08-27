
package com.research.AIagent.reposistory;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScrapedPostRepository extends JpaRepository<ScrapedPost, Long> {

    boolean existsByPlatformAndExternalId(
            Platform platform,
            String externalId
    );

    Object countByPlatform(Platform platform);

    @Query("""
            SELECT p
            FROM ScrapedPost p
            WHERE p.scrapedAt > :since
            ORDER BY p.score DESC
            """)
    List<ScrapedPost> findRecentPostsOrderByScoreDesc(
            @Param("since") LocalDateTime since
    );

    List<ScrapedPost> findByPlatformOrderByScrapedAtDesc(
            Platform platform
    );

    List<ScrapedPost> findTop200ByOrderByScrapedAtDesc();

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ScrapedPost p WHERE p.scrapedAt < :cutoff")
    void deletePostsOlderThan(@Param("cutoff") LocalDateTime cutoff);
}