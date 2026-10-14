package com.healingroom.domain.theme.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.theme.entity.ThemeTrack;

public interface ThemeTrackRepository extends JpaRepository<ThemeTrack, Long> {

    List<ThemeTrack> findByThemeIdOrderBySortOrderAsc(String themeId);
}
