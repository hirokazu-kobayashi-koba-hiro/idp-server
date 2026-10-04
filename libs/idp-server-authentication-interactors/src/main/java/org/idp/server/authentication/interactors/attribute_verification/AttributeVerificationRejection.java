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

package org.idp.server.authentication.interactors.attribute_verification;

import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
import org.idp.server.core.openid.authentication.AuthenticationInteractionStatus;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.authentication.OperationType;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Every way an attribute verification can be refused, and what each one tells the caller.
 *
 * <p>Whether a refusal is recorded under the interaction's name is decided here, once, because it
 * is what makes it count: the name's breakdown is what the per-transaction attempt limit and a
 * policy's {@code lock_conditions} read. A malformed request or one that names no interaction is
 * the caller's fault, not a guess, so it is left unnamed.
 */
enum AttributeVerificationRejection {
  USER_NOT_IDENTIFIED(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      "invalid_request",
      "attribute verification requires an identified user.",
      false),
  NOT_CONFIGURED(
      AuthenticationInteractionStatus.SERVER_ERROR,
      "server_error",
      "attribute verification is not configured.",
      true),
  UNKNOWN_INTERACTION(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      "invalid_request",
      "interaction is missing or not configured.",
      false),
  INVALID_INPUT(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      "invalid_request",
      "each input must be sent as a string.",
      false),
  /** The error code is the tenant's ({@code execution.details.error}). */
  CONDITION_NOT_SATISFIED(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      null,
      "the account does not meet the conditions for this step.",
      true),
  TOO_MANY_ATTEMPTS(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      "too_many_attempts",
      "Too many failed attempts. Please try again later.",
      true),
  MISMATCH(
      AuthenticationInteractionStatus.CLIENT_ERROR,
      "attribute_mismatch",
      "the submitted values do not match the registered attributes.",
      true);

  AuthenticationInteractionStatus status;
  String error;
  String description;
  boolean recordedUnderInteraction;

  AttributeVerificationRejection(
      AuthenticationInteractionStatus status,
      String error,
      String description,
      boolean recordedUnderInteraction) {
    this.status = status;
    this.error = error;
    this.description = description;
    this.recordedUnderInteraction = recordedUnderInteraction;
  }

  /**
   * @param user the user the transaction is about, or null when none is established yet
   * @param interaction the interaction's name, or null when it is not known yet
   */
  AuthenticationInteractionRequestResult toResult(
      AuthenticationInteractionType type, String method, User user, String interaction) {
    return toResult(type, method, user, interaction, error);
  }

  /**
   * As {@link #toResult(AuthenticationInteractionType, String, User, String)}, with the tenant's
   * error code.
   */
  AuthenticationInteractionRequestResult toResult(
      AuthenticationInteractionType type,
      String method,
      User user,
      String interaction,
      String errorCode) {
    AuthenticationInteractionRequestResult result =
        new AuthenticationInteractionRequestResult(
            status,
            type,
            OperationType.VERIFICATION,
            method,
            user,
            Map.of("error", errorCode, "error_description", description),
            DefaultSecurityEventType.attribute_verification_failure);
    if (recordedUnderInteraction && interaction != null) {
      result.setInteractionName(interaction);
    }
    return result;
  }
}
