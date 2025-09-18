---
title: "User Invitation Feature - Implementation Plan"
author: "Claude Code"
created: "2025-01-16"
updated: "2025-01-16"
status: "implementation-ready"
related_issues: ["#448"]
reviewers: []
---

# User Invitation Feature - 正式実装計画

## 📋 概要

Issue #448「User Invitation Feature Needs Formal Implementation」に対する包括的な実装計画。現在の実装の問題点を解決し、業界標準を上回るユーザー招待機能を実現する。

### 🎯 **改善目標**
- **OAuth Protocol Purity**: 標準仕様への厳密準拠
- **Enterprise Security**: OWASP 2025準拠のセキュリティ実装
- **Superior UX**: Auth0/Keycloakを上回るユーザビリティ
- **Architectural Cleanliness**: idp-serverのClean Architecture準拠

## 🚨 現在の実装問題点

### **Critical Issues**

#### 1. **OAuth Protocol 汚染**
```java
// ❌ 問題のある実装 (OAuthFlowEntryService.java:315)
String invitationId = authorizationRequest.customParams().getValueAsStringOrEmpty("invitation_id");
```

**問題**:
- OAuth/OIDC認可リクエストに招待ロジックを混入
- 標準仕様逸脱により相互運用性を損なう
- プロトコルの純粋性を破る設計

#### 2. **セキュリティ脆弱性**
```java
// ❌ セキュリティ問題
httpQueryParams.add("invitation_id", id); // UUID.randomUUID() - 予測可能
String url = adminDashboardUrl.value() + "/invitation/?" + httpQueryParams.params();
```

**問題**:
- **弱い招待トークン**: UUID v4は暗号学的に十分でない（122bit entropy）
- **URL露出リスク**: ログ・リファラー・ブラウザ履歴への露出
- **HTTPS強制なし**: 盗聴リスクあり

#### 3. **UX問題**
```
現在: 招待URL → OAuth認証 → 認証完了 → 招待完了イベント
```

**問題**:
- 複雑すぎるフロー（認証と招待の混在）
- 期限切れ招待での不適切なエラー表示
- 管理者向け招待管理機能不足

#### 4. **実装不完全**
```java
// TODO send email (TenantInvitationManagementEntryService.java:93)
```

**問題**:
- EmailSenders統合未完了
- 招待メール送信機能が動作しない

## 🏗️ 実装アーキテクチャ設計

### **Full Implementation Architecture**

```
┌─ OAuth/OIDC Flow (Pure) ─────────────────────┐
│  Standard Authorization/Authentication        │
│  ❌ No invitation_id parameter               │
└───────────────────────────────────────────────┘
                    │
                    ▼
┌─ Invitation Flow (Dedicated) ─────────────────┐
│                                               │
│  ┌─ Management API ─────────────────────────┐ │
│  │ POST /v1/management/tenants/{id}/invites │ │
│  │ ↓ Create invitation                     │ │
│  │ ↓ Generate secure token (256-bit)       │ │
│  │ ↓ Send invitation email                 │ │
│  └─────────────────────────────────────────┘ │
│                    │                         │
│                    ▼                         │
│  ┌─ Public Invitation Endpoint ─────────────┐ │
│  │ GET /invitation/{secure-token}           │ │
│  │ ↓ Token validation                      │ │
│  │ ↓ Expiry check                          │ │
│  │ ↓ Landing page display                  │ │
│  └─────────────────────────────────────────┘ │
│                    │                         │
│                    ▼                         │
│  ┌─ Invitation Acceptance ──────────────────┐ │
│  │ POST /invitation/{secure-token}/accept   │ │
│  │ ↓ User registration/update               │ │
│  │ ↓ Role assignment                        │ │
│  │ ↓ Status update (accepted)               │ │
│  │ ↓ Security event logging                 │ │
│  └─────────────────────────────────────────┘ │
│                                               │
└───────────────────────────────────────────────┘
```

