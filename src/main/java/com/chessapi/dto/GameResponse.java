package com.chessapi.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GameResponse {
    private boolean success;
    private String gameId;
    private String status;           // ACTIVE, CHECK, CHECKMATE, STALEMATE, DRAW
    private PlayerInfo whitePlayer;
    private PlayerInfo blackPlayer;
    private String currentTurn;      // WHITE или BLACK
    private String board;            // Текстовая доска
    private String message;
    private Map<String, String> additionalInfo;
    private List<String> legalMoves; // Добавьте это поле
    private String playerColor;  // "WHITE", "BLACK" или "OBSERVER"

    public static GameResponse success(GameResponse response) {
        response.setSuccess(true);
        return response;
    }

    public static GameResponse error(String message) {
        return GameResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    @Data
    @Builder
    public static class PlayerInfo {
        private Long id;
        private String name;
        private String color; // WHITE, BLACK
        private Integer rating; // Новое поле
    }
}