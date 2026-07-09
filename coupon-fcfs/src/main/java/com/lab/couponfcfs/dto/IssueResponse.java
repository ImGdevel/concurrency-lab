package com.lab.couponfcfs.dto;

public record IssueResponse(String couponId, String userId, boolean success, String message, long remainingStock) {
}
