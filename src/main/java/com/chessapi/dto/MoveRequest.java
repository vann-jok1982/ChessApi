package com.chessapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MoveRequest {

    @NotNull(message = "ID игрока обязателен")
    private Long playerId;

    @NotBlank(message = "Ход не может быть пустым")
    @Pattern(regexp = "^([KQBNR]?[a-h]?[1-8]?x?[a-h][1-8]|O-O|O-O-O)(=[QBNR])?[+#]?$",
            message = "Некорректная шахматная нотация")
    private String notation;
}