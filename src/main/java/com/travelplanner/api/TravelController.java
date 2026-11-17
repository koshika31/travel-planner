package com.travelplanner.api;

import com.travelplanner.api.ApiModels.*;
import com.travelplanner.model.AppUser;
import com.travelplanner.model.DestinationReview;
import com.travelplanner.model.InvitationStatus;
import com.travelplanner.model.ItineraryItem;
import com.travelplanner.model.Trip;
import com.travelplanner.model.TripMember;
import com.travelplanner.repository.ReviewRepository;
import com.travelplanner.repository.TripRepository;
import com.travelplanner.repository.TripMemberRepository;
import com.travelplanner.service.CsrfTokens;
import com.travelplanner.service.SessionAuth;
import com.travelplanner.service.WeatherService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api")
public class TravelController {
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final ReviewRepository reviews;
    private final WeatherService weather;
    private final SessionAuth auth;
    private final CsrfTokens csrf;

    private static final List<Destination> DESTINATIONS = List.of(
            new Destination("Kyoto", "Japan", "Temple paths, cedar forests and lantern-lit lanes.", "March to May", "⛩️", "lavender"),
            new Destination("Jaipur", "India", "Pink sandstone palaces, rooftop cafés and lively bazaars.", "October to March", "🕌", "peach"),
            new Destination("Bali", "Indonesia", "Volcanic sunrises, surf breaks and slow island mornings.", "April to October", "🌴", "mint"),
            new Destination("Lisbon", "Portugal", "Coastal viewpoints, tiled streets and late-night fado.", "May to September", "🚋", "sky"),
            new Destination("Coorg", "India", "Coffee trails, misty hills and waterfalls in the Western Ghats.", "October to March", "☕", "sage"),
            new Destination("Reykjavik", "Iceland", "Northern lights, geothermal pools and wild landscapes.", "September to March", "🌌", "slate"),
            new Destination("Paris", "France", "Neighborhood bakeries, riverside walks and world-class museums.", "April to June", "🥐", "rose"),
            new Destination("Marrakech", "Morocco", "Garden courtyards, colorful souks and desert day trips.", "March to May", "🧿", "sand")
    );

    public TravelController(TripRepository trips, TripMemberRepository members, ReviewRepository reviews, WeatherService weather,
                            SessionAuth auth, CsrfTokens csrf) {
        this.trips = trips; this.members = members; this.reviews = reviews; this.weather = weather; this.auth = auth; this.csrf = csrf;
    }

    @GetMapping("/trips") @Transactional(readOnly = true)
    public List<TripView> listMyTrips(HttpSession session) {
        AppUser user = auth.requireUser(session);
        List<Trip> myTrips = new ArrayList<>(trips.findByOwnerIdOrderByStartDateAsc(user.getId()));
        List<TripMember> invitations = members.findByUserIdAndStatusOrderByInvitedAtDesc(
                user.getId(), InvitationStatus.ACCEPTED);

        for (TripMember member : invitations) {
            Trip trip = trips.findWithItineraryById(member.getTrip().getId()).orElse(null);
            if (trip != null) {
                myTrips.add(trip);
            }
        }

        List<TripView> result = new ArrayList<>();
        for (Trip trip : myTrips) {
            result.add(view(trip));
        }
        return result;
    }

    @GetMapping("/trips/{id}") @Transactional(readOnly = true)
    public TripView getTrip(@PathVariable Long id, HttpSession session) {
        Trip trip = findTrip(id);
        if (!auth.canAccess(trip, session)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        }
        return view(trip);
    }

    @PostMapping("/trips") @Transactional
    public TripView createTrip(@Valid @RequestBody TripRequest r,
                               @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                               HttpSession session) {
        csrf.requireValid(session, token);
        AppUser owner = auth.requireUser(session);
        validateDates(r.startDate(), r.endDate());
        Trip trip = new Trip(r.title().trim(), r.destination().trim(), r.startDate(), r.endDate(), r.transport(), owner,
                r.plannedBudget(), r.transportBudget(), r.accommodationBudget(), r.foodBudget(), r.activitiesBudget(), r.otherBudget());
        trip.setPubliclyVisible(r.publiclyVisible());
        return view(trips.save(trip));
    }

    @DeleteMapping("/trips/{id}") @Transactional
    public void deleteTrip(@PathVariable Long id,
                           @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                           HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(id); auth.requireOwner(trip, session); trips.delete(trip);
    }

