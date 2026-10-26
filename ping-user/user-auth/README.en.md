# user-auth: Authentication Primitives

`user-auth` provides **user-table-agnostic** authentication foundation capabilities: password complexity, login failure lockout and arithmetic CAPTCHA. `user-pc` and `user-app` both reuse it, but each decides "which table to query and what the login domain is".

It does not query MySQL or generate tokens; it depends only on Redis and configuration.

## 1. What It Contains

| Class | Purpose |
| --- | --- |
| `config.PingSecurityProperties` | `ping.security.*` configuration binding: registration switch, default role, login lockout, password policy, CAPTCHA |
| `security.PasswordPolicy` | Password complexity validation (length + number of character categories) |
| `security.LoginAttemptService` / `impl.LoginAttemptServiceImpl` | Login failure counting, account lockout, cleanup on success |
| `security.CaptchaService` / `impl.CaptchaServiceImpl` | One-time arithmetic CAPTCHA; answer stored in Redis |

Dependencies:

```
user-auth
 ├─ user-common
 ├─ spring-boot-starter
 ├─ spring-boot-starter-data-redis
 └─ lombok
```

## 2. Configuration

```yaml
ping:
  security:
    registration-enabled: true    # Whether backend self-registration is enabled
    default-role-code: guest      # Default role assigned after registration
    login:
      max-attempts: 5             # Lock after 5 consecutive failures
      lock-minutes: 15
    password:
      min-length: 8
      max-length: 64
      require-categories: 3       # At least 3 of uppercase/lowercase/digit/special characters
    captcha:
      enabled: false              # When enabled, login must include a CAPTCHA
      expire-minutes: 2
```

`PingSecurityProperties` itself only has `@ConfigurationProperties` and **is not registered automatically**. `user-service` binds it in `SecurityConfig` via `@EnableConfigurationProperties(PingSecurityProperties.class)`; if you use `user-auth` in another startup module, you must add this line yourself.

## 3. Usage

### 3.1 Password Complexity

```java
@RequiredArgsConstructor
public class SomeService {
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder; // Provided by ping-web-spring-boot-starter (BCrypt)

    public void register(String rawPassword) {
        passwordPolicy.validate(rawPassword);          // Throws RuntimeException if it does not meet the policy
        String hash = passwordEncoder.encode(rawPassword);
        // ... persist
    }
}
```

Validation rules:

- Not empty;
- Length between `min-length` and `max-length`;
- At least `require-categories` of uppercase letters, lowercase letters, digits and special characters.

### 3.2 Login Failure Lockout

```java
if (loginAttemptService.isLocked(username)) {
    long seconds = loginAttemptService.remainingLockSeconds(username);
    throw new RuntimeException("Account is locked. Please try again in " + seconds + " seconds.");
}

if (passwordMismatch) {
    int remaining = loginAttemptService.recordFailure(username);
    if (remaining <= 0) {
        throw new RuntimeException("Too many failed login attempts; the account has been locked.");
    }
    throw new RuntimeException("Incorrect username or password; " + remaining + " attempts remaining.");
}

// After successful login
loginAttemptService.clear(username);
```

Redis keys:

```
ping:security:login:fail:{username}   # Failure count
ping:security:login:lock:{username}   # Lock marker
```

### 3.3 CAPTCHA

```java
// Generate
CaptchaService.Captcha captcha = captchaService.create();
// captcha.captchaId() is returned to the frontend; captcha.question() is e.g. "3 + 5 = ?"

// Verify (one-time)
boolean ok = captchaService.verify(captchaId, userInput);
```

The answer is stored in:

```
ping:security:captcha:{captchaId}
```

Validation **deletes before comparing**, so the same CAPTCHA can only be used once, whether correct or not.

## 4. How PC and App Reuse It

Both domains share the same `PasswordPolicy` / `CaptchaService` / configuration, but their login flows differ:

| | `AuthController` in `user-pc` | `AppAuthController` in `user-app` |
| --- | --- | --- |
| Login lockout | Uses `LoginAttemptService` | **Currently not used** |
| CAPTCHA | Validated according to `ping.security.captcha.enabled` | **Currently not used** |
| Registration switch | Checks `registration-enabled` | **Currently not checked** |
| Login domain | `StpUtil` | `StpAppUtil` |
| Password hashing | BCrypt | BCrypt |

If App also needs credential-stuffing protection / CAPTCHA, it must be integrated explicitly in `AppAuthServiceImpl` or `AppAuthController`; do not expect configuration to take effect automatically.

Example (adding lockout to App login):

```java
if (loginAttemptService.isLocked(phone)) {
    throw new RuntimeException("Too many attempts; please try again later.");
}
try {
    AppUser user = appAuthService.login(phone, password);
    loginAttemptService.clear(phone);
    StpAppUtil.login(user.getId());
} catch (RuntimeException e) {
    loginAttemptService.recordFailure(phone);
    throw e;
}
```

## 5. Pitfalls

### 5.1 Including Only `user-auth` Without a `PasswordEncoder`

`PasswordPolicy` only validates plaintext rules and does not handle hashing. `PasswordEncoder` is provided by `CommonSecurityAutoConfiguration` in `ping-web-spring-boot-starter`. When only `user-auth` is included, either define `PasswordEncoder` yourself or include the web starter too.

### 5.2 Assuming `registration-enabled=false` Disables App Registration

This switch only affects `AuthController.register` in `user-pc`. The registration endpoint in `user-app` does not read it; to disable it, add a check in `AppAuthController.register` or `AppAuthServiceImpl`.

### 5.3 Lockout Granularity Is Username/Phone, Not IP

The key for `recordFailure(username)` directly concatenates the passed value. PC login passes `username`; if App integrates it, it passes `phone`. This means:

- An attacker can switch usernames to bypass per-account lockout;
- Attempts for the same username from multiple IPs share the count.

If you need an IP dimension or CAPTCHA escalation, you must extend the key design yourself.

### 5.4 Degraded Behavior When Redis Is Unavailable

`LoginAttemptServiceImpl` catches all Redis exceptions:

- Lock lookup fails → treated as "not locked";
- Failure recording fails → returns `maxAttempts` (not locked);
- CAPTCHA write fails → only logs; `create()` still returns the question, but `verify()` always fails.

This is the trade-off for "keeping the login entry available": a Redis failure does not lock everyone out, but the defense is weaker. Production should monitor Redis availability.

### 5.5 CAPTCHA Answers Leaked in Logs/Responses

The `Captcha` record only contains `captchaId` and the question, no answer; the answer is only in Redis. Do not return the value from Redis to the frontend, and do not print `captchaId + answer` in logs.

### 5.6 Changing the Password Policy Prevents Old Users from Logging In

`PasswordPolicy` is only validated during **registration/password change/password reset**; login only does BCrypt comparison. Increasing `min-length` does not immediately prevent old users from logging in; however, if an administrator resets a password, the new password must meet the current policy.

## 6. Dependency Usage

```xml
<dependency>
    <groupId>com.ping.user</groupId>
    <artifactId>user-auth</artifactId>
</dependency>
```

Usually business modules obtain it indirectly through `user-pc` / `user-app`; reference it directly only when the authentication primitives need to be reused separately.
