package com.travelplanner.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public final class ApiModels {
    private ApiModels() {}
    public record TripRequest(@NotBlank @Size(max=100) String title,
            @NotBlank @Size(max=100) String destination, @NotNull LocalDate startDate,
            @NotNull LocalDate endDate, @NotBlank String transport,
            @PositiveOrZero double plannedBudget, @PositiveOrZero double transportBudget,
            @PositiveOrZero double accommodationBudget, @PositiveOrZero double foodBudget,
            @PositiveOrZero double activitiesBudget, @PositiveOrZero double otherBudget,
            boolean publiclyVisible) {}
    public record ItineraryRequest(@Min(1) int dayNumber, LocalTime startTime,
            @NotBlank @Size(max=120) String activity, @Size(max=120) String location,
            @NotBlank String category, @PositiveOrZero double cost, @Size(max=500) String notes) {}
    public record ShareRequest(boolean publiclyVisible) {}
    public record ReviewRequest(@NotBlank @Size(max=100) String destination,
            @NotBlank @Size(max=80) String author, @Min(1) @Max(5) int rating,
            @NotBlank @Size(max=1000) String comment) {}
    public record TripView(Long id, String title, String destination, LocalDate startDate,
            LocalDate endDate, String transport, double plannedBudget, double transportBudget,
            double accommodationBudget, double foodBudget, double activitiesBudget, double otherBudget,
            double actualSpend, boolean publiclyVisible, String shareToken, String ownerUsername,
            List<ItineraryView> itinerary) {}
    public record SignupRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_]{3,30}") String username,
            @NotBlank @Size(min=8, max=72) String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserView(Long id, String username) {}
    public record AuthView(UserView user, String csrfToken) {}
    public record InviteRequest(@NotBlank @Size(max=30) String username) {}
    public record InvitationView(Long id, Long tripId, String tripTitle, String destination,
            LocalDate startDate, LocalDate endDate, String invitedBy, LocalDateTime invitedAt) {}
    public record MemberView(Long id, String username, String status, boolean owner) {}
    public record MembersView(boolean viewerCanAddExpenses, List<MemberView> members) {}
    public record ExpenseRequest(@NotBlank @Size(max=120) String description,
            @NotBlank @Size(max=40) String category, @NotNull @DecimalMin("0.01") @Digits(integer=10, fraction=2) BigDecimal amount) {}
    public record ExpenseShareView(String username, BigDecimal share) {}
    public record ExpenseView(Long id, String description, String category, BigDecimal amount,
            String paidBy, LocalDateTime createdAt, List<ExpenseShareView> shares) {}
    public record PersonBalance(String username, BigDecimal paid, BigDecimal owes, BigDecimal balance) {}
    public record ExpenseSummary(List<ExpenseView> expenses, List<PersonBalance> balances, BigDecimal totalSpent) {}
    public record ItineraryView(Long id, int dayNumber, LocalTime startTime, String activity,
            String location, String category, double cost, String notes) {}
    public record ReviewView(Long id, String destination, String author, int rating,
            String comment, LocalDateTime createdAt) {}
    public record Destination(String name, String country, String description, String season, String emoji, String tone) {}
    public record DailyForecast(String date, String condition, double low, double high) {}
    public record WeatherView(String location, String updatedAt, double temperature, double feelsLike,
            String condition, double windKph, double precipitationMm, List<DailyForecast> forecast) {}
}