### **Module Structure**

```
libs/
├── idp-server-core-extension-invitation/     # 新規作成
│   ├── src/main/java/org/idp/server/invitation/
│   │   ├── token/
│   │   │   ├── SecureInvitationToken.java
│   │   │   ├── InvitationTokenGenerator.java
│   │   │   └── InvitationTokenValidator.java
│   │   ├── flow/
│   │   │   ├── InvitationHandler.java
│   │   │   ├── InvitationAcceptanceHandler.java
│   │   │   └── InvitationFlowService.java
│   │   ├── email/
│   │   │   ├── InvitationEmailTemplate.java
│   │   │   └── InvitationEmailSender.java
│   │   └── landing/
│   │       ├── InvitationLandingPageHandler.java
│   │       └── InvitationLandingPageResponse.java
│   └── src/main/resources/
│       ├── email-templates/
│       │   ├── invitation-email.html
│       │   └── invitation-email.txt
│       └── landing-page/
│           └── invitation-landing.html
│
├── idp-server-control-plane/                 # 拡張
│   └── src/main/java/org/idp/server/control_plane/
│       └── management/tenant/invitation/
│           ├── TenantInvitationManagementApi.java    # 拡張
│           ├── advanced/
│           │   ├── InvitationBulkManagementApi.java
│           │   ├── InvitationTemplateManagementApi.java
│           │   └── InvitationAnalyticsApi.java
│           └── operation/
│               ├── TenantInvitation.java             # 拡張
│               ├── SecureInvitationToken.java
│               └── InvitationSecurityEvent.java
│
├── idp-server-springboot-adapter/            # 拡張
│   └── src/main/java/org/idp/server/adapters/springboot/
│       ├── application/restapi/invitation/
│       │   ├── InvitationPublicV1Api.java           # 新規
│       │   ├── InvitationAcceptanceV1Api.java       # 新規
│       │   └── InvitationLandingV1Api.java          # 新規
│       └── tenant/invitation/
│           └── TenantInvitationManagementV1Api.java # 拡張
│
└── idp-server-use-cases/                     # 拡張
    └── src/main/java/org/idp/server/usecases/
        ├── invitation/
        │   ├── InvitationFlowEntryService.java      # 新規
        │   ├── InvitationAcceptanceEntryService.java # 新規
        │   └── InvitationLandingEntryService.java   # 新規
        └── control_plane/system_manager/
            └── TenantInvitationManagementEntryService.java # 拡張
```

## 🔐 セキュリティ実装詳細

### **Secure Token Generation**

```java
/**
 * Cryptographically secure invitation token generator.
 *
 * Generates 256-bit tokens with sufficient entropy to prevent
 * brute force attacks (2^256 combinations).
 */
public class InvitationTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    public static String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);

        // URL-safe Base64 encoding (no padding)
        return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(tokenBytes);
    }
}
```

### **Token Validation & Security**

```java
public class InvitationTokenValidator {

    public InvitationTokenValidationResult validate(String token) {
        // 1. Format validation
        if (!isValidTokenFormat(token)) {
            return InvitationTokenValidationResult.invalidFormat();
        }

        // 2. Database lookup with timing attack protection
        Optional<TenantInvitation> invitation =
            invitationRepository.findByTokenSecure(token);

        if (invitation.isEmpty()) {
            // Constant-time response to prevent timing attacks
            Thread.sleep(randomDelay());
            return InvitationTokenValidationResult.notFound();
        }

        // 3. Expiry validation
        TenantInvitation inv = invitation.get();
        if (inv.isExpired()) {
            return InvitationTokenValidationResult.expired(inv);
        }

        // 4. Status validation
        if (!inv.isPending()) {
            return InvitationTokenValidationResult.alreadyProcessed(inv);
        }

        return InvitationTokenValidationResult.valid(inv);
    }
}
```

### **HTTPS & Security Headers**

