# DzimbaHub test plan: role-based access and operations

Run all automated tests with `cd backend && mvn test`. Each automated test carries its ID in its display name.
The "Result" columns are left for you to fill in after running the tests and the manual checks.

## 1. Role permission matrix (automated: RoleAccessMatrixTest, 39 endpoints x 4 actors)
Every endpoint is called as anonymous, tenant, landlord and administrator. Expected: allowed role gets 2xx/4xx-validation (never 401/403); anonymous gets 401; any other signed-in role gets 403.

| Area | Endpoints | Anonymous | Tenant | Landlord | Admin |
|---|---|---|---|---|---|
| Public | GET /properties, /properties/{id}, /properties/{id}/reviews, POST /recommendations, GET /health | allow | allow | allow | allow |
| Any signed-in | GET /auth/me, any unlisted path | 401 | allow | allow | allow |
| Tenant | /tenant/** (viewings, enquiries, messages, reviews, bookings, preferences) | 401 | allow | 403 | 403 |
| Landlord | /landlord/** (own properties, viewings, messages, bookings) | 401 | 403 | allow | 403 |
| Admin | /admin/** (stats, audit, users, properties) | 401 | 403 | 403 | allow |

## 2. Automated test cases
| ID | Behaviour | Class |
|---|---|---|
| AUTH-01 | Signup creates a PENDING account, no token | AuthApprovalFlowTest |
| AUTH-02 | Pending user cannot sign in, no SMS sent | AuthApprovalFlowTest |
| AUTH-03 | Public signup cannot create an administrator | AuthApprovalFlowTest |
| AUTH-04 | Duplicate email and invalid phone rejected | AuthApprovalFlowTest |
| AUTH-05 | Approved user: password, then SMS code, then token | AuthApprovalFlowTest |
| AUTH-06 | Wrong password rejected, no code sent | AuthApprovalFlowTest |
| AUTH-07 | Wrong code rejected; challenge locks after allowed attempts | AuthApprovalFlowTest |
| AUTH-08 | A code works only once | AuthApprovalFlowTest |
| AUTH-09 | Unknown challenge rejected | AuthApprovalFlowTest |
| AUTH-10 | Rejected and suspended users cannot sign in | AuthApprovalFlowTest |
| AUTH-11 | Suspending a user stops their existing token | AuthApprovalFlowTest |
| AUTH-12 | Admin approval enables sign-in and texts the user | AuthApprovalFlowTest |
| AUTH-13 | Missing or tampered token rejected | AuthApprovalFlowTest |
| LL-01..04 | Landlord creates/edits/deletes own listing; cannot touch others'; invalid listing refused; tenant cannot create | WorkflowTest |
| VW-01, VW-02 | Viewing request, owner-only decision, one pending per property, cancel rules, past dates refused | WorkflowTest |
| MSG-01, MSG-02 | Enquiry reaches owning landlord; reply rules; empty message refused | WorkflowTest |
| REV-01, REV-02 | Rating needs approved viewing, once per tenant, 1 to 5, average updates; only tenants rate | WorkflowTest |
| BK-01 | Booking needs approved viewing; simulated payment once; ownership checks | WorkflowTest |
| PREF-01, REC-01 | Preferences saved per tenant; recommendations public, ranked, exclude suspended listings | WorkflowTest |
| ADM-01..05 | Suspend/restore listing; user suspension rules (not self, not admins); reject pending; stats and audit log; delete cascades | WorkflowTest |
| REC-02..04 | Recommendation scoring unit tests | RecommendationServiceTest |

## 3. Manual checks in the browser (fill in Result)
| # | Steps | Expected | Result |
|---|---|---|---|
| M1 | Request a tenant account at /signup | "Request sent" screen; no sign-in | |
| M2 | Try to sign in with that account | Message says it is waiting for approval | |
| M3 | Sign in as admin; Administration shows 1 waiting request; click Approve | Row disappears; success message | |
| M4 | Sign in as the new tenant (password, then code) | Tenant dashboard (viewings, bookings, messages, preferences) | |
| M5 | Sign in as a landlord | Landlord dashboard (listings, viewing requests, enquiries); no tenant forms on property pages | |
| M6 | Tenant opens a property: request viewing, send enquiry | Buttons show progress then a green confirmation | |
| M7 | Landlord approves the viewing and replies to the enquiry | Tenant dashboard shows both updates after refresh | |
| M8 | Tenant rates the property, creates a booking, confirms simulated payment | Review appears with new average; booking becomes confirmed with SIM- reference | |
| M9 | Admin suspends a listing | Listing disappears from Find Houses; Restore brings it back | |
| M10 | Admin suspends the tenant; tenant clicks any action | Tenant is signed out / receives a message | |
| M11 | Open /dashboard in a narrow (phone-width) window | Tables scroll sideways; buttons remain usable | |

## Listing approval and photo uploads

| ID | Requirement | Test |
|----|-------------|------|
| LST-01 | A new listing is PENDING and hidden from the public until an administrator approves it; the landlord is notified | ListingApprovalTest |
| LST-02 | A rejection needs a reason; the landlord sees it; editing resubmits the listing | ListingApprovalTest |
| LST-03 | Editing an approved listing sends it back for approval; a suspended listing stays suspended | ListingApprovalTest |
| LST-04 | Only administrators can approve listings | ListingApprovalTest |
| IMG-01 | A landlord uploads a photo from their device and anyone can view it | ListingApprovalTest |
| IMG-02 | Only real JPG/PNG/WebP files are accepted, and only from landlords | ListingApprovalTest |
| IMG-03 | Uploaded photos form the listing gallery; removed photos are deleted | ListingApprovalTest |
| IMG-04 | A landlord cannot attach another landlord's photo | ListingApprovalTest |

## Authenticator app sign-in

| ID | Requirement | Test |
|----|-------------|------|
| TOTP-01 | First sign-in shows a QR link; the app's code finishes setup; the secret is stored encrypted | TotpFlowTest |
| TOTP-02 | Later sign-ins ask for the app's code; a code cannot be used twice | TotpFlowTest |
| TOTP-03 | "Get a code another way" sends a code through the fallback channel, once per sign-in | TotpFlowTest |
| TOTP-04 | Only an administrator can reset a lost authenticator; the user then sets it up again | TotpFlowTest |
| TOTP-05 | Accounts that are not approved cannot start setup | TotpFlowTest |
| TOTP-RFC | Codes match the RFC 6238 SHA-1 test values | TotpTest |
