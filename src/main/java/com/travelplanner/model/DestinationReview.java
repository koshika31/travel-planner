package com.travelplanner.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "destination_reviews")
public class DestinationReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String destination;
    @Column(nullable = false, length = 80)
    private String author;
    @Column(nullable = false)
    private int rating;
    @Column(nullable = false, length = 1000)
    private String comment;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected DestinationReview() {}
    public DestinationReview(String destination, String author, int rating, String comment) {
        this.destination = destination;
        this.author = author;
        this.rating = rating;
        this.comment = comment;
    }
    public Long getId() { return id; }
    public String getDestination() { return destination; }
    public String getAuthor() { return author; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
