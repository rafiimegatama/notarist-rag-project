package com.notarist.kase.application.port.in;

import com.notarist.kase.api.response.CaseStatisticsResponse;
import com.notarist.kase.api.response.DashboardSummaryResponse;
import com.notarist.kase.application.query.CallerContext;

/** Factual, SQL-only dashboard and statistics reads. No LLM, no inference. */
public interface CaseAnalyticsUseCase {

    DashboardSummaryResponse getSummary(CallerContext caller);

    CaseStatisticsResponse getStatistics(CallerContext caller);
}
