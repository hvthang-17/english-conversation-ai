package vn.englishconversation.admin.dto;

public record AdminActivity(String id, String activity, String subject, String reference, String actor,
                            String status, String latency, String occurred) {}
