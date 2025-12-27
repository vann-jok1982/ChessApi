package com.chessapi.service;

import com.chessapi.model.Color;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveGeneratorException;
import com.github.bhlangonijr.chesslib.move.MoveList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@Component
public class ChessEngine {

    @Getter
    private final Board board;

    // Стандартные FEN позиции
    public static final String STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // Шаблоны для разбора шахматной нотации
    private static final Pattern SAN_PATTERN = Pattern.compile(
            "^([KQBNR])?([a-h]?[1-8]?)(x?)([a-h][1-8])(=[QRNB])?([+#])?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "^([a-h][1-8])\\s*[-]?\\s*([a-h][1-8])(=[QRNBqrnb])?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, Piece> PIECE_MAP = new HashMap<>();

    static {
        PIECE_MAP.put("K", Piece.WHITE_KING);
        PIECE_MAP.put("Q", Piece.WHITE_QUEEN);
        PIECE_MAP.put("R", Piece.WHITE_ROOK);
        PIECE_MAP.put("B", Piece.WHITE_BISHOP);
        PIECE_MAP.put("N", Piece.WHITE_KNIGHT);
        PIECE_MAP.put("P", Piece.WHITE_PAWN);
        PIECE_MAP.put("k", Piece.BLACK_KING);
        PIECE_MAP.put("q", Piece.BLACK_QUEEN);
        PIECE_MAP.put("r", Piece.BLACK_ROOK);
        PIECE_MAP.put("b", Piece.BLACK_BISHOP);
        PIECE_MAP.put("n", Piece.BLACK_KNIGHT);
        PIECE_MAP.put("p", Piece.BLACK_PAWN);
    }

    /**
     * Конструктор по умолчанию - начальная позиция
     */
    public ChessEngine() {
        this.board = new Board();
        board.loadFromFen(STARTING_FEN);
    }

    /**
     * Конструктор с заданной позицией FEN
     */
    public ChessEngine(String fen) {
        this.board = new Board();
        if (fen != null && !fen.trim().isEmpty()) {
            try {
                board.loadFromFen(fen);
            } catch (Exception e) {
                log.error("Ошибка загрузки FEN '{}': {}", fen, e.getMessage());
                board.loadFromFen(STARTING_FEN);
            }
        } else {
            board.loadFromFen(STARTING_FEN);
        }
    }

    /**
     * Выполнить ход в стандартной шахматной нотации (SAN/UCI)
     */
    public boolean makeMove(String notation) {
        try {
            log.debug("Попытка выполнить ход: {}", notation);

            // Проверяем, является ли ход превращением
            if (isPromotionNotation(notation)) {
                Move promotionMove = handlePromotionMove(notation);
                if (promotionMove != null && board.isMoveLegal(promotionMove, true)) {
                    board.doMove(promotionMove);
                    log.info("Ход превращения выполнен: {} -> {}", notation, promotionMove);
                    return true;
                }
            }

            // Пытаемся интерпретировать ход
            Move move = interpretNotation(notation);
            if (move == null) {
                log.warn("Невозможно интерпретировать ход: {}", notation);
                return false;
            }

            // Проверяем легальность
            if (!board.isMoveLegal(move, true)) {
                log.warn("Ход не легален: {} ({} -> {})",
                        notation, move.getFrom(), move.getTo());
                return false;
            }

            // Выполняем ход
            board.doMove(move);

            log.debug("Ход выполнен: {} -> {}, FEN: {}",
                    notation, move, board.getFen());

            return true;

        } catch (Exception e) {
            log.error("Ошибка выполнения хода '{}': {}", notation, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Нормализация нотации хода
     */
    private String normalizeNotation(String notation) {
        if (notation == null || notation.trim().isEmpty()) {
            return "";
        }

        String normalized = notation.trim()
                .replaceAll("\\s+", "")          // Убираем пробелы
                .replaceAll("(?i)o-o-o", "O-O-O") // Длинная рокировка
                .replaceAll("(?i)o-o", "O-O")     // Короткая рокировка
                .replaceAll("[+#!?]", "");       // Убираем спецсимволы

        // Для простых нотаций типа "e2-e4" -> "e2e4"
        if (normalized.contains("-")) {
            normalized = normalized.replace("-", "");
        }

        return normalized;
    }

    /**
     * Интерпретация нотации в объект Move
     */
    private Move interpretNotation(String notation) {
        String normalized = normalizeNotation(notation);

        // 1. Проверка на рокировку
        if (normalized.equalsIgnoreCase("O-O") || normalized.equals("0-0")) {
            return getCastlingMove(true);
        }

        if (normalized.equalsIgnoreCase("O-O-O") || normalized.equals("0-0-0")) {
            return getCastlingMove(false);
        }

        // 2. Проверка на простую нотацию (e2e4, e7e8q)
        Matcher simpleMatcher = SIMPLE_PATTERN.matcher(normalized);
        if (simpleMatcher.matches()) {
            String from = simpleMatcher.group(1).toLowerCase();
            String to = simpleMatcher.group(2).toLowerCase();
            String promotion = simpleMatcher.group(3);

            try {
                Square fromSquare = Square.fromValue(from.toUpperCase());
                Square toSquare = Square.fromValue(to.toUpperCase());

                // Обработка превращения пешки
                if (promotion != null && promotion.startsWith("=")) {
                    char promoPiece = promotion.charAt(1);
                    return createPromotionMove(fromSquare, toSquare, promoPiece, board.getSideToMove());
                }

                return new Move(fromSquare, toSquare);

            } catch (Exception e) {
                log.debug("Ошибка разбора простой нотации: {}", e.getMessage());
            }
        }

        // 3. Проверка на SAN нотацию (Nf3, Bxe5, e4, Qd8#)
        Matcher sanMatcher = SAN_PATTERN.matcher(normalized);
        if (sanMatcher.matches()) {
            return parseSanNotation(sanMatcher);
        }

        // 4. Пытаемся найти среди легальных ходов
        return findMoveAmongLegal(normalized);
    }

    /**
     * Проверить, является ли нотация превращением
     */
    private boolean isPromotionNotation(String notation) {
        String normalized = normalizeNotation(notation);

        // Проверяем наличие символа превращения
        return normalized.matches(".*[=][QRNBqrnb]$") ||
                (normalized.matches(".*[QRNBqrnb]$") && normalized.length() == 5);
    }

    /**
     * Обработка хода превращения
     */
    private Move handlePromotionMove(String notation) {
        try {
            String normalized = normalizeNotation(notation);
            char promotionPiece;
            String movePart;

            // Извлекаем информацию о превращении
            if (normalized.contains("=")) {
                String[] parts = normalized.split("=");
                movePart = parts[0];
                promotionPiece = parts[1].charAt(0);
            } else {
                // Без "=", например e7e8q
                movePart = normalized.substring(0, 4);
                promotionPiece = normalized.charAt(4);
            }

            // Извлекаем клетки
            if (movePart.length() >= 4) {
                String fromStr = movePart.substring(0, 2).toUpperCase();
                String toStr = movePart.substring(2, 4).toUpperCase();

                Square from = Square.fromValue(fromStr);
                Square to = Square.fromValue(toStr);
                Side side = board.getSideToMove();

                // Проверяем, что это пешка и ход на последнюю горизонталь
                if (isPawnOnSquare(from, side) && isPromotionSquare(to, side)) {
                    return createPromotionMove(from, to, promotionPiece, side);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка обработки превращения: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Создать ход превращения
     */
    private Move createPromotionMove(Square from, Square to, char promotionChar, Side side) {
        Piece promotionPiece;

        switch (Character.toUpperCase(promotionChar)) {
            case 'Q':
                promotionPiece = side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
                break;
            case 'R':
                promotionPiece = side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
                break;
            case 'B':
                promotionPiece = side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
                break;
            case 'N':
                promotionPiece = side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
                break;
            default:
                throw new IllegalArgumentException("Недопустимая фигура превращения: " + promotionChar);
        }

        return new Move(from, to, promotionPiece);
    }

    /**
     * Проверить, является ли клетка последней горизонталью для данной стороны
     */
    private boolean isPromotionSquare(Square square, Side side) {
        int rank = getRankNumber(square);

        if (side == Side.WHITE) {
            return rank == 8; // Белые пешки превращаются на 8-й горизонтали
        } else {
            return rank == 1; // Чёрные пешки превращаются на 1-й горизонтали
        }
    }

    /**
     * Проверить, находится ли пешка данной стороны на клетке
     */
    private boolean isPawnOnSquare(Square square, Side side) {
        Piece piece = board.getPiece(square);

        if (side == Side.WHITE) {
            return piece == Piece.WHITE_PAWN;
        } else {
            return piece == Piece.BLACK_PAWN;
        }
    }

    /**
     * Получить числовое значение горизонтали (1-8)
     */
    private int getRankNumber(Square square) {
        String squareName = square.toString().toLowerCase();
        char rankChar = squareName.charAt(1);
        return Character.getNumericValue(rankChar);
    }

    /**
     * Получить ход рокировки
     */
    private Move getCastlingMove(boolean kingside) {
        Side sideToMove = board.getSideToMove();

        if (kingside) {
            // Короткая рокировка
            if (sideToMove == Side.WHITE) {
                return new Move(Square.E1, Square.G1);
            } else {
                return new Move(Square.E8, Square.G8);
            }
        } else {
            // Длинная рокировка
            if (sideToMove == Side.WHITE) {
                return new Move(Square.E1, Square.C1);
            } else {
                return new Move(Square.E8, Square.C8);
            }
        }
    }

    /**
     * Разбор SAN нотации
     */
    private Move parseSanNotation(Matcher matcher) {
        String pieceSymbol = matcher.group(1); // K, Q, R, B, N или null для пешки
        String disambiguator = matcher.group(2); // Уточнение (a, 3, a3)
        boolean capture = "x".equals(matcher.group(3)); // Есть ли взятие
        String targetSquare = matcher.group(4).toLowerCase(); // Куда ходит
        String promotion = matcher.group(5); // =Q, =R и т.д.

        try {
            Square target = Square.fromValue(targetSquare.toUpperCase());
            Side side = board.getSideToMove();

            // Получаем все легальные ходы
            List<Move> legalMoves = board.legalMoves();

            // Фильтруем по целевой клетке
            List<Move> candidates = legalMoves.stream()
                    .filter(m -> m.getTo() == target)
                    .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                return null;
            }

            // Если указан символ фигуры
            if (pieceSymbol != null && !pieceSymbol.isEmpty()) {
                Piece expectedPiece = getPieceForSymbol(pieceSymbol, side);
                candidates = candidates.stream()
                        .filter(m -> board.getPiece(m.getFrom()) == expectedPiece)
                        .collect(Collectors.toList());
            } else {
                // Для пешки
                candidates = candidates.stream()
                        .filter(m -> {
                            Piece p = board.getPiece(m.getFrom());
                            return p == Piece.WHITE_PAWN || p == Piece.BLACK_PAWN;
                        })
                        .collect(Collectors.toList());
            }

            // Уточнение по файлу/ряду
            if (disambiguator != null && !disambiguator.isEmpty()) {
                candidates = filterByDisambiguator(candidates, disambiguator);
            }

            // Обработка превращения
            if (promotion != null && !promotion.isEmpty()) {
                candidates = filterByPromotion(candidates, promotion.charAt(1), side);
            }

            // Если остался один кандидат - возвращаем его
            if (candidates.size() == 1) {
                return candidates.get(0);
            }

            // Если несколько кандидатов, пробуем выбрать по наличию взятия
            if (capture) {
                List<Move> captureMoves = candidates.stream()
                        .filter(m -> !board.getPiece(m.getTo()).equals(Piece.NONE))
                        .collect(Collectors.toList());

                if (captureMoves.size() == 1) {
                    return captureMoves.get(0);
                }
            }

            log.debug("Неоднозначный ход: {} (кандидатов: {})",
                    matcher.group(0), candidates.size());
            return candidates.isEmpty() ? null : candidates.get(0);

        } catch (Exception e) {
            log.error("Ошибка разбора SAN нотации: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Получить фигуру по символу и стороне
     */
    private Piece getPieceForSymbol(String symbol, Side side) {
        String key = side == Side.WHITE ? symbol.toUpperCase() : symbol.toLowerCase();
        return PIECE_MAP.get(key);
    }

    /**
     * Фильтрация по уточнению (файл/ряд)
     */
    private List<Move> filterByDisambiguator(List<Move> moves, String disambiguator) {
        if (disambiguator.length() == 1) {
            char d = disambiguator.charAt(0);
            if (Character.isLetter(d)) {
                // Уточнение по файлу (a-h)
                String file = String.valueOf(d).toUpperCase();
                return moves.stream()
                        .filter(m -> getFileLetter(m.getFrom()) == d)
                        .collect(Collectors.toList());
            } else if (Character.isDigit(d)) {
                // Уточнение по ряду (1-8)
                int rank = Character.getNumericValue(d);
                return moves.stream()
                        .filter(m -> getRankNumber(m.getFrom()) == rank)
                        .collect(Collectors.toList());
            }
        } else if (disambiguator.length() == 2) {
            // Полное уточнение (e2)
            try {
                Square square = Square.fromValue(disambiguator.toUpperCase());
                return moves.stream()
                        .filter(m -> m.getFrom() == square)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                // Игнорируем
            }
        }
        return moves;
    }

    /**
     * Получить букву вертикали (a-h)
     */
    private char getFileLetter(Square square) {
        String squareName = square.toString().toLowerCase();
        return squareName.charAt(0);
    }

    /**
     * Фильтрация по превращению
     */
    private List<Move> filterByPromotion(List<Move> moves, char promotion, Side side) {
        Piece promoPiece = null;
        switch (Character.toUpperCase(promotion)) {
            case 'Q': promoPiece = side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN; break;
            case 'R': promoPiece = side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK; break;
            case 'B': promoPiece = side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP; break;
            case 'N': promoPiece = side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT; break;
        }

        if (promoPiece != null) {
            return moves.stream()
                    .filter(m -> {
                        // Проверяем, является ли ход превращением
                        // В chesslib это можно проверить по наличию пешки на последней горизонтали
                        Piece movingPiece = board.getPiece(m.getFrom());
                        boolean isPawn = movingPiece == Piece.WHITE_PAWN || movingPiece == Piece.BLACK_PAWN;
                        boolean isPromotionSquare = isPromotionSquare(m.getTo(), side);

                        if (isPawn && isPromotionSquare) {
                            // Для превращения chesslib автоматически превращает в ферзя
                            // Если нужна другая фигура, это должно быть указано явно
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        return moves;
    }

    /**
     * Поиск хода среди легальных по строке
     */
    private Move findMoveAmongLegal(String notation) {
        try {
            List<Move> legalMoves = board.legalMoves();

            // Прямое сравнение
            for (Move move : legalMoves) {
                String moveStr = move.toString().toLowerCase();
                if (moveStr.equals(notation.toLowerCase())) {
                    return move;
                }
            }

            // Сравнение без дефисов
            String notationNoDash = notation.replace("-", "");
            for (Move move : legalMoves) {
                String moveStr = move.toString().toLowerCase().replace("-", "");
                if (moveStr.equals(notationNoDash.toLowerCase())) {
                    return move;
                }
            }

            // Попытка разобрать как "from-to"
            if (notation.length() >= 4) {
                try {
                    String from = notation.substring(0, 2).toUpperCase();
                    String to = notation.substring(2, 4).toUpperCase();

                    Square fromSquare = Square.fromValue(from);
                    Square toSquare = Square.fromValue(to);

                    Move testMove = new Move(fromSquare, toSquare);
                    if (board.isMoveLegal(testMove, false)) {
                        return testMove;
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }

        } catch (MoveGeneratorException e) {
            log.error("Ошибка генерации ходов: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Проверка на шах
     */
    public boolean isCheck() {
        try {
            return board.isKingAttacked();
        } catch (Exception e) {
            log.error("Ошибка проверки шаха: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка на мат
     */
    public boolean isCheckmate() {
        try {
            return board.isMated();
        } catch (Exception e) {
            log.error("Ошибка проверки мата: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка на пат
     */
    public boolean isStalemate() {
        try {
            return board.isStaleMate();
        } catch (Exception e) {
            log.error("Ошибка проверки пата: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Проверка на ничью
     */
    public boolean isDraw() {
        try {
            return board.isDraw() ||
                    isInsufficientMaterial() ||
                    isFiftyMoveRule() ||
                    isThreefoldRepetition();
        } catch (Exception e) {
            log.error("Ошибка проверки ничьи: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Правило 50 ходов
     */
    public boolean isFiftyMoveRule() {
        try {
            String[] fenParts = board.getFen().split(" ");
            if (fenParts.length >= 5) {
                int halfMoves = Integer.parseInt(fenParts[4]);
                return halfMoves >= 100; // 50 полных ходов = 100 полуходов
            }
        } catch (Exception e) {
            log.warn("Ошибка проверки правила 50 ходов: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Трёхкратное повторение позиции
     */
    public boolean isThreefoldRepetition() {
        try {
            return board.isRepetition(3);
        } catch (Exception e) {
            log.warn("Ошибка проверки повторения: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Недостаточный материал
     */
    public boolean isInsufficientMaterial() {
        String fen = board.getFen().split(" ")[0];

        // Считаем фигуры
        int whitePieces = 0, blackPieces = 0;
        int whiteKnights = 0, blackKnights = 0;
        int whiteBishops = 0, blackBishops = 0;

        for (char c : fen.toCharArray()) {
            if (Character.isLetter(c)) {
                switch (Character.toLowerCase(c)) {
                    case 'p': return false; // Есть пешки
                    case 'r': return false; // Есть ладьи
                    case 'q': return false; // Есть ферзи
                    case 'k': break; // Короли есть всегда
                    case 'n': // Кони
                        if (Character.isUpperCase(c)) {
                            whiteKnights++;
                            whitePieces++;
                        } else {
                            blackKnights++;
                            blackPieces++;
                        }
                        break;
                    case 'b': // Слоны
                        if (Character.isUpperCase(c)) {
                            whiteBishops++;
                            whitePieces++;
                        } else {
                            blackBishops++;
                            blackPieces++;
                        }
                        break;
                }
            }
        }

        // Только короли
        if (whitePieces == 0 && blackPieces == 0) {
            return true;
        }

        // Король и слон против короля
        if ((whitePieces == 1 && whiteBishops == 1 && blackPieces == 0) ||
                (blackPieces == 1 && blackBishops == 1 && whitePieces == 0)) {
            return true;
        }

        // Король и конь против короля
        if ((whitePieces == 1 && whiteKnights == 1 && blackPieces == 0) ||
                (blackPieces == 1 && blackKnights == 1 && whitePieces == 0)) {
            return true;
        }

        // Король и слон против короля и слона на полях одного цвета
        if (whitePieces == 1 && whiteBishops == 1 &&
                blackPieces == 1 && blackBishops == 1) {
            // Упрощённая проверка
            return true;
        }

        return false;
    }

    /**
     * Получить FEN текущей позиции
     */
    public String getFen() {
        return board.getFen();
    }

    /**
     * Получить сторону, чей сейчас ход
     */
    public String getSideToMove() {
        return board.getSideToMove().toString();
    }

    /**
     * Получить сторону, чей сейчас ход (объект Side)
     */
    public Side getSideToMoveSide() {
        return board.getSideToMove();
    }

    /**
     * Получить текст представления доски
     */
    public String getBoardAsText() {
        return board.toString();
    }

    /**
     * Получить доску в ориентации для игрока
     */
    public String getBoardForPlayer(Color playerColor) {
        String boardText = board.toString();

        if (playerColor == Color.BLACK) {
            // Разделяем на строки и переворачиваем порядок
            String[] rows = boardText.split("\n");
            StringBuilder reversed = new StringBuilder();

            for (int i = rows.length - 1; i >= 0; i--) {
                reversed.append(rows[i]);
                if (i > 0) reversed.append("\n");
            }

            return reversed.toString();
        }

        return boardText;
    }

    /**
     * Получить список легальных ходов в нотации UCI
     */
    public List<String> getLegalMoveNotations() {
        try {
            return board.legalMoves().stream()
                    .map(Move::toString)
                    .collect(Collectors.toList());
        } catch (MoveGeneratorException e) {
            log.error("Ошибка получения легальных ходов: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Получить список легальных ходов в SAN нотации
     */
    public List<String> getLegalMovesSan() {
        try {
            return board.legalMoves().stream()
                    .map(move -> {
                        try {
                            return moveToSan(board,move);
                        } catch (Exception e) {
                            return move.toString();
                        }
                    })
                    .collect(Collectors.toList());
        } catch (MoveGeneratorException e) {
            log.error("Ошибка получения легальных ходов SAN: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    public String moveToSan(Board board, Move move) {
        // Пример простой реализации
        Piece piece = board.getPiece(move.getFrom());
        String pieceSymbol = getPieceSymbol(piece);
        String toSquare = squareToString(move.getTo().ordinal());

        return pieceSymbol + toSquare;
    }

    private String getPieceSymbol(Piece piece) {
        switch (piece) {
            case WHITE_KING:
            case BLACK_KING: return "K";
            case WHITE_QUEEN:
            case BLACK_QUEEN: return "Q";
            case WHITE_ROOK:
            case BLACK_ROOK: return "R";
            case WHITE_BISHOP:
            case BLACK_BISHOP: return "B";
            case WHITE_KNIGHT:
            case BLACK_KNIGHT: return "N";
            default: return ""; // Для пешек символ не указывается
        }
    }

    private String squareToString(int square) {
        int file = square % 8; // a-h
        int rank = square / 8; // 1-8
        char fileChar = (char) ('a' + file);
        return "" + fileChar + (rank + 1);
    }

    /**
     * Получить список легальных ходов (объекты)
     */
    public List<Move> getLegalMoves() {
        try {
            return board.legalMoves();
        } catch (MoveGeneratorException e) {
            log.error("Ошибка получения легальных ходов: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Сбросить доску к начальной позиции
     */
    public void reset() {
        board.loadFromFen(STARTING_FEN);
    }

    /**
     * Загрузить позицию из FEN
     */
    public boolean loadFromFen(String fen) {
        try {
            board.loadFromFen(fen);
            return true;
        } catch (Exception e) {
            log.error("Ошибка загрузки FEN '{}': {}", fen, e.getMessage());
            return false;
        }
    }

    /**
     * Получить PGN игры
     */
    public String getPgn() {
        try {
            return board.getHistory().toString();
        } catch (Exception e) {
            log.error("Ошибка получения PGN: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Отменить последний ход
     */
    public boolean undoMove() {
        try {
            board.undoMove();
            return true;
        } catch (Exception e) {
            log.error("Ошибка отмены хода: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получить оценку позиции (простая эвристика)
     */
    public int evaluatePosition() {
        int score = 0;

        // Простая материальная оценка
         Map<Piece, Integer> pieceValues = new HashMap<>();

         {
            pieceValues.put(Piece.WHITE_PAWN, 1);
            pieceValues.put(Piece.BLACK_PAWN, -1);
            pieceValues.put(Piece.WHITE_KNIGHT, 3);
            pieceValues.put(Piece.BLACK_KNIGHT, -3);
            pieceValues.put(Piece.WHITE_BISHOP, 3);
            pieceValues.put(Piece.BLACK_BISHOP, -3);
            pieceValues.put(Piece.WHITE_ROOK, 5);
            pieceValues.put(Piece.BLACK_ROOK, -5);
            pieceValues.put(Piece.WHITE_QUEEN, 9);
            pieceValues.put(Piece.BLACK_QUEEN, -9);
            pieceValues.put(Piece.WHITE_KING, 0);
            pieceValues.put(Piece.BLACK_KING, 0); // Короли не считаем
        }
        // Проходим по всем клеткам
        for (Square square : Square.values()) {
            Piece piece = board.getPiece(square);
            Integer value = pieceValues.get(piece);
            if (value != null) {
                score += value;
            }
        }

        return score;
    }

    /**
     * Проверить, находится ли игра в прогрессе
     */
    public boolean isGameActive() {
        return !isCheckmate() && !isStalemate() && !isDraw();
    }

    /**
     * Получить результат игры
     */
    public String getGameResult() {
        if (isCheckmate()) {
            return board.getSideToMove() == Side.WHITE ? "0-1" : "1-0";
        } else if (isStalemate() || isDraw()) {
            return "1/2-1/2";
        }
        return "*"; // Игра продолжается
    }

    /**
     * Проверить, является ли ход взятием
     */
    public boolean isCapture(String notation) {
        try {
            Move move = interpretNotation(normalizeNotation(notation));
            if (move == null) return false;

            Piece targetPiece = board.getPiece(move.getTo());
            return !targetPiece.equals(Piece.NONE);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Получить текущую позицию в формате для отображения
     */
    public String getPositionForDisplay() {
        String boardText = board.toString();
        String[] rows = boardText.split("\n");

        StringBuilder display = new StringBuilder();
        display.append("  a b c d e f g h\n");

        for (int i = 0; i < rows.length; i++) {
            int rank = 8 - i;
            display.append(rank).append(" ");
            display.append(rows[i]);
            display.append(" ").append(rank).append("\n");
        }

        display.append("  a b c d e f g h\n");
        display.append("\nХод: ").append(getSideToMove());

        if (isCheck()) display.append(" (ШАХ!)");
        if (isCheckmate()) display.append(" (МАТ!)");
        if (isStalemate()) display.append(" (ПАТ!)");
        if (isDraw()) display.append(" (НИЧЬЯ)");

        return display.toString();
    }

    /**
     * Получить количество ходов
     */
    public int getMoveCount() {
        return board.getMoveCounter();
    }

    /**
     * Получить историю ходов
     */
//    public List<String> getMoveHistory() {
//        try {
//            MoveList moves = board.getMoveHistory();
//            List<String> result = new ArrayList<>();
//
//            // Проходим по всем ходам
//            for (int i = 0; i < moves.size(); i++) {
//                Move move = moves.get(i);
//                result.add(move.toString());
//            }
//
//            return result;
//        } catch (Exception e) {
//            return Collections.emptyList();
//        }
//    }

    /**
     * Проверить, является ли ход шахом.
     */
    public boolean isMoveCheck(String notation) {
        try {
            // Создаем копию доски для проверки
            Board testBoard = new Board();
            testBoard.loadFromFen(board.getFen());

            Move move = interpretNotation(normalizeNotation(notation));
            if (move != null && testBoard.isMoveLegal(move, true)) {
                testBoard.doMove(move);
                return testBoard.isKingAttacked();
            }
        } catch (Exception e) {
            log.error("Ошибка проверки шаха для хода {}: {}", notation, e.getMessage());
        }
        return false;
    }

    /**
     * Получить все возможные превращения для пешки
     */
    public List<String> getPromotionOptions(Square from, Square to) {
        List<String> options = new ArrayList<>();
        Side side = board.getSideToMove();

        if (isPawnOnSquare(from, side) && isPromotionSquare(to, side)) {
            options.add("Q");
            options.add("R");
            options.add("B");
            options.add("N");
        }

        return options;
    }

    /**
     * Проверить, возможна ли рокировка
     */
    public boolean canCastle(boolean kingside) {
        Side side = board.getSideToMove();
        String fen = board.getFen();
        String castlingRights = fen.split(" ")[2];

        if (side == Side.WHITE) {
            return kingside ? castlingRights.contains("K") : castlingRights.contains("Q");
        } else {
            return kingside ? castlingRights.contains("k") : castlingRights.contains("q");
        }
    }

    /**
     * Проверить, является ли ход рокировкой
     */
    public boolean isCastlingMove(String notation) {
        String normalized = normalizeNotation(notation);
        return normalized.equalsIgnoreCase("O-O") ||
                normalized.equalsIgnoreCase("O-O-O") ||
                normalized.equals("0-0") ||
                normalized.equals("0-0-0");
    }

    /**
     * Получить клетку короля для данной стороны
     */
    public Square getKingSquare(Side side) {
        Piece kingPiece = side == Side.WHITE ? Piece.WHITE_KING : Piece.BLACK_KING;

        for (Square square : Square.values()) {
            if (board.getPiece(square) == kingPiece) {
                return square;
            }
        }

        return null;
    }

    /**
     * Получить все фигуры данной стороны
     */
    public List<Square> getPiecesForSide(Side side) {
        List<Square> pieces = new ArrayList<>();

        for (Square square : Square.values()) {
            Piece piece = board.getPiece(square);
            if (piece != Piece.NONE) {
                boolean isWhite = piece.name().startsWith("WHITE_");
                if ((side == Side.WHITE && isWhite) || (side == Side.BLACK && !isWhite)) {
                    pieces.add(square);
                }
            }
        }

        return pieces;
    }
}