```java
@Configuration
public class InvitationSecurityConfig {

    @Bean
    public SecurityFilterChain invitationSecurityFilterChain(HttpSecurity http) {
        return http
            .securityMatcher("/invitation/**")
            .requiresChannel(channel ->
                channel.requestMatchers("/invitation/**")
                       .requiresSecure()) // HTTPS強制
            .headers(headers ->
                headers.frameOptions().DENY()
                       .contentTypeOptions().and()
                       .httpStrictTransportSecurity(hstsConfig ->
                           hstsConfig.maxAgeInSeconds(31536000))) // HSTS
            .build();
    }
}
```

## 🎨 UX Implementation

### **Progressive Disclosure Landing Page**

```html
<!-- invitation-landing.html -->
<!DOCTYPE html>
<html>
<head>
    <title>Invitation to {{tenant_name}}</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Security headers -->
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'">
</head>
<body>
    <div class="invitation-container">
        <!-- Phase 1: Welcome -->
        <div id="welcome-phase" class="phase active">
            <h1>You're invited to {{tenant_name}}</h1>
            <p>{{inviter_name}} has invited you to join {{tenant_name}} as {{role_name}}.</p>
            <button onclick="showDetails()">View Details</button>
        </div>

        <!-- Phase 2: Details -->
        <div id="details-phase" class="phase hidden">
            <h2>Invitation Details</h2>
            <ul>
                <li>Organization: {{tenant_name}}</li>
                <li>Role: {{role_name}}</li>
                <li>Invited by: {{inviter_name}}</li>
                <li>Expires: {{expiry_date}}</li>
            </ul>
            <button onclick="showAcceptance()">Continue</button>
        </div>

        <!-- Phase 3: Acceptance -->
        <div id="acceptance-phase" class="phase hidden">
            <h2>Accept Invitation</h2>
            <form id="acceptance-form">
                <p>By accepting, you agree to join {{tenant_name}} with the role {{role_name}}.</p>
                <button type="submit">Accept Invitation</button>
                <button type="button" onclick="decline()">Decline</button>
            </form>
        </div>
    </div>

    <script>
        // Progressive disclosure implementation
        function showDetails() {
            document.getElementById('welcome-phase').classList.remove('active');
            document.getElementById('welcome-phase').classList.add('hidden');
            document.getElementById('details-phase').classList.remove('hidden');
            document.getElementById('details-phase').classList.add('active');
        }

        function showAcceptance() {
            document.getElementById('details-phase').classList.remove('active');
            document.getElementById('details-phase').classList.add('hidden');
            document.getElementById('acceptance-phase').classList.remove('hidden');
            document.getElementById('acceptance-phase').classList.add('active');
        }

        document.getElementById('acceptance-form').addEventListener('submit', function(e) {
            e.preventDefault();
            // AJAX call to acceptance endpoint
            fetch('/invitation/{{secure_token}}/accept', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            }).then(response => {
                if (response.ok) {
                    window.location.href = '/invitation/success';
                } else {
                    // Handle error
                    showError('Failed to accept invitation');
                }
            });
        });
    </script>
</body>
</html>
```

### **Error Handling UX**

```java
public class InvitationErrorHandler {

    public ResponseEntity<InvitationErrorResponse> handleExpiredInvitation(
            ExpiredInvitationException ex) {
        return ResponseEntity.status(410) // 410 Gone
            .body(InvitationErrorResponse.builder()
                .error("invitation_expired")
                .message("This invitation has expired")
                .userFriendlyMessage("この招待は期限切れです。新しい招待をリクエストしてください。")
                .supportActions(List.of(
                    "contact_administrator",
                    "request_new_invitation"
                ))
                .build());
    }

    public ResponseEntity<InvitationErrorResponse> handleInvalidToken(
            InvalidTokenException ex) {
        return ResponseEntity.status(404)
            .body(InvitationErrorResponse.builder()
                .error("invitation_not_found")
                .message("Invalid invitation link")
                .userFriendlyMessage("招待リンクが無効です。正しいリンクを確認してください。")
                .supportActions(List.of(
                    "check_link",
                    "contact_support"
                ))
                .build());
    }
}
```

