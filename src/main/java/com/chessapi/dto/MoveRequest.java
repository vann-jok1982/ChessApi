package com.chessapi.dto;

import lombok.Data;

@Data
public class MoveRequest {
    private Long playerId;
    private String notation;  // e2-e4
}