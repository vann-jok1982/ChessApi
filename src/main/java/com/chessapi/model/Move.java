package com.chessapi.model;

import jakarta.persistence.*;
import lombok.*;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Table(name = "moves")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    @ToString.Exclude
    private Game game;

    @Column(nullable = false)
    private Integer moveNumber;  // Номер хода (1, 2, 3...)

    @Column(nullable = false)
    private String notation;  // Нотация хода (e2-e4)

    @Column(length = 1000)
    private String fenAfter;  // Позиция после хода

    @Column
    private String san;  // Стандартная алгебраическая нотация (если есть)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}