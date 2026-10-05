package com.codeit.careeros.common.enums;

/** Lifecycle of an assessment attempt. */
public enum AttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    /** Terminal state: the allowed duration elapsed before submission. */
    EXPIRED
}
