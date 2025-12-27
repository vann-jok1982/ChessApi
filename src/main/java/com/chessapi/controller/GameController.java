package com.chessapi.controller;

import com.chessapi.dto.*;
import com.chessapi.model.Move;
import com.chessapi.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping
    public ResponseEntity<GameResponse> createGame(@RequestBody CreateGameRequest request) {
        log.info("POST /games - {}", request);
        GameResponse response = gameService.createGame(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<GameResponse> joinGame(
            @PathVariable String gameId,
            @RequestBody JoinGameRequest request) {
        log.info("POST /games/{}/join - {}", gameId, request);
        GameResponse response = gameService.joinGame(gameId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{gameId}/move")
    public ResponseEntity<GameResponse> makeMove(
            @PathVariable String gameId,
            @RequestBody MoveRequest request) {
        log.info("POST /games/{}/move - {}", gameId, request);
        GameResponse response = gameService.makeMove(gameId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameResponse> getGame(
            @PathVariable String gameId,
            @RequestParam Long playerId) {
        log.info("GET /games/{}?playerId={}", gameId, playerId);
        GameResponse response = gameService.getGame(gameId, playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/waiting")
    public ResponseEntity<List<GameInfoResponse>> getWaitingGames() {
        log.info("GET /games/waiting");
        List<GameInfoResponse> waitingGames = gameService.getWaitingGames();
        return ResponseEntity.ok(waitingGames);
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Chess API with PostgreSQL is working!");
    }

    // Новые endpoints для будущего
    @GetMapping("/{gameId}/moves")
    public ResponseEntity<List<Move>> getGameMoves(@PathVariable String gameId) {
        // TODO: реализовать.
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{gameId}/resign")
    public ResponseEntity<GameResponse> resignGame(
            @PathVariable String gameId,
            @RequestParam Long playerId) {
        // TODO: реализовать
        return ResponseEntity.ok(GameResponse.error("Not implemented yet"));
    }
    @PostMapping("/{gameId}/draw/offer")
    public ResponseEntity<GameResponse> offerDraw(
            @PathVariable String gameId,
            @RequestParam Long playerId) {
        log.info("POST /games/{}/draw/offer?playerId={}", gameId, playerId);
        GameResponse response = gameService.offerDraw(gameId, playerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{gameId}/draw/respond")
    public ResponseEntity<GameResponse> respondToDraw(
            @PathVariable String gameId,
            @RequestParam Long playerId,
            @RequestParam boolean accept) {
        log.info("POST /games/{}/draw/respond?playerId={}&accept={}",
                gameId, playerId, accept);
        GameResponse response = gameService.respondToDraw(gameId, playerId, accept);
        return ResponseEntity.ok(response);
    }
}