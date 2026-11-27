package vn.englishconversation.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminMetrics(Instant from, Instant until, Accounts accounts, Learning learning,
    Catalog catalog, List<Content> popularContent, List<Ai> ai, Limits configuredLimits) {
  public record Accounts(long learners, long admins, long locked, long registeredInPeriod) {}
  public record Learning(long learnerMessages, long listeningSubmissions, long pronunciationDrills,
      long reviews, long completedConversations, long endedConversations) {}
  public record Catalog(long visibleTopics,long visibleScenarios) {}
  public record Content(String type,String id,String title,long uses) {}
  public record Ai(String task,String provider,long attempts,long retries,long succeeded,long failed,long unknown,
      long running,long quotaErrors,BigDecimal averageLatencyMs,Long inputTokens,Long outputTokens,
      long latencySamples,long inputTokenSamples,long outputTokenSamples) {}
  public record Limits(int geminiGlobalPerMinute,int geminiUserPerMinute,int geminiConcurrent,int geminiTotal,
      int pronunciationUserPerDay,int pronunciationProviderPerDay) {}
}
