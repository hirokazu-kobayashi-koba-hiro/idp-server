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

package org.idp.server.core.openid.identity.contact;

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
 * Self-service contact verification and change (Issue #1416).
 *
 * <p>Serves email and phone from one code round-trip. Everything channel-specific — required scope,
 * template, which attribute is written, which audit events fire — lives on {@link
 * ContactVerificationOperation}, so the only branching here is choosing the repository method.
 *
 * <p>A verify proves the value already on the account is reachable and commits {@code *_verified}
 * and nothing else. A change proves a new value is reachable and then makes it the account's value,
 * which under a matching identity policy also moves {@code preferred_username}, the login
 * identifier.
 *
 * <p>Deliberately not built on {@code AuthenticationTransaction} / {@code
 * AuthenticationInteractor}: that machinery drives parties who hold no credentials yet, so its
 * interaction endpoints are unauthenticated by design. An already-authenticated profile mutation
 * must not be reachable through them, so this owns its challenge state instead.
 */
public class ContactVerificationService {

  ContactVerificationChallengeRepository challengeRepository;
  ContactVerificationCodeSender codeSender;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(ContactVerificationService.class);

  public ContactVerificationService(
      ContactVerificationChallengeRepository challengeRepository,
      ContactVerificationCodeSender codeSender,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository) {
    this.challengeRepository = challengeRepository;
    this.codeSender = codeSender;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
  }

  /** Issues a code to the operation's target address and stores the challenge. */
  public ContactVerificationResponse request(
      Tenant tenant,
      User user,
      ContactVerificationOperation operation,
      ContactVerificationRequest request) {

    TargetValue target = resolveTarget(operation, user, request);
    if (target.isRejected()) {
      return target.rejection();
    }

    OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
    if (!codeSender.send(tenant, operation, target.value(), oneTimePassword.value())) {
      log.warn("Contact verification code sending failed. operation={}", operation.value());
      return ContactVerificationResponse.requestFailure(
          "failed to send the verification code.", operation);
    }

    ContactVerificationChallenge challenge =
        ContactVerificationChallenge.create(
            user.userIdentifier(),
            operation,
            target.value(),
            oneTimePassword.value(),
            codeSender.expireSeconds(tenant, operation));
    challengeRepository.register(tenant, challenge);

    return ContactVerificationResponse.challengeIssued(challenge.identifier(), operation);
  }

  /** Verifies the code and commits what the operation is allowed to commit. */
  public ContactVerificationResponse verify(
      Tenant tenant,
      User user,
      ContactVerificationOperation operation,
      ContactVerificationChallengeIdentifier challengeIdentifier,
      ContactVerificationRequest request) {

    // Ownership is part of the lookup, not a check afterwards, and the row is locked so two
    // concurrent verifies cannot both consume the code.
    ContactVerificationChallenge challenge =
        challengeRepository.findForUpdate(tenant, challengeIdentifier, user.userIdentifier());

    if (!challenge.exists() || challenge.operation() != operation) {
      return ContactVerificationResponse.notFound(operation);
    }

    ContactVerificationResponse codeFailure = verifyCode(tenant, challenge, request, operation);
    if (codeFailure != null) {
      return codeFailure;
    }

    return commit(tenant, user, challenge, operation);
  }

  private ContactVerificationResponse commit(
      Tenant tenant,
      User user,
      ContactVerificationChallenge challenge,
      ContactVerificationOperation operation) {

    if (operation.isVerify()) {
      // The value is unchanged by definition, so only the claim moves. Writing the value or
      // preferred_username here would let the openid-gated half touch what the change scope exists
      // to gate, and would re-assert a snapshot over any out-of-band correction.
      operation.markVerified(user);
      updateVerified(tenant, user, operation);
      challengeRepository.delete(tenant, challenge.identifier());
      return ContactVerificationResponse.committed(user.toMinimalizedMap(), operation);
    }

    operation.applyTarget(user, challenge.targetValue());
    operation.markVerified(user);
    // Under a matching identity policy the login identifier tracks this attribute, the same
    // recomputation registration and management updates perform (Issue #729).
    user.applyIdentityPolicy(tenant.identityPolicyConfig());

    try {
      new UserVerifier(userQueryRepository).verify(tenant, user);
    } catch (UserDuplicateException e) {
      log.warn("Contact change rejected: preferred_username already in use.");
      return ContactVerificationResponse.failure("new_value is already in use.", operation);
    }

    updateValue(tenant, user, operation);
    // A committed value invalidates every other outstanding challenge of this user, so a stale
    // one cannot later move it back or elsewhere.
    challengeRepository.deleteAllBy(tenant, user.userIdentifier());

    return ContactVerificationResponse.committed(user.toMinimalizedMap(), operation);
  }

  /** The only channel branching in this class: which partial update to run. */
  private void updateVerified(Tenant tenant, User user, ContactVerificationOperation operation) {
    switch (operation.channel()) {
      case EMAIL -> userCommandRepository.updateEmailVerified(tenant, user);
      case PHONE -> userCommandRepository.updatePhoneNumberVerified(tenant, user);
    }
  }

  private void updateValue(Tenant tenant, User user, ContactVerificationOperation operation) {
    switch (operation.channel()) {
      case EMAIL -> userCommandRepository.updateEmail(tenant, user);
      case PHONE -> userCommandRepository.updatePhoneNumber(tenant, user);
    }
  }

  /** Returns a rejection when the code is expired, exhausted or wrong; otherwise null. */
  private ContactVerificationResponse verifyCode(
      Tenant tenant,
      ContactVerificationChallenge challenge,
      ContactVerificationRequest request,
      ContactVerificationOperation operation) {

    if (challenge.isExpired()) {
      challengeRepository.delete(tenant, challenge.identifier());
      return ContactVerificationResponse.failure("verification code is expired.", operation);
    }

    if (challenge.exceededRetryLimit(codeSender.retryCountLimitation(tenant, operation))) {
      challengeRepository.delete(tenant, challenge.identifier());
      return ContactVerificationResponse.failure(
          "verification code retry limit exceeded.", operation);
    }

    if (!challenge.matches(request.verificationCode())) {
      challengeRepository.countUpAttempts(tenant, challenge.countUpAttempts());
      return ContactVerificationResponse.failure("verification code is unmatched.", operation);
    }

    return null;
  }

  private TargetValue resolveTarget(
      ContactVerificationOperation operation, User user, ContactVerificationRequest request) {

    if (operation.isVerify()) {
      // No caller-supplied recipient at all: this half cannot be redirected, whatever the body
      // says.
      if (!operation.hasCurrentValue(user)) {
        return TargetValue.rejected(
            ContactVerificationResponse.requestFailure(
                String.format("the account has no %s to verify.", operation.channel().value()),
                operation));
      }
      return TargetValue.of(operation.currentValue(user));
    }

    JsonSchemaValidationResult validationResult =
        new ContactTargetValidator(request, operation).validate();
    if (!validationResult.isValid()) {
      return TargetValue.rejected(
          ContactVerificationResponse.invalidCandidate(
              "new_value is unspecified or invalid format.", validationResult.errors(), operation));
    }

    String newValue = request.newValue();
    // Compared as stored, without normalisation: preferred_username uniqueness is exact, so any
    // difference is a real change.
    if (newValue.equals(operation.currentValue(user))) {
      return TargetValue.rejected(
          ContactVerificationResponse.requestFailure(
              "new_value is the current value. use the verification endpoint instead.", operation));
    }
    return TargetValue.of(newValue);
  }

  /** Either the value a code should go to, or the reason there is none. */
  private record TargetValue(String value, ContactVerificationResponse rejection) {

    static TargetValue of(String value) {
      return new TargetValue(value, null);
    }

    static TargetValue rejected(ContactVerificationResponse rejection) {
      return new TargetValue(null, rejection);
    }

    boolean isRejected() {
      return rejection != null;
    }
  }
}
