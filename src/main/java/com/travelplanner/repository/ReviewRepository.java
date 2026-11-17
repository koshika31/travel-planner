package com.travelplanner.repository;

import com.travelplanner.model.DestinationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<DestinationReview, Long> {
    List<DestinationReview> findByDestinationIgnoreCaseOrderByCreatedAtDesc(String destination);
}
