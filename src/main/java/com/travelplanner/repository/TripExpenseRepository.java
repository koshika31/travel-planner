package com.travelplanner.repository;

import com.travelplanner.model.TripExpense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {
    @EntityGraph(attributePaths = {"paidBy", "shares", "shares.user"})
    List<TripExpense> findByTripIdOrderByCreatedAtAsc(Long tripId);
}
