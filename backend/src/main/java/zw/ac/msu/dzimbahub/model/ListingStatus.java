package zw.ac.msu.dzimbahub.model;

/**
 * PENDING   - submitted by a landlord, waiting for an administrator (hidden from the public)
 * ACTIVE    - approved and visible to everyone
 * REJECTED  - not approved; the landlord sees the reason and can edit and resubmit
 * SUSPENDED - was live, hidden later by an administrator
 */
public enum ListingStatus { PENDING, ACTIVE, REJECTED, SUSPENDED }
