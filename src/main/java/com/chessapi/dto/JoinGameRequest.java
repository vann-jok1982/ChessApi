package com.chessapi.dto;

import lombok.Data;

@Data
public class JoinGameRequest {
    private Long playerId;
    private String playerName;
}