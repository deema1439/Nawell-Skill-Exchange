[NAWELL_README_Deema.md](https://github.com/user-attachments/files/33176923/NAWELL_README_Deema.md)
# NAWELL | ناول

NAWELL | ناول is a skill-exchange platform that helps individuals and companies learn without relying only on paid lessons. Users earn tokens by teaching and spend them on learning. Skill verification, AI matching, date negotiation, sessions, and reviews help users find suitable providers and organize their exchanges.

## Main features

- **Accounts and profiles:** individual and company registration, session-based login/logout, email verification, profile views, and dashboards.
- **Skills and assessments:** a skill catalog, account skills, assessment history, skill levels, and verification.
- **Teaching offers:** providers offer verified skills with token costs, capacity, online/on-site delivery modes, and AI draft-price evaluation.
- **Learning requests:** learners request active offers and view requests by skill, requester, provider, urgency, or status.
- **Negotiations:** learners and providers exchange messages and propose dates; urgency and weekend rules can affect the token price.
- **Exchanges:** participants create, accept, cancel, and complete exchanges, with token reservation, refunds, and provider credits.
- **Agreements:** provider and learner acceptance flags, acceptance status, and AI-assisted agreement drafting.
- **Sessions:** individual and group sessions, participant enrollment, attendance, offer/exchange lookups, and Zoom meeting creation.
- **Reviews:** reviews between exchange participants, account review lists, and average ratings.
- **Tokens:** balance, transaction history, teaching bonuses, refunds, and simulated purchase/redemption.
- **Search:** find providers and learning requests by skill.
- **Notifications:** email and WhatsApp updates for learning requests, negotiation messages, exchanges, and refunds; session emails and automated reminders.

## Typical learning journey

1. Register an account, log in, and verify the email address.
2. Add skills to the account and take an assessment. Passing an assessment can verify the skill and update its level.
3. Publish an offer for a verified skill, or search for another provider's offers.
4. Create a learning request against an active offer.
5. Negotiate a date and accept a proposal, including any applicable urgency or weekend cost.
6. Create and accept an exchange. The acceptance workflow reserves the learner's tokens.
7. Create a session from the accepted proposal. Registered learners are joined automatically and online sessions create Zoom automatically; then record attendance.
8. The learner confirms exchange completion; reserved tokens are credited to the provider.
9. Review the other participant and track token history or eligible teaching bonuses.

Agreement acceptance is tracked separately; the current exchange workflow does not require both agreement acceptance flags before completion.

## Token rules

| Rule                     | Current behavior                                                              |
| ---                      | ---                                                                           |
| Starting balance         | New accounts start with 3 tokens                                              |
| Offer price              | Each offer sets its token cost                                                |
| Urgency                  | An earlier proposed date can add 1â€“3 tokens under the negotiation calculation |
| Weekend                  | Friday or Saturday proposals add 1 token                                      |
| Exchange acceptance      | Reserves tokens from the learner's balance                                    |
| Exchange cancellation    | Refunds reserved tokens where applicable                                      |
| Exchange completion      | Credits the provider after learner confirmation                               |
| Teaching bonus           | 5 bonus tokens per eligible group of 5 completed teachings                    |
| Purchase/redemption rate | 1 token equals 10 SAR in the simulation                                       |

Purchase and redemption update local balances and transaction records. They do not process real payments or payouts.

## AI and external integrations

| Service  | Features                                                                                                                                                                                                                    |
| ---      | ---                                                                                                                                                                                                                         |
| OpenAI   | Provider matching, match explanations, CV skill extraction, offer suggestions, skill relationships, related providers, assessment generation/evaluation, exchange fairness, agreement drafting, draft-offer price evaluation, and LinkedIn skill matching |
| Apify    | Retrieves LinkedIn profile data for the LinkedIn skill workflow                                                                                                                                                             |
| Brevo    | Verification, learning-request, negotiation, exchange, token, and session emails, plus scheduled session reminders                                                                                                          |
| Zoom     | OAuth token retrieval and meeting creation                                                                                                                                                                                  |
| Ultramsg | WhatsApp notifications for learning requests, negotiation messages, exchange creation/acceptance/cancellation, and cancellation refunds                                                                                     |

The LinkedIn workflow uses Apify rather than the official LinkedIn API. AI feature routes include supporting import operations; not every route calls a model on every request.

## Notification workflows

- A new learning request notifies the provider by email and WhatsApp.
- A negotiation response notifies the other participant by email and WhatsApp; proposal acceptance sends an email.
- Exchange creation and acceptance notify the relevant participant by email and WhatsApp.
- Cancellation sends participant updates and, when tokens were reserved, a WhatsApp refund confirmation to the learner.
- New sessions automatically join learners with accepted or in-progress exchanges and email the schedule and Zoom link for online sessions. An already-joined learner cannot join again.
- Exchange completion sends completion emails to the learner and provider.
- A scheduled job checks every minute and sends session reminder emails to enrolled learners during the hour before the session starts.

Notifications run inside existing workflows or the scheduled reminder job. They are not separate HTTP endpoints.

## Technology and structure

Java 17, Spring Boot, Spring MVC, Spring Data JPA/Hibernate, MySQL, Jakarta Validation, Lombok, Maven, the OpenAI Java SDK, Apache PDFBox, and Jackson.

```text
Capstone_3/
â”œâ”€â”€ pom.xml
â”œâ”€â”€ mvnw / mvnw.cmd
â””â”€â”€ src/main/
    â”œâ”€â”€ java/com/example/capstone_3/
    â”‚   â”œâ”€â”€ Controller/   HTTP routes
    â”‚   â”œâ”€â”€ Service/      Business logic and integration helpers
    â”‚   â”œâ”€â”€ Repository/   Database access interfaces
    â”‚   â”œâ”€â”€ Model/        JPA entities
    â”‚   â”œâ”€â”€ DtoIn/        Request data
    â”‚   â”œâ”€â”€ DtoOut/       Response data
    â”‚   â”œâ”€â”€ Config/       Application configuration
    â”‚   â”œâ”€â”€ Advice/       Exception handling
    â”‚   â””â”€â”€ Api/          Response and exception types
    â””â”€â”€ resources/application.properties
```

## Database entities

| Area                    | Entities                                         |
| ---                     | ---                                              |
| Accounts and profiles   | Account, IndividualProfile, CompanyProfile       |
| Skills and offers       | Skill, AccountSkill, SkillAssessment, SkillOffer |
| Requests and exchanges  | LearningRequest, RequestNegotiation, Exchange    |
| Agreements and sessions | Agreement, Session, SessionParticipant           |
| Feedback and tokens     | Review, TokenTransaction                         |

## API conventions

- JSON request/response bodies are used for most endpoints.
- CV upload uses `multipart/form-data` with a `file` field.
- Login stores the account ID in an HTTP session. Preserve the session cookie when calling routes that require login.
- IDs in route paths identify skills, offers, requests, exchanges, sessions, and account skills.
- Bean validation and controller advice handle request validation and application exceptions.

This is a capstone implementation. Route counts describe implemented source routes, not a guarantee of runtime correctness. Production deployment requires further security work, including password hashing and consistent authorization on raw CRUD routes.

## Endpoint summary

| Category                   | Count   |
| ---                        | ---:    |
| Basic CRUD                 | 43      |
| Extra business features    | 66      |
| AI feature routes          | 13      |
| Dedicated other API routes | 2       |
| **Total**                  | **124** |

Each HTTP method plus full route is counted once. Basic CRUD includes ordinary field mapping, defaults, record/relationship existence checks, and uniqueness checks. Endpoints with additional business rulesâ€”such as pricing, scoring, eligibility, token grants, lifecycle restrictions, or cross-record effectsâ€”are classified as Extra. AI and dedicated integration routes have their own categories.


## Current test data and Postman

Checked against GitHub main `8700b61` on 7 October 2026. Use `NAWELL_Existing_Database_Seed.sql` in the already selected application database after Hibernate has created the tables. No new database is created, and existing account data is preserved. The earlier two-database package is superseded.

The seed keeps the original learner and teacher accounts and adds dedicated AI fixture accounts, provider offers, skill assessments, requests, proposals, exchanges, agreements, sessions, reviews and token history. AI writes use `ai.teacher.fixture@example.com` and `ai.learner.fixture@example.com`, leaving the flow accounts' skills and balances independent. Matching and recommendation queries still search global active offers, so new flow offers may affect their results.

After running SQL, copy the final `postmanSeedIdsJson` JSON cell into the `seedIdsJson` collection variable. Do this in the main collection and, if used, `NAWELL_AI_Existing_Database.postman_collection.json`. Select No environment; old environments can override variables. Both use `http://localhost:8080/api/v1`. The imported map contains dynamic flow IDs and separate `ai*` fixture IDs; no fixed database IDs or second backend instance is required.

The seeded-account flow skips registration (`useSeededAccounts=true`) and skips verification when the profile is already verified. Existing Java enrollment is accepted; verified EXPERT skills skip reassessment. Balances are checked relative to the actual starting wallet, not a fixed 3-token balance. Existing records are not reset.

The main flow keeps offer evaluation step 14a and matching steps 20a/20b removed as requested. Offer evaluation is implemented and available in the standalone AI collection. The AI folder in the main collection also uses dedicated fixture IDs and login details. `aiExchangeId` belongs to its accepted AI fixture, not the completed main exchange.

Generate Java questions before grading and provide answers to those Java questions. The supplied initial `javaQuestions`/`javaAnswers` examples contain English grammar; they cannot guarantee a passing Java result. Generation replaces `javaQuestions`, so update `javaAnswers` accordingly. English AI tests separately use `aiQuestions`/`aiAnswers`. Use an authorized public LinkedIn URL and the supplied test CV for those import requests. Service credentials remain required. No live MySQL or provider/API run was performed during this audit.

## Current session workflow

`POST /api/v1/session/create/{offerId}` accepts `negotiationId`, `startTime` in `HH:mm`, and `durationMinutes`. The proposal must be accepted and belong to an accepted/in-progress exchange for the offer. The date comes from that proposal; the requested start time supplies the time in Asia/Riyadh. Mode comes from the offer; a BOTH offer needs an explicit mode, and an in-person session needs a location.

Successful creation returns HTTP **201**, message `Session created and all email requests accepted`, `sessionId`, schedule, title, mode/status, participant/email counts, and meeting data. It automatically joins registered learners. For online sessions it creates and stores the Zoom meeting and includes the link in session emails. The session title is the skill name. If integration creation fails the database transaction is rolled back; the service also attempts Zoom cleanup when needed.

`POST /api/v1/session/{sessionId}/zoom` remains available and returns an already stored meeting instead of creating another. Postman checks this after creation. Repeating the learner join request returns HTTP **400**, `You have already joined this session`; it does not send another join email. The main flow checks this and then records attendance as before.

## Deema Contributions

The route inventory credits the first recorded introduction in Git. Later changes may involve shared work.

**Entities:** Skill, AccountSkill, SkillAssessment, LearningRequest, SkillOffer.

**Main work:** skill and account-skill management, assessment scoring/history, learning-request CRUD, verified teaching offers, offer filters, both search routes, and token balance/history/bonus/refund/purchase/redemption workflows.

**AI:** skill relationships, related providers, assessment generation, and assessment evaluation.

**Integration contributions:** Brevo notifications for token operations. These run inside existing endpoints, so they do not add dedicated Other API routes.

**Configuration ownership:** Ultramsg WhatsApp API configuration and the shared WhatsApp messaging service.

**Notification work:** WhatsApp confirmation of the learner's token refund after exchange cancellation, including support for individual and company profile phone numbers.

**Additional contributions:** duplicate active-offer prevention, a minimum balance after redemption, and token email/message improvements.


| Basic CRUD | Extra | AI   | Other API | Total endpoints | Entity classes |
| ---:       | ---:  | ---: | ---:      | ---:            | ---:           |
| 10         | 23    | 4    | 0         | **37**          | 5              |

### Extra endpoints

| Method | Route                                               | Handler                      |
| ---    | ---                                                 | ---                          |
| POST   | `/api/v1/account-skill/add/{skillId}`               | `addAccountSkill`            |
| GET    | `/api/v1/account-skill/account/{accountId}`         | `getSkillsByAccount`         |
| GET    | `/api/v1/account-skill/verified/{accountId}`        | `getVerifiedSkillsOfAccount` |
| POST   | `/api/v1/learning-request/create/{offerId}`         | `addLearningRequest`         |
| PUT    | `/api/v1/learning-request/update/{id}`              | `updateLearningRequest`      |
| DELETE | `/api/v1/learning-request/delete/{id}`              | `deleteLearningRequest`      |
| GET    | `/api/v1/search/providers/{skillId}`                | `findProvidersBySkill`       |
| GET    | `/api/v1/search/requests/{skillId}`                 | `findRequestsBySkill`        |
| POST   | `/api/v1/skill-assessment/take/{accountSkillId}`    | `takeSkillAssessment`        |
| GET    | `/api/v1/skill-assessment/history/{accountSkillId}` | `getAssessmentHistory`       |
| GET    | `/api/v1/skill-assessment/latest/{accountSkillId}`  | `getLatestAssessment`        |
| POST   | `/api/v1/skill-offer/create/{skillId}`              | `addOffer`                   |
| PUT    | `/api/v1/skill-offer/update/{id}`                   | `updateSkillOffer`           |
| DELETE | `/api/v1/skill-offer/delete/{id}`                   | `deleteSkillOffer`           |
| GET    | `/api/v1/skill-offer/skill/{skillId}`               | `getOffersBySkill`           |
| GET    | `/api/v1/skill-offer/provider/{providerId}`         | `getOffersCreatedByProvider` |
| GET    | `/api/v1/skill-offer/available`                     | `getActiveOffers`            |
| GET    | `/api/v1/token-transaction/account/balance`         | `getBalance`                 |
| GET    | `/api/v1/token-transaction/account/history`         | `getHistory`                 |
| POST   | `/api/v1/token-transaction/bonus`                   | `giveBonus`                  |
| POST   | `/api/v1/token-transaction/refund/{exchangeId}`     | `refundExchange`             |
| POST   | `/api/v1/token-transaction/purchase/{amount}`       | `purchaseTokens`             |
| POST   | `/api/v1/token-transaction/redeem/{amount}`         | `redeemTokens`               |

### AI feature routes

| Method | Route                                             | Handler              |
| ---    | ---                                               | ---                  |
| GET    | `/api/v1/ai/skill/relationships/{skillId}`        | `skillRelationships` |
| GET    | `/api/v1/ai/skill/{skillId}/related-providers`    | `relatedProviders`   |
| POST   | `/api/v1/ai/assessment/generate/{accountSkillId}` | `generateAssessment` |
| POST   | `/api/v1/ai/assessment/evaluate/{accountSkillId}` | `evaluateAssessment` |

### Basic CRUD endpoints

<details>
<summary>View basic CRUD routes</summary>

| Method | Route                               | Handler                  |
| ---    | ---                                 | ---                      |
| GET    | `/api/v1/account-skill/get`         | `getAccountSkills`       |
| PUT    | `/api/v1/account-skill/update/{id}` | `updateAccountSkill`     |
| DELETE | `/api/v1/account-skill/delete/{id}` | `deleteAccountSkill`     |
| GET    | `/api/v1/learning-request/get`      | `getAllLearningRequests` |
| GET    | `/api/v1/skill-assessment/get`      | `getAllSkillAssessments` |
| GET    | `/api/v1/skill/get`                 | `getAllSkills`           |
| POST   | `/api/v1/skill/add`                 | `addSkill`               |
| PUT    | `/api/v1/skill/update/{id}`         | `updateSkill`            |
| DELETE | `/api/v1/skill/delete/{id}`         | `deleteSkill`            |
| GET    | `/api/v1/skill-offer/get`           | `getSkillOffer`          |

</details>
