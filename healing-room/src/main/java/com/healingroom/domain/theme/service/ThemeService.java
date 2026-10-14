package com.healingroom.domain.theme.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healingroom.domain.theme.dto.AmbientResponse;
import com.healingroom.domain.theme.dto.ThemeResponse;
import com.healingroom.domain.theme.entity.ThemeTrack;
import com.healingroom.domain.theme.repository.ThemeAmbientRepository;
import com.healingroom.domain.theme.repository.ThemeRepository;
import com.healingroom.domain.theme.repository.ThemeTrackRepository;

@Service
@Transactional(readOnly = true)
public class ThemeService {

    private final ThemeRepository themes;
    private final ThemeAmbientRepository ambients;
    private final ThemeTrackRepository tracks;

    public ThemeService(ThemeRepository themes, ThemeAmbientRepository ambients, ThemeTrackRepository tracks) {
        this.themes = themes;
        this.ambients = ambients;
        this.tracks = tracks;
    }

    public List<ThemeResponse> findAll() {
        return themes.findAllByOrderBySortOrderAsc().stream().map(ThemeResponse::from).toList();
    }

    public Optional<ThemeResponse> find(String themeId) {
        return themes.findById(themeId).map(ThemeResponse::from);
    }

    public boolean exists(String themeId) {
        return themes.existsById(themeId);
    }

    public List<AmbientResponse> ambients(String themeId) {
        return ambients.findByThemeIdOrderBySortOrderAsc(themeId).stream().map(AmbientResponse::from).toList();
    }

    /** 대기열이 비었을 때 돌아가며 트는 기본 음악 */
    public List<ThemeTrack> defaultTracks(String themeId) {
        return tracks.findByThemeIdOrderBySortOrderAsc(themeId);
    }
}
