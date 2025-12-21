package com.chessapi.repository;

import com.chessapi.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByTelegramId(String telegramId);

    Optional<Player> findByUsername(String username);

    List<Player> findByOrderByRatingDesc();

    @Query("SELECT p FROM Player p WHERE p.rating >= :minRating AND p.rating <= :maxRating")
    List<Player> findByRatingBetween(@Param("minRating") int minRating,
                                     @Param("maxRating") int maxRating);

    boolean existsByTelegramId(String telegramId);

    boolean existsByUsername(String username);
}