## 📧 Email Integration

### **EmailSenders Integration**

```java
@Service
public class InvitationEmailService {

    private final EmailSenders emailSenders;
    private final InvitationEmailTemplateService templateService;

    public void sendInvitationEmail(TenantInvitation invitation, Tenant tenant) {
        try {
            InvitationEmailContext context = InvitationEmailContext.builder()
                .recipientEmail(invitation.email())
                .tenantName(tenant.name().value())
                .roleName(invitation.roleName())
                .invitationUrl(invitation.url())
                .expiryDate(invitation.expiresAt())
                .build();

            String htmlContent = templateService.renderHtml(context);
            String textContent = templateService.renderText(context);

            EmailMessage message = EmailMessage.builder()
                .to(invitation.email())
                .subject("Invitation to " + tenant.name().value())
                .htmlBody(htmlContent)
                .textBody(textContent)
                .build();

            emailSenders.send(tenant, message);

            // Security event logging
            publishSecurityEvent(invitation, tenant, "invitation_email_sent");

        } catch (Exception e) {
            publishSecurityEvent(invitation, tenant, "invitation_email_failed");
            throw new InvitationEmailSendFailedException(
                "Failed to send invitation email", e);
        }
    }
}
```

### **Invitation Email Template**

```html
<!-- invitation-email.html -->
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Invitation to {{tenant_name}}</title>
</head>
<body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
    <div style="background: #f8f9fa; padding: 20px; border-radius: 8px;">
        <h1 style="color: #333;">You're Invited!</h1>

        <p>Hello,</p>

        <p>You have been invited to join <strong>{{tenant_name}}</strong> as a <strong>{{role_name}}</strong>.</p>

        <div style="background: white; padding: 15px; border-radius: 4px; margin: 20px 0;">
            <h3>Invitation Details:</h3>
            <ul>
                <li><strong>Organization:</strong> {{tenant_name}}</li>
                <li><strong>Role:</strong> {{role_name}}</li>
                <li><strong>Expires:</strong> {{expiry_date}}</li>
            </ul>
        </div>

        <div style="text-align: center; margin: 30px 0;">
            <a href="{{invitation_url}}"
               style="background: #007bff; color: white; padding: 12px 24px;
                      text-decoration: none; border-radius: 4px; display: inline-block;">
                Accept Invitation
            </a>
        </div>

        <p style="color: #666; font-size: 14px;">
            This invitation will expire on {{expiry_date}}.
            If you don't want to accept this invitation, you can ignore this email.
        </p>

        <p style="color: #666; font-size: 12px;">
            If you're having trouble with the button above, copy and paste the following link into your browser:
            <br>{{invitation_url}}
        </p>
    </div>
</body>
</html>
```

## 📊 API Design

### **Enhanced Management API**

