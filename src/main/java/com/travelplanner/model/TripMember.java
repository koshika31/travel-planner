package com.travelplanner.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_members", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "user_id"}))
public class TripMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime invitedAt = LocalDateTime.now();

    protected TripMember() {}

    public TripMember(Trip trip, AppUser user) {
        this.trip = trip;
        this.user = user;
    }

    public void accept() { status = InvitationStatus.ACCEPTED; }
    public void decline() { status = InvitationStatus.DECLINED; }
    public void inviteAgain() { status = InvitationStatus.PENDING; invitedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public AppUser getUser() { return user; }
    public InvitationStatus getStatus() { return status; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
}