    @PostMapping("/trips/{id}/itinerary") @Transactional
    public TripView addItinerary(@PathVariable Long id, @Valid @RequestBody ItineraryRequest r,
                                 @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                                 HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(id); auth.requireOwner(trip, session);
        long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        if (r.dayNumber() > days) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That day falls outside this trip.");
        trip.addItem(new ItineraryItem(r.dayNumber(), r.startTime(), r.activity().trim(), clean(r.location()), r.category(), r.cost(), clean(r.notes())));
        return view(trips.save(trip));
    }

    @DeleteMapping("/trips/{tripId}/itinerary/{itemId}") @Transactional
    public TripView deleteItem(@PathVariable Long tripId, @PathVariable Long itemId,
                               @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                               HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(tripId); auth.requireOwner(trip, session);
        ItineraryItem itemToRemove = null;
        for (ItineraryItem item : trip.getItinerary()) {
            if (item.getId().equals(itemId)) {
                itemToRemove = item;
                break;
            }
        }
        if (itemToRemove == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Itinerary item not found.");
        }
        trip.getItinerary().remove(itemToRemove);
        return view(trips.save(trip));
    }

    @PatchMapping("/trips/{id}/share") @Transactional
    public TripView setVisibility(@PathVariable Long id, @RequestBody ShareRequest r,
                                 @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                                 HttpSession session) {
        csrf.requireValid(session, token);
        Trip trip = findTrip(id); auth.requireOwner(trip, session); trip.setPubliclyVisible(r.publiclyVisible());
        return view(trips.save(trip));
    }

    @GetMapping("/shared/{token}") @Transactional(readOnly = true)
    public TripView shared(@PathVariable String token, HttpSession session) {
        Trip trip = trips.findByShareToken(token).orElse(null);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This trip is private or no longer shared.");
        }
        if (!auth.canAccess(trip, session)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This trip is private or no longer shared.");
        }
        return view(trip);
    }

    @GetMapping("/destinations")
    public List<Destination> destinations(@RequestParam(defaultValue = "") String q) {
        String query = q.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return DESTINATIONS;

        List<Destination> result = new ArrayList<>();
        for (Destination destination : DESTINATIONS) {
            boolean matchesName = destination.name().toLowerCase(Locale.ROOT).contains(query);
            boolean matchesCountry = destination.country().toLowerCase(Locale.ROOT).contains(query);
            boolean matchesDescription = destination.description().toLowerCase(Locale.ROOT).contains(query);
            if (matchesName || matchesCountry || matchesDescription) {
                result.add(destination);
            }
        }
        return result;
    }

    @GetMapping("/reviews") @Transactional(readOnly = true)
    public List<ReviewView> reviews(@RequestParam String destination) {
        List<DestinationReview> savedReviews = reviews.findByDestinationIgnoreCaseOrderByCreatedAtDesc(destination.trim());
        List<ReviewView> result = new ArrayList<>();
        for (DestinationReview review : savedReviews) {
            result.add(view(review));
        }
        return result;
    }

    @PostMapping("/reviews") @Transactional
    public ReviewView addReview(@Valid @RequestBody ReviewRequest r,
                                @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                                HttpSession session) {
        csrf.requireValid(session, token);
        return view(reviews.save(new DestinationReview(r.destination().trim(), r.author().trim(), r.rating(), r.comment().trim())));
    }

    @GetMapping("/weather")
    public WeatherView weather(@RequestParam String city) { return weather.weatherFor(city); }

    private Trip findTrip(Long id) {
        Trip trip = trips.findWithItineraryById(id).orElse(null);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        }
        return trip;
    }
    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return date must be on or after departure date.");
        if (ChronoUnit.DAYS.between(start, end) > 45) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can be at most 46 days long.");
    }
    private String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private TripView view(Trip t) {
        List<ItineraryView> items = new ArrayList<>();
        double actualSpend = 0;
        for (ItineraryItem item : t.getItinerary()) {
            items.add(new ItineraryView(item.getId(), item.getDayNumber(), item.getStartTime(),
                    item.getActivity(), item.getLocation(), item.getCategory(), item.getCost(), item.getNotes()));
            actualSpend += item.getCost();
        }

        return new TripView(t.getId(), t.getTitle(), t.getDestination(), t.getStartDate(), t.getEndDate(), t.getTransport(), t.getPlannedBudget(),
                t.getTransportBudget(), t.getAccommodationBudget(), t.getFoodBudget(), t.getActivitiesBudget(), t.getOtherBudget(),
                actualSpend, t.isPubliclyVisible(), t.getShareToken(),
                t.getOwner().getUsername(), items);
    }
    private ReviewView view(DestinationReview r) { return new ReviewView(r.getId(), r.getDestination(), r.getAuthor(), r.getRating(), r.getComment(), r.getCreatedAt()); }
}