```java
@RestController
@RequestMapping("/{tenant-id}/v1/management/invitations")
public class TenantInvitationManagementV1Api {

    // Enhanced create with email sending
    @PostMapping
    public ResponseEntity<TenantInvitationManagementResponse> create(
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            @RequestBody @Valid TenantInvitationCreateRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "true") boolean sendEmail,
            HttpServletRequest httpServletRequest) {

        // Standard validation + access control
        // Enhanced context creation with email sending flag
        // EmailSenders integration
        // Security event logging
    }

    // Bulk invitation
    @PostMapping("/bulk")
    public ResponseEntity<TenantInvitationBulkResponse> createBulk(
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            @RequestBody @Valid TenantInvitationBulkRequest request,
            HttpServletRequest httpServletRequest) {

        // CSV import support
        // Batch processing with progress tracking
        // Error aggregation and reporting
    }

    // Invitation analytics
    @GetMapping("/analytics")
    public ResponseEntity<TenantInvitationAnalytics> getAnalytics(
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest httpServletRequest) {

        // Conversion funnel analytics
        // Success/failure rates
        // Time-to-acceptance metrics
    }

    // Resend invitation
    @PostMapping("/{id}/resend")
    public ResponseEntity<TenantInvitationManagementResponse> resend(
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            @PathVariable("id") TenantInvitationIdentifier invitationId,
            HttpServletRequest httpServletRequest) {

        // Generate new secure token
        // Reset expiry
        // Send new email
        // Security event logging
    }

    // Cancel invitation
    @PostMapping("/{id}/cancel")
    public ResponseEntity<TenantInvitationManagementResponse> cancel(
            @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
            @PathVariable("id") TenantInvitationIdentifier invitationId,
            HttpServletRequest httpServletRequest) {

        // Status update to "cancelled"
        // Token invalidation
        // Security event logging
    }
}
```

### **Public Invitation API**

```java
@RestController
@RequestMapping("/invitation")
public class InvitationPublicV1Api {

    // Landing page
    @GetMapping("/{token}")
    public ResponseEntity<InvitationLandingResponse> getLanding(
            @PathVariable("token") String secureToken,
            HttpServletRequest request) {

        // Token validation
        // Invitation details retrieval
        // Progressive disclosure data preparation
        // Rate limiting
    }

    // Accept invitation
    @PostMapping("/{token}/accept")
    public ResponseEntity<InvitationAcceptanceResponse> accept(
            @PathVariable("token") String secureToken,
            @RequestBody @Valid InvitationAcceptanceRequest request,
            HttpServletRequest httpRequest) {

        // Token validation
        // User registration/update
        // Role assignment
        // Status update
        // Security event logging
    }

    // Decline invitation
    @PostMapping("/{token}/decline")
    public ResponseEntity<InvitationDeclineResponse> decline(
            @PathVariable("token") String secureToken,
            HttpServletRequest httpRequest) {

        // Token validation
        // Status update to "declined"
        // Security event logging
    }
}
```

## 🧪 Testing Strategy

### **Security Testing**

