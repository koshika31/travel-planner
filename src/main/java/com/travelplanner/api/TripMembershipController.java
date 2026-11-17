package com.travelplanner.api;

import com.travelplanner.api.ApiModels.InvitationView;
import com.travelplanner.api.ApiModels.InviteRequest;
import com.travelplanner.api.ApiModels.MemberView;
import com.travelplanner.api.ApiModels.MembersView;
import com.travelplanner.model.AppUser;
import com.travelplanner.model.InvitationStatus;
import com.travelplanner.model.Trip;
import com.travelplanner.model.TripMember;
import com.travelplanner.repository.AppUserRepository;
import com.travelplanner.repository.TripMemberRepository;
import com.travelplanner.repository.TripRepository;
import com.travelplanner.service.CsrfTokens;
import com.travelplanner.service.SessionAuth;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
public class TripMembershipController {
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final AppUserRepository users;
    private final SessionAuth auth;
    private final CsrfTokens csrf;

    public TripMembershipController(TripRepository trips, TripMemberRepository members,
                                    AppUserRepository users, SessionAuth auth, CsrfTokens csrf) {
        this.trips = trips; this.members = members; this.users = users; this.auth = auth; this.csrf = csrf;
    }

    @GetMapping("/api/invitations") @Transactional(readOnly = true)
    public List<InvitationView> pendingInvitations(HttpSession session) {
        AppUser current = auth.requireUser(session);
        List<TripMember> invitations = members.findByUserIdAndStatusOrderByInvitedAtDesc(
                current.getId(), InvitationStatus.PENDING);
        List<InvitationView> result = new ArrayList<>();
        for (TripMember invitation : invitations) {
            result.add(invitationView(invitation));
        }
        return result;
    }

    @PostMapping("/api/trips/{tripId}/invitations") @Transactional
    public InvitationView invite(@PathVariable Long tripId, @Valid @RequestBody InviteRequest request,
                                 @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                                 HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(tripId);
        auth.requireOwner(trip, session);
        AppUser inviter = auth.requireUser(session);
        AppUser invitee = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        if (invitee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No account was found for that username.");
        }
        if (inviter.getId().equals(invitee.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are already a member of your own trip.");
        }

        TripMember member = members.findByTripIdAndUserId(tripId, invitee.getId()).orElse(null);
        if (member != null && member.getStatus() != InvitationStatus.DECLINED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That traveler is already invited or has joined.");
        }
        if (member == null) member = new TripMember(trip, invitee);
        else member.inviteAgain();
        return invitationView(members.save(member));
    }

    @GetMapping("/api/trips/{tripId}/members") @Transactional(readOnly = true)
    public MembersView listMembers(@PathVariable Long tripId, HttpSession session) {
        Trip trip = findTrip(tripId);
        if (!auth.canContribute(trip, session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this trip to view its expense group.");
        }
        boolean owner = auth.owns(trip, session);
        List<MemberView> result = new ArrayList<>();
        result.add(new MemberView(null, trip.getOwner().getUsername(), "OWNER", true));
        for (TripMember member : members.findByTripIdOrderByInvitedAtAsc(tripId)) {
            if (member.getStatus() == InvitationStatus.ACCEPTED || (owner && member.getStatus() == InvitationStatus.PENDING)) {
                result.add(new MemberView(member.getId(), member.getUser().getUsername(), member.getStatus().name(), false));
            }
        }
        return new MembersView(true, result);
    }

    @PostMapping("/api/invitations/{invitationId}/accept") @Transactional
    public InvitationView accept(@PathVariable Long invitationId,
                                 @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                                 HttpSession session) {
        csrf.requireValid(session, token);
        TripMember member = requireInvitee(invitationId, session);
        member.accept();
        return invitationView(members.save(member));
    }

    @PostMapping("/api/invitations/{invitationId}/decline") @Transactional
    public void decline(@PathVariable Long invitationId,
                        @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                        HttpSession session) {
        csrf.requireValid(session, token);
        TripMember member = requireInvitee(invitationId, session);
        member.decline();
        members.save(member);
    }

    @DeleteMapping("/api/trips/{tripId}/members/{memberId}") @Transactional
    public void removeMember(@PathVariable Long tripId, @PathVariable Long memberId,
                             @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                             HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(tripId);
        auth.requireOwner(trip, session);
        TripMember member = members.findById(memberId).orElse(null);
        if (member == null || !member.getTrip().getId().equals(tripId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip member not found.");
        }
        members.delete(member);
    }

    private TripMember requireInvitee(Long invitationId, HttpSession session) {
        AppUser current = auth.requireUser(session);
        TripMember member = members.findById(invitationId).orElse(null);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found.");
        }
        if (!member.getUser().getId().equals(current.getId()) || member.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invitation is no longer available to your account.");
        }
        return member;
    }

    private Trip findTrip(Long id) {
        Trip trip = trips.findWithItineraryById(id).orElse(null);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        }
        return trip;
    }

    private InvitationView invitationView(TripMember member) {
        Trip trip = member.getTrip();
        return new InvitationView(member.getId(), trip.getId(), trip.getTitle(), trip.getDestination(),
                trip.getStartDate(), trip.getEndDate(), trip.getOwner().getUsername(), member.getInvitedAt());
    }
}
