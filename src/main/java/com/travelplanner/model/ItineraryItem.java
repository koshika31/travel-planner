package com.travelplanner.model;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_items")
public class ItineraryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;
    @Column(nullable = false)
    private int dayNumber;
    private LocalTime startTime;
    @Column(nullable = false, length = 120)
    private String activity;
    @Column(length = 120)
    private String location;
    @Column(nullable = false, length = 40)
    private String category;
    private double cost;
    @Column(length = 500)
    private String notes;

    protected ItineraryItem() {}
    public ItineraryItem(int dayNumber, LocalTime startTime, String activity, String location,
                         String category, double cost, String notes) {
        this.dayNumber = dayNumber;
        this.startTime = startTime;
        this.activity = activity;
        this.location = location;
        this.category = category;
        this.cost = cost;
        this.notes = notes;
    }
    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public void setTrip(Trip trip) { this.trip = trip; }
    public int getDayNumber() { return dayNumber; }
    public LocalTime getStartTime() { return startTime; }
    public String getActivity() { return activity; }
    public String getLocation() { return location; }
    public String getCategory() { return category; }
    public double getCost() { return cost; }
    public String getNotes() { return notes; }
}
