package com.chessapi.repository;

import com.chessapi.model.Game;
import com.chessapi.model.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    List<Move> findByGame(Game game);

    int countByGame(Game game);

    @Query("SELECT COUNT(m) FROM Move m WHERE m.game.id = :gameId")
    int countByGameId(@Param("gameId") Long gameId);

    @Modifying
    @Query("DELETE FROM Move m WHERE m.game = :game")
    void deleteByGame(@Param("game") Game game);

    @Modifying
    @Query("DELETE FROM Move m WHERE m.game.id = :gameId")
    void deleteByGameId(@Param("gameId") Long gameId);

    @Query("SELECT m FROM Move m WHERE m.game.publicId = :publicId ORDER BY m.moveNumber")
    List<Move> findByGamePublicId(@Param("publicId") String publicId);

    @Query("SELECT MAX(m.moveNumber) FROM Move m WHERE m.game.id = :gameId")
    Integer findLastMoveNumberByGameId(@Param("gameId") Long gameId);
}