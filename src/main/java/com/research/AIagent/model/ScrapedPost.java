package com.research.AIagent.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "scraped_posts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"platform", "externalId"})
        }
)
public class ScrapedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 2048)
    private String url;

    private String author;

    private int score;

    private int commentCount;

    @Column(length = 512)
    private String subReddit;

    private String proxyIpUsed;

    private LocalDateTime postedAt;

    private LocalDateTime scrapedAt;

    @PrePersist
    public void prePersist() {
        this.scrapedAt = LocalDateTime.now();
    }
}