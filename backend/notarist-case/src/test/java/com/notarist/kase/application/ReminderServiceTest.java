package com.notarist.kase.application;

import com.notarist.core.domain.valueobject.CorrelationId;
import com.notarist.kase.api.response.ReminderResponse;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository;
import com.notarist.kase.application.port.out.CaseAnalyticsRepository.ReminderCandidate;
import com.notarist.kase.application.query.CallerContext;
import com.notarist.kase.application.service.ReminderService;
import com.notarist.kase.domain.state.CaseState;
import com.notarist.kase.domain.valueobject.CaseType;
import com.notarist.kase.domain.valueobject.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Determinism is the whole point of this engine, so the test pins the clock and asserts exact bucket
 * membership. "now" is 2026-07-15T00:00:00Z.
 */
class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    private final UUID tenantId = UUID.randomUUID();

    private CallerContext caller() {
        return new CallerContext(UUID.randomUUID(), tenantId, Role.STAFF, CorrelationId.generate());
    }

    private ReminderCandidate candidate(CaseType type, CaseState state, String createdAt) {
        return new ReminderCandidate(UUID.randomUUID(), "1/V/2026", type, state, Instant.parse(createdAt));
    }

    @Test
    @DisplayName("reminders land in the right bucket by due-date proximity")
    void bucketing() {
        CaseAnalyticsRepository repo = mock(CaseAnalyticsRepository.class);
        when(repo.reminderCandidates(tenantId)).thenReturn(List.of(
                candidate(CaseType.SKMHT, CaseState.UPLOADING, "2026-05-01T00:00:00Z"),   // +30d = 05-31 OVERDUE
                candidate(CaseType.APHT,  CaseState.UPLOADING, "2026-05-16T00:00:00Z"),   // +60d = 07-15 TODAY
                candidate(CaseType.ROYA,  CaseState.WAITING_VERIFICATION, "2026-07-14T00:00:00Z"), // +3d = 07-17 W7
                candidate(CaseType.FIDUSIA, CaseState.WAITING_QC, "2026-07-10T00:00:00Z"),// +2d = 07-12 OVERDUE
                candidate(CaseType.SKMHT, CaseState.CASE_CREATED, "2026-07-01T00:00:00Z") // +30d = 07-31 W30
        ));

        ReminderService service = new ReminderService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
        ReminderResponse r = service.getReminders(caller());

        assertThat(r.totalCount()).isEqualTo(5);
        assertThat(r.overdue()).extracting(ReminderResponse.ReminderItem::reminderType)
                .containsExactlyInAnyOrder("SKMHT_DEADLINE", "PENDING_QC");
        assertThat(r.today()).extracting(ReminderResponse.ReminderItem::reminderType)
                .containsExactly("APHT_DEADLINE");
        assertThat(r.within7Days()).extracting(ReminderResponse.ReminderItem::reminderType)
                .containsExactly("PENDING_VERIFICATION");
        assertThat(r.within30Days()).extracting(ReminderResponse.ReminderItem::reminderType)
                .containsExactly("SKMHT_DEADLINE");
        assertThat(r.countsByBucket())
                .containsEntry("OVERDUE", 2).containsEntry("TODAY", 1)
                .containsEntry("WITHIN_7_DAYS", 1).containsEntry("WITHIN_30_DAYS", 1);
    }

    @Test
    @DisplayName("a case can raise two reminders: a deadline AND a pending-gate SLA")
    void deadlineAndPendingTogether() {
        CaseAnalyticsRepository repo = mock(CaseAnalyticsRepository.class);
        // An SKMHT that is also awaiting verification: SKMHT_DEADLINE (+30d) and PENDING_VERIFICATION (+3d).
        when(repo.reminderCandidates(tenantId)).thenReturn(List.of(
                candidate(CaseType.SKMHT, CaseState.WAITING_VERIFICATION, "2026-07-14T00:00:00Z")));

        ReminderService service = new ReminderService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
        ReminderResponse r = service.getReminders(caller());

        assertThat(r.totalCount()).isEqualTo(2);
        assertThat(r.within7Days()).extracting(ReminderResponse.ReminderItem::reminderType)
                .contains("PENDING_VERIFICATION");
        assertThat(r.within30Days()).extracting(ReminderResponse.ReminderItem::reminderType)
                .contains("SKMHT_DEADLINE");
    }

    @Test
    @DisplayName("items due more than 30 days out are not surfaced")
    void beyond30Dropped() {
        CaseAnalyticsRepository repo = mock(CaseAnalyticsRepository.class);
        // APHT created today → due +60d, well beyond the 30-day horizon.
        when(repo.reminderCandidates(tenantId)).thenReturn(List.of(
                candidate(CaseType.APHT, CaseState.UPLOADING, "2026-07-15T00:00:00Z")));

        ReminderService service = new ReminderService(repo, Clock.fixed(NOW, ZoneOffset.UTC));
        ReminderResponse r = service.getReminders(caller());

        assertThat(r.totalCount()).isZero();
    }
}
