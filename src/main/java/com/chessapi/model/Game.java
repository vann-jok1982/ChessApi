package com.chessapi.model;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_player_id")
    private Player whitePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_player_id")
    private Player blackPlayer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameStatus status;

    @Column(name = "current_turn")
    private String currentTurn;

    @Column(name = "current_fen", length = 100)
    private String currentFen;

    @Column(name = "is_archived")
    @Builder.Default
    private boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "draw_offered_by")
    private Long drawOfferedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum GameStatus {
        WAITING,      // Ожидает второго игрока
        ACTIVE,       // Игра идёт
        WHITE_WIN,    // Победа белых
        BLACK_WIN,    // Победа чёрных
        DRAW,         // Ничья
        TIMEOUT,      // Истекло время
        ABANDONED     // Игра брошена
    }

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId = generatePublicId();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generatePublicId() {
        return "G" + System.currentTimeMillis() + "_" +
                Math.abs(hashCode()) % 10000;
    }

    public boolean isPlayerInGame(Long playerId) {
        if (playerId == null) return false;
        return (whitePlayer != null && playerId.equals(whitePlayer.getId())) ||
                (blackPlayer != null && playerId.equals(blackPlayer.getId()));
    }

    public Color getPlayerColor(Long playerId) {
        if (playerId == null) return null;
        if (whitePlayer != null && playerId.equals(whitePlayer.getId())) {
            return Color.WHITE;
        }
        if (blackPlayer != null && playerId.equals(blackPlayer.getId())) {
            return Color.BLACK;
        }
        return null;
    }

    public void archive() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Проверки статусов
    public boolean isWaiting() {
        return status == GameStatus.WAITING;
    }

    public boolean isActive() {
        return status == GameStatus.ACTIVE;
    }

    public boolean isFinished() {
        return status == GameStatus.WHITE_WIN ||
                status == GameStatus.BLACK_WIN ||
                status == GameStatus.DRAW;
    }
}