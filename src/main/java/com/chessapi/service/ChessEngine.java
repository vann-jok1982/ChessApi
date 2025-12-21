package com.chessapi.service;

import com.chessapi.model.Color;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ChessEngine {

    @Getter
    private Board board;

    public static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public ChessEngine() {
        this.board = new Board();
        board.loadFromFen(STARTING_FEN);
    }

    // УПРОЩЕННАЯ ПРОВЕРКА НА НИЧЬЮ
    public boolean isDraw() {
        // В chesslib 1.3.0 используем встроенные методы
        return board.isStaleMate() ||
                isInsufficientMaterial() ||
                isFiftyMoveRule();
    }

    public boolean isInsufficientMaterial() {
        String fen = board.getFen().split(" ")[0];

        // Считаем количество каждого типа фигур
        int kings = countPieces(fen, "Kk");
        int queens = countPieces(fen, "Qq");
        int rooks = countPieces(fen, "Rr");
        int bishops = countPieces(fen, "Bb");
        int knights = countPieces(fen, "Nn");
        int pawns = countPieces(fen, "Pp");

        // Только короли
        if (kings == 2 && queens == 0 && rooks == 0 && bishops == 0 && knights == 0 && pawns == 0) {
            return true;
        }

        // Король + слон/конь против короля
        if (kings == 2 && queens == 0 && rooks == 0 && pawns == 0) {
            // Король и слон против короля
            if (bishops == 1 && knights == 0) {
                return true;
            }
            // Король и конь против короля
            if (bishops == 0 && knights == 1) {
                return true;
            }
        }

        return false;
    }

    private int countPieces(String fen, String pieceTypes) {
        int count = 0;
        for (char c : fen.toCharArray()) {
            if (pieceTypes.indexOf(c) >= 0) {
                count++;
            }
        }
        return count;
    }

    private boolean isFiftyMoveRule() {
        String fen = board.getFen();
        String[] parts = fen.split(" ");

        if (parts.length >= 5) {
            try {
                int halfmoveClock = Integer.parseInt(parts[4]);
                return halfmoveClock >= 50;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    public boolean makeMove(String notation) {
        try {
            // Убираем все не-алфавитные символы кроме тире
            String cleanNotation = notation
                    .replace("#", "")  // Убираем мат
                    .replace("+", "")  // Убираем шах
                    .replace("!", "")  // Убираем восклицательные знаки
                    .replace("?", ""); // Убираем вопросительные знаки

            // Проверяем формат
            if (!cleanNotation.matches("[a-h][1-8]-[a-h][1-8]")) {
                log.warn("Некорректный формат хода: {}", notation);
                return false;
            }

            Move move = new Move(cleanNotation.replace("-", ""), board.getSideToMove());

            if (!board.isMoveLegal(move, true)) {
                log.warn("Недопустимый ход: {} (очищенный: {})", notation, cleanNotation);
                return false;
            }

            board.doMove(move);
            log.info("Ход выполнен: {} -> {}, шах: {}, мат: {}",
                    notation, cleanNotation,
                    board.isKingAttacked(), board.isMated());
            return true;

        } catch (Exception e) {
            log.error("Ошибка выполнения хода {}: {}", notation, e.getMessage());
            return false;
        }
    }

    public String getBoardAsText() {
        return board.toString();
    }

    public String getBoardForPlayer(Color playerColor) {
        String boardText = board.toString();

        // Если игрок чёрные, переворачиваем доску
        if (playerColor == Color.BLACK) {
            String[] rows = boardText.split("\n");
            StringBuilder reversed = new StringBuilder();
            for (int i = rows.length - 1; i >= 0; i--) {
                reversed.append(rows[i]).append("\n");
            }
            return reversed.toString().trim();
        }

        return boardText;
    }

    public boolean isCheck() {
        return board.isKingAttacked();
    }

    public boolean isCheckmate() {
        return board.isMated();
    }

    public boolean isStalemate() {
        return board.isStaleMate();
    }

    public String getSideToMove() {
        return board.getSideToMove().toString();
    }

    public String getFen() {
        return board.getFen();
    }

    public List<Move> getLegalMoves() {
        return board.legalMoves();
    }

    // Новый метод для получения списка допустимых ходов в нотации
    public List<String> getLegalMoveNotations() {
        List<Move> moves = board.legalMoves();
        return moves.stream()
                .map(Move::toString)
                .collect(java.util.stream.Collectors.toList());
    }
}