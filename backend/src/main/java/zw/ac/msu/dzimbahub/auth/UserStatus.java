package zw.ac.msu.dzimbahub.auth;

/** Life-cycle of an account. Only APPROVED accounts can sign in. */
public enum UserStatus { PENDING, APPROVED, REJECTED, SUSPENDED }
