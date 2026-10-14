package com.healingroom.domain.queue.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.healingroom.domain.queue.entity.QueueVote;

public interface QueueVoteRepository extends JpaRepository<QueueVote, QueueVote.Key> {

    List<QueueVote> findByItemIdIn(Collection<Long> itemIds);

    List<QueueVote> findByItemId(Long itemId);
}
