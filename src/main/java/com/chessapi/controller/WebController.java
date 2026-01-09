package com.chessapi.controller;

import com.chessapi.dto.CreateGameRequest;
import com.chessapi.dto.GameResponse;
import com.chessapi.dto.MoveRequest;
import com.chessapi.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/web")
@RequiredArgsConstructor
public class WebController {

    private final GameService gameService;


    // GET - отображение формы создания игры
    @GetMapping("/new-game")
    public String newGame(Model model) {
        model.addAttribute("title", "Новая игра - Chess API");
        return "new-game";
    }

    // POST - обработка создания игры
    @PostMapping("/create-game")
    public String createGameFromWeb(
            @RequestParam String playerName,
            @RequestParam(required = false, defaultValue = "RANDOM") String color,
            @RequestParam(required = false, defaultValue = "1800") int timeControl,
            RedirectAttributes redirectAttributes) {

        try {
            log.info("Creating game from web: playerName={}", playerName);

            // Генерируем playerId на основе имени (временное решение)
            // В реальном приложении здесь была бы аутентификация
            Long playerId = (long) Math.abs(playerName.hashCode());

            // Создаем запрос как в REST API
            CreateGameRequest request = new CreateGameRequest(playerId, playerName);


            // Вызываем существующий сервис
            GameResponse response = gameService.createGame(request);

            if (response.isSuccess()) {
                // Перенаправляем на страницу игры
                return "redirect:/web/game/" + response.getGameId();
            } else {
                // Ошибка создания игры
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Ошибка: " + response.getMessage());
                return "redirect:/web/new-game";
            }

        } catch (Exception e) {
            log.error("Error creating game from web", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка сервера: " + e.getMessage());
            return "redirect:/web/new-game";
        }
    }

    // GET - страница игры
    @GetMapping("/game/{gameId}")
    public String gameBoard(@PathVariable String gameId, Model model) {
        try {
            log.info("Загрузка страницы игры: {}", gameId);

            // Получаем данные ПРЯМО из GameService
            GameResponse gameResponse = gameService.getGameForWeb(gameId);

            model.addAttribute("gameId", gameId);
            model.addAttribute("gameResponse", gameResponse);

            return "game-board";

        } catch (Exception e) {
            log.error("Ошибка загрузки: {}", e.getMessage(), e);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("gameId", gameId);
            return "error";
        }
    }

    private GameResponse getGameResponse(String publicId) {
        // Создаем заглушку MoveRequest с playerId = 0 (наблюдатель)
        MoveRequest request = new MoveRequest();
        request.setPlayerId(0L);
        request.setNotation(""); // Пустой ход, просто получить состояние

        // Используем существующий метод или создаем новый
        return gameService.getGameState(publicId, request);
    }

    // GET - главная страница
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Chess API - Online Chess Platform");
        model.addAttribute("activeGames", gameService.getWaitingGames());//список игр ожидающих
        return "index";
    }

    // GET - список игр
    @GetMapping("/games")
    public String gamesList(Model model) {
        model.addAttribute("title", "Список игр - Chess API");
        model.addAttribute("games", gameService.getWaitingGames());//список игр ожидающих
        return "games-list";
    }

    // Вспомогательные методы

    private String getServerHost() {
        try {
            // В продакшене нужно использовать реальный домен
            return java.net.InetAddress.getLocalHost().getHostAddress() + ":8080";
        } catch (Exception e) {
            return "localhost:8080";
        }
    }
}