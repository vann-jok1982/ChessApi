package com.chessapi.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "players")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private String telegramId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "rating", nullable = false)
    @Builder.Default
    private Integer rating = 1200;

    // Статистика игр
    @Column(name = "games_played")
    @Builder.Default
    private Integer gamesPlayed = 0;

    @Column(name = "games_won")
    @Builder.Default
    private Integer gamesWon = 0;

    @Column(name = "games_lost")
    @Builder.Default
    private Integer gamesLost = 0;

    @Column(name = "games_drawn")
    @Builder.Default
    private Integer gamesDrawn = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "whitePlayer", fetch = FetchType.LAZY)
    private List<Game> whiteGames;

    @OneToMany(mappedBy = "blackPlayer", fetch = FetchType.LAZY)
    private List<Game> blackGames;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        // Инициализация полей статистики
        if (gamesPlayed == null) gamesPlayed = 0;
        if (gamesWon == null) gamesWon = 0;
        if (gamesLost == null) gamesLost = 0;
        if (gamesDrawn == null) gamesDrawn = 0;
        if (rating == null) rating = 1200;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addWin() {
        this.gamesPlayed = (this.gamesPlayed == null ? 0 : this.gamesPlayed) + 1;
        this.gamesWon = (this.gamesWon == null ? 0 : this.gamesWon) + 1;
        this.rating += 20;
    }

    public void addLoss() {
        this.gamesPlayed = (this.gamesPlayed == null ? 0 : this.gamesPlayed) + 1;
        this.gamesLost = (this.gamesLost == null ? 0 : this.gamesLost) + 1;
        this.rating = Math.max(100, this.rating - 20);
    }

    public void addDraw() {
        this.gamesPlayed = (this.gamesPlayed == null ? 0 : this.gamesPlayed) + 1;
        this.gamesDrawn = (this.gamesDrawn == null ? 0 : this.gamesDrawn) + 1;
        this.rating += 5;
    }

    public double getWinRate() {
        if (gamesPlayed == null || gamesPlayed == 0) return 0.0;
        return (double) (gamesWon == null ? 0 : gamesWon) / gamesPlayed * 100;
    }
}