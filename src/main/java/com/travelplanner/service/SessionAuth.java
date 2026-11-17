package com.travelplanner.service;

import com.travelplanner.model.AppUser;
import com.travelplanner.model.Trip;
import com.travelplanner.model.InvitationStatus;
import com.travelplanner.repository.AppUserRepository;
import com.travelplanner.repository.TripMemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SessionAuth {
    private static final String USER_ID = "userId";
    private final AppUserRepository users;
    private final TripMemberRepository members;

    public SessionAuth(AppUserRepository users, TripMemberRepository members) { this.users = users; this.members = members; }

    public AppUser requireUser(HttpSession session) {
        Object id = session.getAttribute(USER_ID);
        if (!(id instanceof Long userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        AppUser user = users.findById(userId).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        }
        return user;
    }

    public boolean owns(Trip trip, HttpSession session) {
        Object id = session == null ? null : session.getAttribute(USER_ID);
        return id instanceof Long userId && trip.getOwner() != null && trip.getOwner().getId().equals(userId);
    }

    public void requireOwner(Trip trip, HttpSession session) {
        AppUser user = requireUser(session);
        if (!trip.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner can edit this trip.");
        }
    }

    public boolean isAcceptedMember(Trip trip, HttpSession session) {
        Object id = session == null ? null : session.getAttribute(USER_ID);
        return id instanceof Long userId && members.existsByTripIdAndUserIdAndStatus(trip.getId(), userId, InvitationStatus.ACCEPTED);
    }

    public boolean canAccess(Trip trip, HttpSession session) {
        return trip.isPubliclyVisible() || owns(trip, session) || isAcceptedMember(trip, session);
    }

    public boolean canContribute(Trip trip, HttpSession session) {
        return owns(trip, session) || isAcceptedMember(trip, session);
    }

    public AppUser requireCanContribute(Trip trip, HttpSession session) {
        AppUser user = requireUser(session);
        if (!canContribute(trip, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the trip owner and accepted invitees can add shared expenses.");
        }
        return user;
    }

    public static String userSessionKey() { return USER_ID; }
}
