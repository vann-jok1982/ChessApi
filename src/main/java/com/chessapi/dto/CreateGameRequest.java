package com.chessapi.dto;

import lombok.Data;

@Data
public class CreateGameRequest {
    private Long playerId;
    private String playerName;

    // Дефолтный конструктор для Jackson
    public CreateGameRequest() {
    }

    // Конструктор для Builder
    public CreateGameRequest(Long playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }
}