```java
@TestMethodOrder(OrderAnnotation.class)
class InvitationSecurityTest {

    @Test
    @Order(1)
    void testTokenUnpredictability() {
        Set<String> tokens = IntStream.range(0, 10000)
            .mapToObj(i -> InvitationTokenGenerator.generateSecureToken())
            .collect(Collectors.toSet());

        // No collisions in 10k tokens
        assertThat(tokens).hasSize(10000);

        // Minimum entropy check
        double entropy = calculateShannonEntropy(tokens);
        assertThat(entropy).isGreaterThan(6.0); // High entropy
    }

    @Test
    @Order(2)
    void testTimingAttackResistance() {
        String validToken = createValidInvitation().getSecureToken();
        String invalidToken = "invalid-token-123";

        long validTime = measureValidationTime(validToken);
        long invalidTime = measureValidationTime(invalidToken);

        // Timing difference should be minimal (< 10ms)
        assertThat(Math.abs(validTime - invalidTime)).isLessThan(10);
    }

    @Test
    @Order(3)
    void testRateLimiting() {
        String token = createValidInvitation().getSecureToken();

        // Should allow reasonable requests
        for (int i = 0; i < 10; i++) {
            ResponseEntity<?> response = invitationApi.getLanding(token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // Should rate limit excessive requests
        for (int i = 0; i < 50; i++) {
            ResponseEntity<?> response = invitationApi.getLanding(token);
        }

        ResponseEntity<?> response = invitationApi.getLanding(token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

### **UX Flow Testing**

```java
@Test
void testCompleteInvitationFlow() {
    // 1. Admin creates invitation
    TenantInvitationCreateRequest request = TenantInvitationCreateRequest.builder()
        .email("newuser@example.com")
        .roleId(roleId)
        .roleName("User")
        .sendEmail(true)
        .build();

    ResponseEntity<TenantInvitationManagementResponse> createResponse =
        managementApi.create(tenantId, request, false, true);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String invitationId = extractInvitationId(createResponse);
    String secureToken = extractSecureToken(createResponse);

    // 2. User visits invitation landing page
    ResponseEntity<InvitationLandingResponse> landingResponse =
        publicApi.getLanding(secureToken);
    assertThat(landingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    InvitationLandingData data = landingResponse.getBody().getData();
    assertThat(data.getTenantName()).isEqualTo(expectedTenantName);
    assertThat(data.getRoleName()).isEqualTo("User");
    assertThat(data.getEmail()).isEqualTo("newuser@example.com");

    // 3. User accepts invitation
    InvitationAcceptanceRequest acceptRequest = InvitationAcceptanceRequest.builder()
        .acceptTerms(true)
        .build();

    ResponseEntity<InvitationAcceptanceResponse> acceptResponse =
        publicApi.accept(secureToken, acceptRequest);
    assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 4. Verify user is created with correct role
    User user = userRepository.findByEmail(tenantId, "newuser@example.com");
    assertThat(user.exists()).isTrue();
    assertThat(user.hasRole(roleId)).isTrue();

    // 5. Verify invitation status updated
    TenantInvitation invitation = invitationRepository.find(tenantId, invitationId);
    assertThat(invitation.status()).isEqualTo("accepted");

    // 6. Verify security events logged
    List<SecurityEvent> events = securityEventRepository.findByType(
        tenantId, "invitation_accepted");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getPayload().get("invitation_id")).isEqualTo(invitationId);
}
```

## 📈 Performance & Monitoring

### **Rate Limiting Implementation**

```java
@Component
public class InvitationRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS_PER_IP = 100; // per hour
    private static final int MAX_REQUESTS_PER_TOKEN = 10; // per hour

    public boolean isAllowed(String clientIp, String token) {
        String ipKey = "invitation:rate_limit:ip:" + clientIp;
        String tokenKey = "invitation:rate_limit:token:" + token;

        long ipRequests = incrementAndExpire(ipKey, 3600);
        long tokenRequests = incrementAndExpire(tokenKey, 3600);

        return ipRequests <= MAX_REQUESTS_PER_IP &&
               tokenRequests <= MAX_REQUESTS_PER_TOKEN;
    }

    private long incrementAndExpire(String key, int ttlSeconds) {
        Long current = redisTemplate.opsForValue().increment(key);
        if (current == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return current;
    }
}
```

### **Metrics Collection**

```java
@Component
public class InvitationMetricsCollector {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter invitationsCreated;
    private final Counter invitationsAccepted;
    private final Counter invitationsDeclined;
    private final Counter invitationsExpired;

    // Timers
    private final Timer invitationAcceptanceTime;
    private final Timer emailSendTime;

    public InvitationMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.invitationsCreated = Counter.builder("invitations.created")
            .description("Number of invitations created")
            .register(meterRegistry);
        // ... other metrics
    }

    public void recordInvitationCreated(Tenant tenant) {
        invitationsCreated.increment(
            Tags.of("tenant_id", tenant.identifierValue()));
    }

    public void recordInvitationAccepted(Tenant tenant, Duration timeToAccept) {
        invitationsAccepted.increment(
            Tags.of("tenant_id", tenant.identifierValue()));
        invitationAcceptanceTime.record(timeToAccept);
    }
}
```

### **Cleanup Job**

```java
@Component
@Scheduled(fixedRate = 3600000) // Every hour
public class InvitationCleanupJob {

    private final TenantInvitationQueryRepository queryRepository;
    private final TenantInvitationCommandRepository commandRepository;
    private final InvitationMetricsCollector metricsCollector;

