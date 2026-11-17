package com.travelplanner.repository;

import com.travelplanner.model.Trip;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    @EntityGraph(attributePaths = {"itinerary", "owner"})
    List<Trip> findByOwnerIdOrderByStartDateAsc(Long ownerId);
    @EntityGraph(attributePaths = "itinerary")
    Optional<Trip> findWithItineraryById(Long id);
    @EntityGraph(attributePaths = {"itinerary", "owner"})
    Optional<Trip> findByShareToken(String shareToken);
}
