package com.travelplanner.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_expenses")
public class TripExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id", nullable = false)
    private AppUser paidBy;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ExpenseShare> shares = new ArrayList<>();

    protected TripExpense() {}

    public TripExpense(Trip trip, AppUser paidBy, String description, String category, BigDecimal amount) {
        this.trip = trip;
        this.paidBy = paidBy;
        this.description = description;
        this.category = category;
        this.amount = amount;
    }

    public void addShare(ExpenseShare share) { shares.add(share); share.setExpense(this); }
    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public AppUser getPaidBy() { return paidBy; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ExpenseShare> getShares() { return shares; }
}