    public void cleanupExpiredInvitations() {
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            List<TenantInvitation> expired =
                queryRepository.findExpired(tenant, LocalDateTime.now());

            for (TenantInvitation invitation : expired) {
                // Update status to expired
                TenantInvitation updated = invitation.updateWithStatus("expired");
                commandRepository.update(tenant, updated);

                // Record metric
                metricsCollector.recordInvitationExpired(tenant);

                // Security event
                publishSecurityEvent(tenant, invitation, "invitation_expired");
            }
        }
    }
}
```

## 🚀 Migration Strategy

### **Phase 1: Infrastructure (Week 1-2)**

#### **Day 1-3: Security Foundation**
1. **Secure Token Implementation**
   ```bash
   # Create new module
   mkdir -p libs/idp-server-core-extension-invitation/src/main/java/org/idp/server/invitation/token

   # Implement classes
   touch InvitationTokenGenerator.java
   touch InvitationTokenValidator.java
   touch SecureInvitationToken.java
   ```

2. **Database Schema Migration**
   ```sql
   -- Add secure token column
   ALTER TABLE tenant_invitations
   ADD COLUMN secure_token VARCHAR(64) UNIQUE;

   -- Add indexes for performance
   CREATE INDEX idx_tenant_invitations_secure_token
   ON tenant_invitations(secure_token);

   CREATE INDEX idx_tenant_invitations_status_expires
   ON tenant_invitations(status, expires_at);
   ```

#### **Day 4-7: Core Flow Implementation**
1. **Invitation Flow Service**
2. **Public API Endpoints**
3. **Landing Page Handler**

#### **Day 8-14: Email Integration**
1. **EmailSenders Integration**
2. **Template Engine Setup**
3. **Error Handling**

### **Phase 2: UX Enhancement (Week 3-4)**

#### **Day 15-21: Landing Page**
1. **Progressive Disclosure Implementation**
2. **Responsive Design**
3. **Error Page Design**

#### **Day 22-28: Management API Enhancement**
1. **Bulk Operations**
2. **Analytics API**
3. **Resend/Cancel Operations**

### **Phase 3: Advanced Features (Week 5-8)**

#### **Week 5-6: Security & Monitoring**
1. **Rate Limiting**
2. **Security Event Integration**
3. **Metrics Collection**

#### **Week 7-8: Enterprise Features**
1. **Custom Templates**
2. **Organization-level Invitations**
3. **External IdP Integration**

### **Migration Safety**

```java
@Component
public class InvitationMigrationService {

    public void migrateExistingInvitations() {
        List<TenantInvitation> oldInvitations =
            invitationRepository.findWithoutSecureToken();

        for (TenantInvitation invitation : oldInvitations) {
            if (invitation.status().equals("pending")) {
                // Generate secure token for pending invitations
                String secureToken = InvitationTokenGenerator.generateSecureToken();
                TenantInvitation updated = invitation.withSecureToken(secureToken);
                invitationRepository.update(updated);
            }
        }
    }

