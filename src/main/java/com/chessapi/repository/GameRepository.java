package com.chessapi.repository;

import com.chessapi.model.Game;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    // Основные методы

    Optional<Game> findByPublicId(String publicId);

    /**
     * Найти активные игры игрока
     */
    @Query("SELECT g FROM Game g WHERE " +
            "(g.whitePlayer.id = :playerId OR g.blackPlayer.id = :playerId) " +
            "AND g.status = 'ACTIVE'")
    List<Game> findActiveGamesByPlayer(@Param("playerId") Long playerId);

    /**
     * Найти ожидающие игры (не старше указанного времени)
     */
    @Query("SELECT g FROM Game g WHERE g.status = 'WAITING' AND g.createdAt >= :cutoffTime")
    List<Game> findWaitingGames(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Найти старые ожидающие игры (старше указанной даты)
     */
    @Query("SELECT g FROM Game g WHERE g.status = 'WAITING' AND g.createdAt < :cutoffDate")
    List<Game> findOldWaitingGames(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Найти старые завершённые игры (вариант 1 - с явным перечислением enum)
     */
    @Query("SELECT g FROM Game g WHERE g.status IN (com.chessapi.model.Game.GameStatus.WHITE_WIN, " +
            "com.chessapi.model.Game.GameStatus.BLACK_WIN, " +
            "com.chessapi.model.Game.GameStatus.DRAW, " +
            "com.chessapi.model.Game.GameStatus.TIMEOUT) " +
            "AND g.updatedAt < :cutoffDate")
    List<Game> findOldFinishedGames(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Найти игры по статусу и дате обновления
     */
    @Query("SELECT g FROM Game g WHERE g.status IN :statuses AND g.updatedAt < :cutoffDate")
    List<Game> findOldFinishedGamesByStatuses(
            @Param("statuses") List<Game.GameStatus> statuses,
            @Param("cutoffDate") LocalDateTime cutoffDate
    );

    /**
     * Найти игры по дате обновления
     */
    @Query("SELECT g FROM Game g WHERE g.updatedAt < :date")
    List<Game> findByUpdatedAtBefore(@Param("date") LocalDateTime date);

    /**
     * Подсчитать игры по статусу
     */
    long countByStatus(Game.GameStatus status);

    /**
     * Подсчитать игры по списку статусов
     */
    @Query("SELECT COUNT(g) FROM Game g WHERE g.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<Game.GameStatus> statuses);

    /**
     * Найти игры по статусу и дате создания
     */
    List<Game> findByStatusAndCreatedAtBefore(Game.GameStatus status, LocalDateTime date);

    /**
     * Найти игры по списку статусов и дате обновления
     */
    @Query("SELECT g FROM Game g WHERE g.status IN :statuses AND g.updatedAt < :date")
    List<Game> findByStatusInAndUpdatedAtBefore(
            @Param("statuses") List<Game.GameStatus> statuses,
            @Param("date") LocalDateTime date
    );

    /**
     * Удалить игры по публичному ID
     */
    void deleteByPublicId(String publicId);

    /**
     * Найти игры, где предложена ничья
     */
    @Query("SELECT g FROM Game g WHERE g.drawOfferedBy IS NOT NULL")
    List<Game> findGamesWithDrawOffered();

    /**
     * Проверить существует ли игра с таким publicId
     */
    boolean existsByPublicId(String publicId);

    /**
     * Найти игры созданные конкретным игроком
     */
    @Query("SELECT g FROM Game g WHERE g.whitePlayer.id = :playerId " +
            "OR g.blackPlayer.id = :playerId " +
            "ORDER BY g.createdAt DESC")
    List<Game> findGamesByPlayerId(@Param("playerId") Long playerId);

    // Для блокировки при ходе
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.publicId = :publicId")
    Optional<Game> findByPublicIdForUpdate(@Param("publicId") String publicId);

    /**
     * Дефолтный метод для удобства
     */
    default List<Game> findFinishedGamesBefore(LocalDateTime cutoffDate) {
        List<Game.GameStatus> finishedStatuses = Arrays.asList(
                Game.GameStatus.WHITE_WIN,
                Game.GameStatus.BLACK_WIN,
                Game.GameStatus.DRAW,
                Game.GameStatus.TIMEOUT
        );
        return findOldFinishedGamesByStatuses(finishedStatuses, cutoffDate);
    }
}