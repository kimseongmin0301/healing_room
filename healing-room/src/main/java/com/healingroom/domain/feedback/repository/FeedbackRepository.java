package com.healingroom.domain.feedback.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.feedback.entity.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findTop200ByOrderByIdDesc();
}
