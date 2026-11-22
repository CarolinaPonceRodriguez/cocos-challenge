package org.cocos.portfolio.infrastructure.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.cocos.portfolio.application.PortfolioService;
import org.cocos.portfolio.infrastructure.dto.response.PortfolioResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio")
public class PortfolioController {

    private final PortfolioService service;

    @GetMapping
    @Operation(summary = "Get the portfolio of a user")
    @ApiResponse(responseCode = "200", description = "Portfolio of the user.")
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    public PortfolioResponse get(
            @PathVariable Integer userId) {
        return service.get(userId);
    }
}
