package com.healingroom.domain.theme.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.theme.entity.ThemeAmbient;

public interface ThemeAmbientRepository extends JpaRepository<ThemeAmbient, Long> {

    List<ThemeAmbient> findByThemeIdOrderBySortOrderAsc(String themeId);
}
