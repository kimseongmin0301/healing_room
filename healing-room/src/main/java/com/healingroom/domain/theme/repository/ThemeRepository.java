package com.healingroom.domain.theme.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.theme.entity.Theme;

public interface ThemeRepository extends JpaRepository<Theme, String> {

    List<Theme> findAllByOrderBySortOrderAsc();
}
