/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.identity.email;

import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserVerifier;
import org.idp.server.core.openid.identity.exception.UserDuplicateException;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.platform.json.schema.JsonSchemaValidationResult;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.random.OneTimePassword;
import org.idp.server.platform.random.OneTimePasswordGenerator;

/**
 * Self-service email verification and change (Issue #1416).
 *
 * <p>Two operations that share a code round-trip but not a privilege level:
 *
 * <ol>
 *   <li>{@link EmailVerificationOperation#VERIFY} — proves the address already on the account is
 *       reachable. Commits {@code email_verified} and nothing else.
 *   <li>{@link EmailVerificationOperation#CHANGE} — proves a new address is reachable, then makes
 *       it the account's address, which under an EMAIL identity policy also moves {@code
 *       preferred_username}, the login identifier.
 * </ol>
 *
 * <p>Deliberately not built on {@code AuthenticationTransaction} / {@code
 * AuthenticationInteractor}: that machinery drives parties who hold no credentials yet, so its
 * interaction endpoints are unauthenticated by design. An already-authenticated profile mutation
 * must not be reachable through them, so this owns its challenge state instead.
 */
public class EmailVerificationService {

  EmailVerificationChallengeRepository challengeRepository;
  EmailVerificationCodeSender codeSender;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(EmailVerificationService.class);

  public EmailVerificationService(
      EmailVerificationChallengeRepository challengeRepository,
      EmailVerificationCodeSender codeSender,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository) {
    this.challengeRepository = challengeRepository;
    this.codeSender = codeSender;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
  }

  /** Issues a code to the operation's target address and stores the challenge. */
  public EmailVerificationResponse request(
      Tenant tenant,
      User user,
      EmailVerificationOperation operation,
      EmailVerificationRequest request) {

    TargetEmail target = resolveTarget(operation, user, request);
    if (target.isRejected()) {
      return target.rejection();
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    if (!codeSender.send(tenant, operation, target.value(), oneTimePassword.value())) {
      log.warn("Email verification code sending failed. operation={}", operation.value());
      return EmailVerificationResponse.requestFailure(
          "failed to send the verification code.", operation);
    }

    EmailVerificationChallenge challenge =
        EmailVerificationChallenge.create(
            user.userIdentifier(),
            operation,
            target.value(),
            oneTimePassword.value(),
            codeSender.expireSeconds(tenant));
    challengeRepository.register(tenant, challenge);

    return EmailVerificationResponse.challengeIssued(challenge.identifier(), operation);
  }

  /** Verifies the code and commits what the operation is allowed to commit. */
  public EmailVerificationResponse verify(
      Tenant tenant,
      User user,
      EmailVerificationOperation operation,
      EmailVerificationChallengeIdentifier challengeIdentifier,
      EmailVerificationRequest request) {

    // Ownership is part of the lookup, not a check afterwards, and the row is locked so two
    // concurrent verifies cannot both consume the code.
    EmailVerificationChallenge challenge =
        challengeRepository.findForUpdate(tenant, challengeIdentifier, user.userIdentifier());

    if (!challenge.exists() || challenge.operation() != operation) {
      return EmailVerificationResponse.notFound(operation);
    }

    EmailVerificationResponse codeFailure = verifyCode(tenant, challenge, request, operation);
    if (codeFailure != null) {
      return codeFailure;
    }

    return commit(tenant, user, challenge, operation);
  }

  private EmailVerificationResponse commit(
      Tenant tenant,
      User user,
      EmailVerificationChallenge challenge,
      EmailVerificationOperation operation) {

    if (operation.isVerify()) {
      // The address is unchanged by definition, so only the claim moves. Writing email /
      // preferred_username here would let the openid-gated half touch columns that email:change
      // exists to gate, and would re-assert a snapshot over any out-of-band correction.
      user.setEmailVerified(true);
      userCommandRepository.updateEmailVerified(tenant, user);
      challengeRepository.delete(tenant, challenge.identifier());
      return EmailVerificationResponse.committed(user.toMinimalizedMap(), operation);
    }

    user.setEmail(challenge.targetEmail());
    user.setEmailVerified(true);
    // Under an EMAIL identity policy the login identifier tracks the email, the same recomputation
    // registration and management updates perform (Issue #729).
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    try {
      new UserVerifier(userQueryRepository).verify(tenant, user);
    } catch (UserDuplicateException e) {
      log.warn("Email change rejected: preferred_username already in use.");
      return EmailVerificationResponse.failure("new_email is already in use.", operation);
    }

    userCommandRepository.updateEmail(tenant, user);
    // A committed address invalidates every other outstanding challenge of this user, so a stale
    // one cannot later move the address back or elsewhere.
    challengeRepository.deleteAllBy(tenant, user.userIdentifier());

    return EmailVerificationResponse.committed(user.toMinimalizedMap(), operation);
  }

  /** Returns a rejection when the code is expired, exhausted or wrong; otherwise null. */
  private EmailVerificationResponse verifyCode(
      Tenant tenant,
      EmailVerificationChallenge challenge,
      EmailVerificationRequest request,
      EmailVerificationOperation operation) {

    if (challenge.isExpired()) {
      challengeRepository.delete(tenant, challenge.identifier());
      return EmailVerificationResponse.failure("verification code is expired.", operation);
    }

    if (challenge.exceededRetryLimit(codeSender.retryCountLimitation(tenant))) {
      challengeRepository.delete(tenant, challenge.identifier());
      return EmailVerificationResponse.failure(
          "verification code retry limit exceeded.", operation);
    }

    if (!challenge.matches(request.verificationCode())) {
      challengeRepository.countUpAttempts(tenant, challenge.countUpAttempts());
      return EmailVerificationResponse.failure("verification code is unmatched.", operation);
    }

    return null;
  }

  private TargetEmail resolveTarget(
      EmailVerificationOperation operation, User user, EmailVerificationRequest request) {

    if (operation.isVerify()) {
      // No caller-supplied recipient at all: this half cannot be redirected, whatever the body
      // says.
      if (!user.hasEmail()) {
        return TargetEmail.rejected(
            EmailVerificationResponse.requestFailure(
                "the account has no email address to verify.", operation));
      }
      return TargetEmail.of(user.email());
    }

    JsonSchemaValidationResult validationResult =
        new EmailChangeRequestValidator(request).validate();
    if (!validationResult.isValid()) {
      return TargetEmail.rejected(
          EmailVerificationResponse.invalidCandidate(
              "new_email is unspecified or invalid format.", validationResult.errors(), operation));
    }

    String newEmail = request.newEmail();
    // Case-sensitive on purpose: email is stored as-is and preferred_username uniqueness is
    // case-sensitive, so a case-only difference is a real change.
    if (newEmail.equals(user.email())) {
      return TargetEmail.rejected(
          EmailVerificationResponse.requestFailure(
              "new_email is the current address. use the email verification endpoint instead.",
              operation));
    }
    return TargetEmail.of(newEmail);
  }

  /** Either the address a code should go to, or the reason there is none. */
  private record TargetEmail(String value, EmailVerificationResponse rejection) {

    static TargetEmail of(String value) {
      return new TargetEmail(value, null);
    }

    static TargetEmail rejected(EmailVerificationResponse rejection) {
      return new TargetEmail(null, rejection);
    }

    boolean isRejected() {
      return rejection != null;
    }
  }
}
