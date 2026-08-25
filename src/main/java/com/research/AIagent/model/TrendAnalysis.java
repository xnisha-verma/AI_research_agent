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
@Table(name = "trend_analysis")
public class TrendAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawAnalysis;

    @Enumerated(EnumType.STRING)
    private Platform platform;

    private int postAnlaysis;
    private LocalDateTime analyzedAt;

    @PrePersist
    public void prePersist(){
        this.analyzedAt = LocalDateTime.now();
    }
}
