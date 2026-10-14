package com.healingroom.domain.theme.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healingroom.domain.theme.dto.ThemeResponse;
import com.healingroom.domain.theme.service.ThemeService;

@RestController
@RequestMapping("/api/themes")
public class ThemeController {

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    /** 방 만들기에서 고를 수 있는 테마 목록 */
    @GetMapping
    public List<ThemeResponse> list() {
        return themeService.findAll();
    }
}
