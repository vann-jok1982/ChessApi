package com.chessapi.service;

import com.chessapi.dto.*;
import com.chessapi.model.*;
import com.chessapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GameService {

    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final MoveRepository moveRepository;

    // Стандартные FEN позиции
    private static final String STANDARD_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * 🆕 СОЗДАТЬ НОВУЮ ИГРУ
     */
    public GameResponse createGame(CreateGameRequest request) {
        try {
            log.info("Создание игры для игрока {}", request.getPlayerId());

            // 1. Находим или создаём игрока
            Player player = findOrCreatePlayer(request.getPlayerId(), request.getPlayerName());

            // 2. Проверяем нет ли активных игр
            List<Game> activeGames = gameRepository.findActiveGamesByPlayer(player.getId());
            if (!activeGames.isEmpty()) {
                log.warn("У игрока {} уже есть активная игра {}",
                        player.getId(), activeGames.get(0).getPublicId());
                return GameResponse.error("У вас уже есть активная игра: " +
                        activeGames.get(0).getPublicId());
            }

            // 3. Создаём шахматный движок для получения начального FEN
            ChessEngine engine = new ChessEngine();

            // 4. Создаём игру в БД
            Game game = Game.builder()
                    .whitePlayer(player)
                    .status(Game.GameStatus.WAITING)
                    .currentTurn("WHITE")
                    .currentFen(engine.getFen()) // Сохраняем начальный FEN
                    .build();

            game = gameRepository.save(game);

            log.info("✅ Игра создана: {} игроком {}", game.getPublicId(), player.getUsername());

            return buildGameResponse(game, player.getId(), engine);

        } catch (Exception e) {
            log.error("❌ Ошибка создания игры: {}", e.getMessage(), e);
            return GameResponse.error("Ошибка создания игры: " + e.getMessage());
        }
    }

    /**
     * 🤝 ПРИСОЕДИНИТЬСЯ К ИГРЕ
     */
    public GameResponse joinGame(String publicId, JoinGameRequest request) {
        try {
            log.info("Присоединение к игре {} игроком {}", publicId, request.getPlayerId());

            // 1. Находим игру
            Game game = gameRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new IllegalArgumentException("Игра не найдена: " + publicId));

            // 2. Проверяем статус
            if (game.getStatus() != Game.GameStatus.WAITING) {
                return GameResponse.error("Игра уже начата или завершена");
            }

            // 3. Находим или создаём игрока
            Player player = findOrCreatePlayer(request.getPlayerId(), request.getPlayerName());

            // 4. Проверяем что игрок не присоединяется к своей же игре
            if (game.getWhitePlayer().getId().equals(player.getId())) {
                return GameResponse.error("Вы уже создали эту игру");
            }

            // 5. Создаём движок для получения начального FEN
            ChessEngine engine = new ChessEngine();

            // 6. Обновляем игру
            game.setBlackPlayer(player);
            game.setStatus(Game.GameStatus.ACTIVE);
            game.setCurrentFen(engine.getFen()); // Сохраняем начальную позицию
            game = gameRepository.save(game);

            log.info("✅ Игрок {} присоединился к игре {}", player.getUsername(), publicId);

            return buildGameResponse(game, player.getId(), engine);

        } catch (Exception e) {
            log.error("❌ Ошибка присоединения к игре: {}", e.getMessage(), e);
            return GameResponse.error("Ошибка присоединения: " + e.getMessage());
        }
    }

    /**
     * ♟️ СДЕЛАТЬ ХОД
     */
    public GameResponse makeMove(String publicId, MoveRequest request) {
        try {
            log.info("Ход в игре {}: {} от игрока {}",
                    publicId, request.getNotation(), request.getPlayerId());

            // 1. Находим игру
            Game game = gameRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new IllegalArgumentException("Игра не найдена: " + publicId));

            // 2. Проверяем статус
            if (game.getStatus() != Game.GameStatus.ACTIVE) {
                return GameResponse.error("Игра не активна. Статус: " + game.getStatus());
            }

            // 3. Находим игрока
            Player player = playerRepository.findByTelegramId(String.valueOf(request.getPlayerId()))
                    .orElseThrow(() -> new IllegalArgumentException("Игрок не найден: " + request.getPlayerId()));

            // 4. Проверяем что игрок участвует в игре
            if (!game.isPlayerInGame(player.getId())) {
                return GameResponse.error("Вы не участвуете в этой игре");
            }

            // 5. СОЗДАЁМ НОВЫЙ ДВИЖОК ИЗ ТЕКУЩЕГО FEN
            ChessEngine engine = createEngineFromFen(game.getCurrentFen());

            // 6. Проверяем очередь хода
            Color playerColor = game.getPlayerColor(player.getId());
            Color currentTurn = Color.fromString(engine.getSideToMove());

            if (playerColor != currentTurn) {
                return GameResponse.error("Не ваша очередь. Сейчас ходят: " + currentTurn);
            }

            // 7. Пробуем сделать ход
            boolean moveSuccess = engine.makeMove(request.getNotation());
            if (!moveSuccess) {
                return GameResponse.error("Недопустимый ход: " + request.getNotation());
            }

            // 8. Сохраняем ход в БД
            int moveNumber = moveRepository.countByGame(game) + 1;
            Move move = Move.builder()
                    .game(game)
                    .moveNumber(moveNumber)
                    .notation(request.getNotation())
                    .fenAfter(engine.getFen())
                    .build();

            moveRepository.save(move);

            // 9. Обновляем игру в БД
            game.setCurrentFen(engine.getFen());
            game.setCurrentTurn(engine.getSideToMove());

            // 10. Проверяем окончание игры
            updateGameStatus(game, engine);
            game = gameRepository.save(game);

            log.info("✅ Ход {} (№{}) выполнен в игре {}",
                    request.getNotation(), moveNumber, publicId);

            return buildGameResponse(game, player.getId(), engine);

        } catch (Exception e) {
            log.error("❌ Ошибка выполнения хода: {}", e.getMessage(), e);
            return GameResponse.error("Ошибка хода: " + e.getMessage());
        }
    }

    /**
     * 📊 ПОЛУЧИТЬ ИНФОРМАЦИЮ ОБ ИГРЕ
     */
    public GameResponse getGame(String publicId, Long playerId) {
        try {
            log.debug("Получение игры {} для игрока {}", publicId, playerId);

            // 1. Находим игру
            Game game = gameRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new IllegalArgumentException("Игра не найдена: " + publicId));

            // 2. СОЗДАЁМ НОВЫЙ ДВИЖОК ИЗ FEN В БД
            ChessEngine engine = createEngineFromFen(game.getCurrentFen());

            // 3. Строим ответ
            return buildGameResponse(game, playerId, engine);

        } catch (Exception e) {
            log.error("❌ Ошибка получения игры: {}", e.getMessage(), e);
            return GameResponse.error("Ошибка получения игры: " + e.getMessage());
        }
    }

    /**
     * 📋 ПОЛУЧИТЬ СПИСОК ОЖИДАЮЩИХ ИГР
     */
    public List<GameInfoResponse> getWaitingGames() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
        List<Game> waitingGames = gameRepository.findWaitingGames(cutoffTime);

        return waitingGames.stream()
                .map(game -> GameInfoResponse.builder()
                        .gameId(game.getPublicId())
                        .whitePlayerName(game.getWhitePlayer().getUsername())
                        .createdAt(game.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 🏁 ОБНОВИТЬ СТАТУС ИГРЫ (мат, пат, ничья)
     */
    private void updateGameStatus(Game game, ChessEngine engine) {
        if (engine.isCheckmate()) {
            // Мат: кто сейчас должен ходить - тот и проиграл
            game.setStatus(game.getCurrentTurn().equals("WHITE") ?
                    Game.GameStatus.BLACK_WIN : Game.GameStatus.WHITE_WIN);
            updatePlayerRatings(game);

        } else if (engine.isStalemate()) {
            // Пат
            game.setStatus(Game.GameStatus.DRAW);
            updatePlayerRatings(game);

        } else if (engine.isDraw()) {
            // Другие виды ничьи
            game.setStatus(Game.GameStatus.DRAW);
            updatePlayerRatings(game);

        } else if (engine.isCheck()) {
            // Просто шах - статус остаётся ACTIVE
            log.debug("Шах в игре {}", game.getPublicId());
        }
    }

    /**
     * 📈 ОБНОВИТЬ РЕЙТИНГИ ИГРОКОВ ПОСЛЕ ОКОНЧАНИЯ ИГРЫ
     */
    /**
     * 📈 ОБНОВИТЬ РЕЙТИНГИ ИГРОКОВ ПОСЛЕ ОКОНЧАНИЯ ИГРЫ
     */
    private void updatePlayerRatings(Game game) {
        if (game.getWhitePlayer() == null || game.getBlackPlayer() == null) {
            log.warn("Не удалось обновить рейтинги: отсутствует один из игроков");
            return;
        }

        Player white = game.getWhitePlayer();
        Player black = game.getBlackPlayer();

        // Инициализируем поля если они null
        if (white.getGamesPlayed() == null) white.setGamesPlayed(0);
        if (white.getGamesWon() == null) white.setGamesWon(0);
        if (white.getGamesLost() == null) white.setGamesLost(0);
        if (white.getGamesDrawn() == null) white.setGamesDrawn(0);
        if (white.getRating() == null) white.setRating(1200);

        if (black.getGamesPlayed() == null) black.setGamesPlayed(0);
        if (black.getGamesWon() == null) black.setGamesWon(0);
        if (black.getGamesLost() == null) black.setGamesLost(0);
        if (black.getGamesDrawn() == null) black.setGamesDrawn(0);
        if (black.getRating() == null) black.setRating(1200);

        switch (game.getStatus()) {
            case WHITE_WIN:
                white.addWin();
                black.addLoss();
                log.info("Рейтинги обновлены: {} (+20) -> {}, {} (-20) -> {}",
                        white.getUsername(), white.getRating(),
                        black.getUsername(), black.getRating());
                break;
            case BLACK_WIN:
                black.addWin();
                white.addLoss();
                log.info("Рейтинги обновлены: {} (-20) -> {}, {} (+20) -> {}",
                        white.getUsername(), white.getRating(),
                        black.getUsername(), black.getRating());
                break;
            case DRAW:
                white.addDraw();
                black.addDraw();
                log.info("Рейтинги обновлены: {} (+5) -> {}, {} (+5) -> {}",
                        white.getUsername(), white.getRating(),
                        black.getUsername(), black.getRating());
                break;
            default:
                return;
        }

        try {
            playerRepository.save(white);
            playerRepository.save(black);
            log.info("Рейтинги сохранены в БД");
        } catch (Exception e) {
            log.error("Ошибка сохранения рейтингов: {}", e.getMessage());
        }
    }
    /**
     * 👤 НАЙТИ ИЛИ СОЗДАТЬ ИГРОКА
     */
    private Player findOrCreatePlayer(Long telegramId, String username) {
        return playerRepository.findByTelegramId(telegramId.toString())
                .orElseGet(() -> {
                    Player newPlayer = Player.builder()
                            .telegramId(telegramId.toString())
                            .username(username)
                            .firstName(username)
                            .rating(1200)
                            .build();
                    return playerRepository.save(newPlayer);
                });
    }

    /**
     * 🛠️ СОЗДАТЬ ДВИЖОК ИЗ FEN ПОЗИЦИИ
     */
    private ChessEngine createEngineFromFen(String fen) {
        try {
            ChessEngine engine = new ChessEngine();

            if (fen != null && !fen.trim().isEmpty()) {
                engine.getBoard().loadFromFen(fen);
            } else {
                // Начальная позиция по умолчанию
                engine.getBoard().loadFromFen(STANDARD_START_FEN);
            }

            return engine;

        } catch (Exception e) {
            log.error("Ошибка создания движка из FEN '{}': {}", fen, e.getMessage());
            // Возвращаем движок с начальной позицией
            return new ChessEngine();
        }
    }

    /**
     * 🏗️ ПОСТРОИТЬ ОТВЕТ ДЛЯ API
     */
    private GameResponse buildGameResponse(Game game, Long requestingPlayerId, ChessEngine engine) {
        // Определяем цвет запрашивающего игрока
        Color playerColor = game.getPlayerColor(requestingPlayerId);
        boolean isPlayerInGame = playerColor != null;

        // Определяем статус игры
        String gameStatus = "ACTIVE";
        String message = "Ход ожидается";

        if (engine != null) {
            if (engine.isCheckmate()) {
                gameStatus = "CHECKMATE";
                Color winner = game.getCurrentTurn().equals("WHITE") ? Color.BLACK : Color.WHITE;
                message = "МАТ! Победили " + (winner == Color.WHITE ? "белые" : "чёрные");
            } else if (engine.isStalemate()) {
                gameStatus = "STALEMATE";
                message = "ПАТ! Ничья";
            } else if (engine.isDraw()) {
                gameStatus = "DRAW";
                message = "Ничья";
            } else if (engine.isCheck()) {
                gameStatus = "CHECK";
                message = "ШАХ!";
            }
        }

        // Получаем доску в правильной ориентации
        String board = "Доска не доступна";
        if (engine != null && isPlayerInGame) {
            board = engine.getBoardForPlayer(playerColor);
        } else if (engine != null) {
            board = engine.getBoardAsText();
        }

        // Получаем легальные ходы (только если это ход игрока)
        List<String> legalMoves = new ArrayList<>();
        if (engine != null && isPlayerInGame &&
                playerColor.toString().equals(engine.getSideToMove())) {
            legalMoves = engine.getLegalMoveNotations();
        }

        // Строим ответ
        GameResponse.GameResponseBuilder responseBuilder = GameResponse.builder()
                .success(true)
                .gameId(game.getPublicId())
                .status(gameStatus)
                .message(message)
                .whitePlayer(buildPlayerInfo(game.getWhitePlayer()))
                .currentTurn(engine != null ? engine.getSideToMove() : game.getCurrentTurn())
                .board(board)
                .playerColor(isPlayerInGame ? playerColor.toString() : "OBSERVER");

        // Добавляем легальные ходы
        if (legalMoves != null && !legalMoves.isEmpty()) {
            responseBuilder.legalMoves(legalMoves);
        }

        // Добавляем чёрного игрока, если есть
        if (game.getBlackPlayer() != null) {
            responseBuilder.blackPlayer(buildPlayerInfo(game.getBlackPlayer()));
        }

        // Добавляем дополнительную информацию
        responseBuilder.additionalInfo(buildAdditionalInfo(game, engine));

        return responseBuilder.build();
    }

    /**
     * 👤 ПОСТРОИТЬ ИНФОРМАЦИЮ ОБ ИГРОКЕ
     */
    private GameResponse.PlayerInfo buildPlayerInfo(Player player) {
        if (player == null) {
            return null;
        }

        // Определяем цвет игрока
        String color = "UNKNOWN";
        if (player.getWhiteGames() != null && !player.getWhiteGames().isEmpty()) {
            color = "WHITE";
        } else if (player.getBlackGames() != null && !player.getBlackGames().isEmpty()) {
            color = "BLACK";
        }

        return GameResponse.PlayerInfo.builder()
                .id(player.getId())
                .name(player.getUsername())
                .color(color)
                .rating(player.getRating())
                .build();
    }

    /**
     * ℹ️ ПОСТРОИТЬ ДОПОЛНИТЕЛЬНУЮ ИНФОРМАЦИЮ
     */
    private Map<String, String> buildAdditionalInfo(Game game, ChessEngine engine) {
        Map<String, String> info = new HashMap<>();

        if (game != null) {
            info.put("createdAt", game.getCreatedAt().toString());
            info.put("movesCount", String.valueOf(moveRepository.countByGame(game)));
            info.put("gameStatus", game.getStatus().toString());
        }

        if (engine != null) {
            info.put("fen", engine.getFen());
            info.put("legalMovesCount", String.valueOf(engine.getLegalMoves().size()));
            info.put("sideToMove", engine.getSideToMove());
        }

        return info;
    }

    /**
     * 🧹 ОЧИСТИТЬ СТАРЫЕ ИГРЫ (вызывать по расписанию)
     */
    @Transactional
    public int cleanupOldGames() {
        log.info("🚀 Начало очистки старых игр");

        int totalCleaned = 0;

        try {
            // 1. Очистка старых ожидающих игр (старше 7 дней)
            LocalDateTime waitingCutoff = LocalDateTime.now().minusDays(7);
            List<Game> oldWaitingGames = gameRepository.findOldWaitingGames(waitingCutoff);

            log.info("Найдено {} старых ожидающих игр", oldWaitingGames.size());

            for (Game game : oldWaitingGames) {
                try {
                    // Удаляем связанные ходы
                    moveRepository.deleteByGame(game);

                    // Удаляем игру
                    gameRepository.delete(game);

                    totalCleaned++;
                    log.debug("Удалена ожидающая игра: {} (создана: {})",
                            game.getPublicId(), game.getCreatedAt());

                } catch (Exception e) {
                    log.error("Ошибка при удалении игры {}: {}", game.getPublicId(), e.getMessage());
                }
            }

            // 2. Обработка старых завершённых игр (старше 30 дней)
            LocalDateTime finishedCutoff = LocalDateTime.now().minusDays(30);
            List<Game> oldFinishedGames = gameRepository.findOldFinishedGames(finishedCutoff);

            log.info("Найдено {} старых завершённых игр", oldFinishedGames.size());

            for (Game game : oldFinishedGames) {
                try {
                    // Архивируем вместо удаления
                    if (!game.isArchived()) {
                        game.archive();
                        gameRepository.save(game);

                        totalCleaned++;
                        log.debug("Архивирована завершённая игра: {} (статус: {})",
                                game.getPublicId(), game.getStatus());
                    }

                } catch (Exception e) {
                    log.error("Ошибка при архивации игры {}: {}", game.getPublicId(), e.getMessage());
                }
            }

            log.info("✅ Очистка завершена. Обработано игр: {}", totalCleaned);
            return totalCleaned;

        } catch (Exception e) {
            log.error("❌ Критическая ошибка при очистке игр: {}", e.getMessage(), e);
            return totalCleaned;
        }
    }

    /**
     * 🧹 ОЧИСТКА ОЧЕНЬ СТАРЫХ ИГР (можно запускать чаще)
     */
    @Transactional
    public int cleanupVeryOldGames() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(60);

            log.info("Очистка игр старше 60 дней...");

            // Находим очень старые завершённые игры
            List<Game> veryOldGames = gameRepository.findByUpdatedAtBefore(cutoffDate);

            int deletedCount = 0;
            for (Game game : veryOldGames) {
                // Удаляем только если игра завершена
                if (game.getStatus() != Game.GameStatus.ACTIVE &&
                        game.getStatus() != Game.GameStatus.WAITING) {

                    try {
                        log.debug("Удаление очень старой игры: {} (статус: {}, создана: {})",
                                game.getPublicId(), game.getStatus(), game.getCreatedAt());

                        // Сначала удаляем связанные ходы
                        moveRepository.deleteByGame(game);

                        // Затем удаляем игру
                        gameRepository.delete(game);
                        deletedCount++;

                    } catch (Exception e) {
                        log.error("Ошибка при удалении старой игры {}: {}",
                                game.getPublicId(), e.getMessage());
                    }
                }
            }

            log.info("🧹 Очистка очень старых игр завершена: удалено {} игр", deletedCount);
            return deletedCount;

        } catch (Exception e) {
            log.error("❌ Ошибка при очистке очень старых игр: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 🤝 ПРЕДЛОЖИТЬ НИЧЬЮ
     */
    public GameResponse offerDraw(String publicId, Long playerId) {
        try {
            Game game = gameRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new IllegalArgumentException("Игра не найдена"));

            if (game.getStatus() != Game.GameStatus.ACTIVE) {
                return GameResponse.error("Игра не активна");
            }

            // Проверяем что игрок участвует
            if (!game.isPlayerInGame(playerId)) {
                return GameResponse.error("Вы не участвуете в этой игре");
            }

            // Устанавливаем флаг предложения ничьи
            game.setDrawOfferedBy(playerId);
            gameRepository.save(game);

            return GameResponse.builder()
                    .success(true)
                    .gameId(publicId)
                    .message("Ничья предложена. Ожидаем ответ соперника")
                    .build();

        } catch (Exception e) {
            log.error("Ошибка предложения ничьи: {}", e.getMessage());
            return GameResponse.error("Ошибка: " + e.getMessage());
        }
    }

    /**
     * 🤝 ПРИНЯТЬ/ОТКЛОНИТЬ НИЧЬЮ
     */
    public GameResponse respondToDraw(String publicId, Long playerId, boolean accept) {
        try {
            Game game = gameRepository.findByPublicId(publicId)
                    .orElseThrow(() -> new IllegalArgumentException("Игра не найдена"));

            if (game.getDrawOfferedBy() == null) {
                return GameResponse.error("Ничья не была предложена");
            }

            if (game.getDrawOfferedBy().equals(playerId)) {
                return GameResponse.error("Нельзя отвечать на своё предложение");
            }

            if (!game.isPlayerInGame(playerId)) {
                return GameResponse.error("Вы не участвуете в этой игре");
            }

            if (accept) {
                game.setStatus(Game.GameStatus.DRAW);
                updatePlayerRatings(game);
                game.setDrawOfferedBy(null);
                gameRepository.save(game);

                return buildGameResponse(game, playerId, createEngineFromFen(game.getCurrentFen()));

            } else {
                game.setDrawOfferedBy(null);
                gameRepository.save(game);

                return GameResponse.builder()
                        .success(true)
                        .gameId(publicId)
                        .message("Вы отклонили ничью")
                        .build();
            }

        } catch (Exception e) {
            log.error("Ошибка обработки ничьи: {}", e.getMessage());
            return GameResponse.error("Ошибка: " + e.getMessage());
        }
    }
}