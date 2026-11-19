package com.tiet.docket.model;

public enum ApplicationStatus {
    PAYMENT_PENDING,     // saved, waiting for the student to pay the application fee
    SUPERVISOR_PENDING,  // waiting for the teacher to approve by email
    SUBMITTED,
    UNDER_REVIEW,
    AWAITING_CORRECTION,
    APPROVED,
    REJECTED,
    PROCESSED,
    VERIFIED,
    CLOSED
}