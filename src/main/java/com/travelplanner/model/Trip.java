package com.travelplanner.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String title;
    @Column(nullable = false, length = 100)
    private String destination;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(nullable = false, length = 40)
    private String transport;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    private double plannedBudget;
    private double transportBudget;
    private double accommodationBudget;
    private double foodBudget;
    private double activitiesBudget;
    private double otherBudget;
    @Column(nullable = false)
    private boolean publiclyVisible;
    @Column(nullable = false, unique = true, length = 36)
    private String shareToken = UUID.randomUUID().toString();
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC, startTime ASC, id ASC")
    private List<ItineraryItem> itinerary = new ArrayList<>();
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripMember> members = new ArrayList<>();
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TripExpense> expenses = new ArrayList<>();

    protected Trip() {}

    public Trip(String title, String destination, LocalDate startDate, LocalDate endDate, String transport, AppUser owner,
                double plannedBudget, double transportBudget, double accommodationBudget,
                double foodBudget, double activitiesBudget, double otherBudget) {
        this.title = title;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.transport = transport;
        this.owner = owner;
        this.plannedBudget = plannedBudget;
        this.transportBudget = transportBudget;
        this.accommodationBudget = accommodationBudget;
        this.foodBudget = foodBudget;
        this.activitiesBudget = activitiesBudget;
        this.otherBudget = otherBudget;
    }

    public void addItem(ItineraryItem item) { itinerary.add(item); item.setTrip(this); }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDestination() { return destination; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getTransport() { return transport; }
    public AppUser getOwner() { return owner; }
    public double getPlannedBudget() { return plannedBudget; }
    public double getTransportBudget() { return transportBudget; }
    public double getAccommodationBudget() { return accommodationBudget; }
    public double getFoodBudget() { return foodBudget; }
    public double getActivitiesBudget() { return activitiesBudget; }
    public double getOtherBudget() { return otherBudget; }
    public boolean isPubliclyVisible() { return publiclyVisible; }
    public void setPubliclyVisible(boolean publiclyVisible) { this.publiclyVisible = publiclyVisible; }
    public String getShareToken() { return shareToken; }
    public List<ItineraryItem> getItinerary() { return itinerary; }
    public List<TripMember> getMembers() { return members; }
    public List<TripExpense> getExpenses() { return expenses; }
}
