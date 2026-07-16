package com.notarist.kase.application.service;

import com.notarist.kase.api.response.ReminderResponse;
import com.notarist.kase.api.response.ReminderResponse.ReminderItem;
import com.notarist.kase.application.port.in.ReminderUseCase;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.ReminderCandidate;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The reminder engine. PURE CALCULATION: it reads the cases that can carry a reminder (one SQL query),
 * computes each item's due date from fixed, documented rules, and buckets them by proximity to now.
 * There is no scheduler and no persistence — a later sprint can wrap this in one, but the calculation
 * is deterministic and side-effect-free so it is trivially testable in isolation.
 *
 * <p>Due-date rules (calendar days from case creation). These are office SLAs, not a legal opinion;
 * they live here as constants so the engine stays deterministic and are the single place to change
 * when the office confirms real numbers.
 */
@Service
public class ReminderService implements ReminderUseCase {

    // Statutory-ish deadlines from case creation.
    static final int SKMHT_DEADLINE_DAYS = 30;   // SKMHT must be followed up promptly
    static final int APHT_DEADLINE_DAYS  = 60;

    // Human-gate SLAs: how long a case may sit awaiting a person before it is "due".
    static final int VERIFICATION_SLA_DAYS = 3;
    static final int QC_SLA_DAYS           = 2;
    static final int APPROVAL_SLA_DAYS      = 2;

    private final CaseAnalyticsRepository analytics;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ReminderService(CaseAnalyticsRepository analytics) {
        this(analytics, Clock.systemUTC());
    }

    /** Explicit-clock constructor: a fixed clock makes bucketing deterministic under test. */
    public ReminderService(CaseAnalyticsRepository analytics, Clock clock) {
        this.analytics = analytics;
        this.clock = clock;
    }

    @Override
    public ReminderResponse getReminders(CallerContext caller) {
        Instant now = clock.instant();
        List<ReminderItem> items = new ArrayList<>();

        for (ReminderCandidate c : analytics.reminderCandidates(caller.tenantId())) {
            addDeadlineReminder(items, c, now);
            addPendingReminder(items, c, now);
        }

        List<ReminderItem> overdue = bucket(items, now, Bucket.OVERDUE);
        List<ReminderItem> today = bucket(items, now, Bucket.TODAY);
        List<ReminderItem> within7 = bucket(items, now, Bucket.WITHIN_7_DAYS);
        List<ReminderItem> within30 = bucket(items, now, Bucket.WITHIN_30_DAYS);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("OVERDUE", overdue.size());
        counts.put("TODAY", today.size());
        counts.put("WITHIN_7_DAYS", within7.size());
        counts.put("WITHIN_30_DAYS", within30.size());

        int total = overdue.size() + today.size() + within7.size() + within30.size();
        return new ReminderResponse(now.toString(), total, counts, overdue, today, within7, within30);
    }

    private void addDeadlineReminder(List<ReminderItem> items, ReminderCandidate c, Instant now) {
        if (c.caseType() == CaseType.SKMHT) {
            items.add(item(c, "SKMHT_DEADLINE", c.createdAt().plus(SKMHT_DEADLINE_DAYS, ChronoUnit.DAYS), now));
        } else if (c.caseType() == CaseType.APHT) {
            items.add(item(c, "APHT_DEADLINE", c.createdAt().plus(APHT_DEADLINE_DAYS, ChronoUnit.DAYS), now));
        }
    }

    private void addPendingReminder(List<ReminderItem> items, ReminderCandidate c, Instant now) {
        switch (c.state()) {
            case WAITING_VERIFICATION -> items.add(item(c, "PENDING_VERIFICATION",
                    c.createdAt().plus(VERIFICATION_SLA_DAYS, ChronoUnit.DAYS), now));
            case WAITING_QC -> items.add(item(c, "PENDING_QC",
                    c.createdAt().plus(QC_SLA_DAYS, ChronoUnit.DAYS), now));
            case WAITING_NOTARY_APPROVAL -> items.add(item(c, "PENDING_APPROVAL",
                    c.createdAt().plus(APPROVAL_SLA_DAYS, ChronoUnit.DAYS), now));
            default -> { /* not a human-gate state — no pending reminder */ }
        }
    }

    private ReminderItem item(ReminderCandidate c, String type, Instant due, Instant now) {
        long days = ChronoUnit.DAYS.between(now, due);   // negative when overdue
        return new ReminderItem(c.caseId(), c.caseNumber(), c.caseType().name(),
                c.state().name(), type, due.toString(), days);
    }

    private enum Bucket { OVERDUE, TODAY, WITHIN_7_DAYS, WITHIN_30_DAYS }

    /** Buckets items and returns those in {@code target}, soonest-due first. Items due >30 days out drop off. */
    private List<ReminderItem> bucket(List<ReminderItem> items, Instant now, Bucket target) {
        Instant endOfToday = now.plus(1, ChronoUnit.DAYS);
        Instant in7 = now.plus(7, ChronoUnit.DAYS);
        Instant in30 = now.plus(30, ChronoUnit.DAYS);

        return items.stream()
                .filter(i -> classify(Instant.parse(i.dueDate()), now, endOfToday, in7, in30) == target)
                .sorted(Comparator.comparingLong(ReminderItem::daysUntilDue))
                .toList();
    }

    private Bucket classify(Instant due, Instant now, Instant endOfToday, Instant in7, Instant in30) {
        if (due.isBefore(now)) return Bucket.OVERDUE;
        if (due.isBefore(endOfToday)) return Bucket.TODAY;
        if (due.isBefore(in7)) return Bucket.WITHIN_7_DAYS;
        if (due.isBefore(in30)) return Bucket.WITHIN_30_DAYS;
        return null;   // beyond 30 days — not surfaced as a near-term reminder
    }

    /** Exposed for logging/telemetry parity with the rules above. */
    static Duration slaFor(CaseState state) {
        return switch (state) {
            case WAITING_VERIFICATION -> Duration.ofDays(VERIFICATION_SLA_DAYS);
            case WAITING_QC -> Duration.ofDays(QC_SLA_DAYS);
            case WAITING_NOTARY_APPROVAL -> Duration.ofDays(APPROVAL_SLA_DAYS);
            default -> Duration.ZERO;
        };
    }
}