    @EventListener
    public void handleOldInvitationFlow(UserLifecycleEvent event) {
        if (event.type() == UserLifecycleType.INVITE_COMPLETE) {
            // Log deprecation warning
            log.warn("Old invitation flow detected. Migration recommended.");

            // Continue with old logic for backward compatibility
            // TODO: Remove after migration complete
        }
    }
}
```

## 📋 Implementation Checklist

### **Phase 1: Critical Fixes ✅**
- [ ] **OAuth Protocol Separation**
  - [ ] Remove `invitation_id` from OAuth authorization flow
  - [ ] Create dedicated invitation endpoints
  - [ ] Update OAuth flow tests to ensure no invitation contamination

- [ ] **Secure Token Implementation**
  - [ ] `InvitationTokenGenerator` with 256-bit tokens
  - [ ] `InvitationTokenValidator` with timing attack protection
  - [ ] Database schema migration for secure_token column

- [ ] **HTTPS & Security Headers**
  - [ ] Force HTTPS for all invitation endpoints
  - [ ] Implement HSTS headers
  - [ ] Add Content Security Policy

- [ ] **EmailSenders Integration**
  - [ ] Remove TODO comment from TenantInvitationManagementEntryService
  - [ ] Implement InvitationEmailService
  - [ ] Create HTML/text email templates
  - [ ] Add email sending error handling

### **Phase 2: UX Enhancement ✅**
- [ ] **Progressive Disclosure Landing Page**
  - [ ] Three-phase landing page design
  - [ ] Mobile-responsive layout
  - [ ] Accessibility compliance (WCAG 2.1)

- [ ] **Enhanced Error Handling**
  - [ ] Expired invitation handling
  - [ ] Invalid token handling
  - [ ] User-friendly error messages
  - [ ] Support action suggestions

- [ ] **Management Dashboard Improvements**
  - [ ] Invitation status tracking
  - [ ] Bulk operations UI
  - [ ] Analytics dashboard
  - [ ] Resend/cancel functionality

### **Phase 3: Advanced Features ✅**
- [ ] **Bulk Invitation Support**
  - [ ] CSV import API
  - [ ] Progress tracking
  - [ ] Error aggregation and reporting

- [ ] **Custom Email Templates**
  - [ ] Tenant-specific templates
  - [ ] Template editor interface
  - [ ] Preview functionality

- [ ] **Organization-level Invitations**
  - [ ] Organization-scoped invitation API
  - [ ] Cross-tenant invitation support
  - [ ] Organization admin permissions

### **Testing & Quality ✅**
- [ ] **Security Testing**
  - [ ] Token unpredictability tests
  - [ ] Timing attack resistance
  - [ ] Rate limiting validation
  - [ ] HTTPS enforcement tests

- [ ] **E2E Flow Testing**
  - [ ] Complete invitation flow
  - [ ] Error scenarios
  - [ ] Edge cases (expired, invalid tokens)
  - [ ] Performance testing

- [ ] **Integration Testing**
  - [ ] EmailSenders integration
  - [ ] SecurityEvent integration
  - [ ] Database transaction testing

## 🎯 Success Metrics

### **Technical Metrics**
- **Security**: Zero token prediction attacks
- **Performance**: < 200ms invitation page load time
- **Reliability**: 99.9% email delivery success rate
- **Availability**: 99.95% invitation endpoint uptime

### **Business Metrics**
- **Conversion Rate**: Target 85% invitation acceptance rate
- **Time to Accept**: Target < 24 hours median acceptance time
- **User Satisfaction**: Target > 4.5/5 UX rating
- **Admin Efficiency**: 50% reduction in invitation management time

### **Compliance Metrics**
- **OWASP Compliance**: 100% critical vulnerability mitigation
- **OAuth Purity**: Zero RFC deviation in OAuth flows
- **Data Protection**: GDPR/privacy compliance verification
- **Audit Trail**: 100% invitation action logging

## 📚 References

### **Industry Standards**
- [RFC 6749: OAuth 2.0](https://tools.ietf.org/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OWASP Top 10 2021](https://owasp.org/www-project-top-ten/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)

### **Competitive Analysis**
- [Auth0 User Invitation API](https://auth0.com/docs/api/management/v2#!/User_Invitations)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/22.0.1/rest-api/index.html)
- [Amazon Cognito User Invitation](https://docs.aws.amazon.com/cognito/latest/developerguide/how-to-create-user-accounts.html)

### **idp-server Documentation**
- [Management API Framework](./management-api-framework.md)
- [Security Event Framework](../content_06_developer-guide/security-event-framework.md)
- [Multi-tenant Architecture](../content_06_developer-guide/multi-tenant-architecture.md)

---

*Implementation plan created: 2025-01-16*
*Ready for Phase 1 implementation start*