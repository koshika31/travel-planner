package com.travelplanner.api;

import com.travelplanner.api.ApiModels.ExpenseRequest;
import com.travelplanner.api.ApiModels.ExpenseShareView;
import com.travelplanner.api.ApiModels.ExpenseSummary;
import com.travelplanner.api.ApiModels.ExpenseView;
import com.travelplanner.api.ApiModels.PersonBalance;
import com.travelplanner.model.AppUser;
import com.travelplanner.model.ExpenseShare;
import com.travelplanner.model.InvitationStatus;
import com.travelplanner.model.Trip;
import com.travelplanner.model.TripExpense;
import com.travelplanner.model.TripMember;
import com.travelplanner.repository.TripExpenseRepository;
import com.travelplanner.repository.TripMemberRepository;
import com.travelplanner.repository.TripRepository;
import com.travelplanner.service.CsrfTokens;
import com.travelplanner.service.SessionAuth;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/trips/{tripId}/expenses")
public class TripExpensesController {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final TripRepository trips;
    private final TripMemberRepository members;
    private final TripExpenseRepository expenses;
    private final SessionAuth auth;
    private final CsrfTokens csrf;

    public TripExpensesController(TripRepository trips, TripMemberRepository members,
                                  TripExpenseRepository expenses, SessionAuth auth, CsrfTokens csrf) {
        this.trips = trips;
        this.members = members;
        this.expenses = expenses;
        this.auth = auth;
        this.csrf = csrf;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ExpenseSummary list(@PathVariable Long tripId, HttpSession session) {
        Trip trip = findTrip(tripId);
        auth.requireCanContribute(trip, session);
        return createSummary(trip);
    }

    @PostMapping
    @Transactional
    public ExpenseSummary add(@PathVariable Long tripId,
                              @Valid @RequestBody ExpenseRequest request,
                              @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                              HttpSession session) {
        csrf.requireValid(session, token);

        Trip trip = findTrip(tripId);
        AppUser payer = auth.requireCanContribute(trip, session);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        TripExpense expense = new TripExpense(
                trip, payer, request.description().trim(), request.category(), amount);

        List<AppUser> people = getPeopleOnTrip(trip);
        long cents = amount.movePointRight(2).longValueExact();
        long centsPerPerson = cents / people.size();
        long remainingCents = cents % people.size();

        for (int i = 0; i < people.size(); i++) {
            long shareCents = centsPerPerson;
            if (i < remainingCents) {
                shareCents++;
            }

            BigDecimal shareAmount = BigDecimal.valueOf(shareCents, 2);
            expense.addShare(new ExpenseShare(people.get(i), shareAmount));
        }

        expenses.save(expense);
        return createSummary(trip);
    }

    private List<AppUser> getPeopleOnTrip(Trip trip) {
        List<AppUser> people = new ArrayList<>();
        people.add(trip.getOwner());

        for (TripMember member : members.findByTripIdOrderByInvitedAtAsc(trip.getId())) {
            if (member.getStatus() == InvitationStatus.ACCEPTED) {
                people.add(member.getUser());
            }
        }

        return people;
    }

    private ExpenseSummary createSummary(Trip trip) {
        List<TripExpense> tripExpenses = expenses.findByTripIdOrderByCreatedAtAsc(trip.getId());
        List<Balance> balances = getBalances(trip);
        List<ExpenseView> expenseViews = new ArrayList<>();
        BigDecimal totalSpent = ZERO;

        for (TripExpense expense : tripExpenses) {
            totalSpent = totalSpent.add(expense.getAmount());

            Balance payerBalance = findOrAddBalance(balances, expense.getPaidBy());
            payerBalance.paid = payerBalance.paid.add(expense.getAmount());

            List<ExpenseShareView> shares = new ArrayList<>();
            for (ExpenseShare share : expense.getShares()) {
                Balance personBalance = findOrAddBalance(balances, share.getUser());
                personBalance.owes = personBalance.owes.add(share.getAmount());
                shares.add(new ExpenseShareView(share.getUser().getUsername(), share.getAmount()));
            }

            ExpenseView view = new ExpenseView(
                    expense.getId(),
                    expense.getDescription(),
                    expense.getCategory(),
                    expense.getAmount(),
                    expense.getPaidBy().getUsername(),
                    expense.getCreatedAt(),
                    shares);
            expenseViews.add(view);
        }

        List<PersonBalance> personBalances = new ArrayList<>();
        for (Balance balance : balances) {
            BigDecimal amountOwed = balance.paid.subtract(balance.owes);
            personBalances.add(new PersonBalance(
                    balance.user.getUsername(), balance.paid, balance.owes, amountOwed));
        }

        return new ExpenseSummary(expenseViews, personBalances, totalSpent);
    }

    private List<Balance> getBalances(Trip trip) {
        List<Balance> balances = new ArrayList<>();
        balances.add(new Balance(trip.getOwner()));

        for (TripMember member : members.findByTripIdOrderByInvitedAtAsc(trip.getId())) {
            if (member.getStatus() == InvitationStatus.ACCEPTED) {
                balances.add(new Balance(member.getUser()));
            }
        }

        return balances;
    }

    private Balance findOrAddBalance(List<Balance> balances, AppUser user) {
        for (Balance balance : balances) {
            if (balance.user.getId().equals(user.getId())) {
                return balance;
            }
        }

        Balance balance = new Balance(user);
        balances.add(balance);
        return balance;
    }

    private Trip findTrip(Long id) {
        Trip trip = trips.findWithItineraryById(id).orElse(null);
        if (trip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found.");
        }
        return trip;
    }

    private static class Balance {
        private final AppUser user;
        private BigDecimal paid = ZERO;
        private BigDecimal owes = ZERO;

        private Balance(AppUser user) {
            this.user = user;
        }
    }
}
