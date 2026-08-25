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
@Table(name = "trend_topics")
public class TrendTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private String category;
    private int mentionCount;

    private double trendScore;

    @Enumerated(EnumType.STRING)
    private  Platform platform;

    @Column(length = 2048)
    private String samplePostIds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name= "analysis_id")
    private  TrendAnalysis analysis;
    private LocalDateTime detectedAt;

    @PrePersist
    public void prePersist(){
        this.detectedAt =  LocalDateTime.now();
    }


}



