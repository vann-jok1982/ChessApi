package com.chessapi.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class GameInfoResponse {
    private String gameId;
    private String whitePlayerName;
    private LocalDateTime createdAt;
}