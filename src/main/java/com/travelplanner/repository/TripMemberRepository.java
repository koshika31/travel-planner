package com.travelplanner.repository;

import com.travelplanner.model.InvitationStatus;
import com.travelplanner.model.TripMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {
    @EntityGraph(attributePaths = {"trip", "trip.owner", "user"})
    List<TripMember> findByUserIdAndStatusOrderByInvitedAtDesc(Long userId, InvitationStatus status);
    @EntityGraph(attributePaths = "user")
    List<TripMember> findByTripIdOrderByInvitedAtAsc(Long tripId);
    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);
    boolean existsByTripIdAndUserIdAndStatus(Long tripId, Long userId, InvitationStatus status);
}
