package vn.englishconversation.admin.dto;

public record AdminOverview(long learners, long lockedAccounts, long visibleTopics,
    long visibleScenarios, long completedPractices, long aiAttempts24h, long aiSucceeded24h,
    long aiFailed24h, long aiUnknown24h) {}
