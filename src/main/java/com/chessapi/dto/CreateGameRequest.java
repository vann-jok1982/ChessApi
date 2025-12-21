package com.chessapi.dto;

import lombok.Data;

@Data
public class CreateGameRequest {
    private Long playerId;
    private String playerName;
}