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

package org.idp.server.authentication.interactors.email;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.oidc.authentication.*;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.UserStatus;
import org.idp.server.core.oidc.identity.exception.UserTooManyFoundResultException;
import org.idp.server.core.oidc.identity.repository.UserQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.notification.email.EmailSendResult;
import org.idp.server.platform.notification.email.EmailSender;
import org.idp.server.platform.notification.email.EmailSenders;
import org.idp.server.platform.notification.email.EmailSendingRequest;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

public class EmailAuthenticationChallengeInteractor implements AuthenticationInteractor {

  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  AuthenticationInteractionCommandRepository interactionCommandRepository;
  EmailSenders emailSenders;

  public EmailAuthenticationChallengeInteractor(
      AuthenticationConfigurationQueryRepository configurationQueryRepository,
      AuthenticationInteractionCommandRepository interactionCommandRepository,
      EmailSenders emailSenders) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.interactionCommandRepository = interactionCommandRepository;
    this.emailSenders = emailSenders;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      UserQueryRepository userQueryRepository) {

    try {

      EmailAuthenticationConfiguration emailAuthenticationConfiguration =
          configurationQueryRepository.get(tenant, "email", EmailAuthenticationConfiguration.class);

      String email = resolveEmail(transaction, request);

      if (email.isEmpty()) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "invalid_request");
        response.put("error_description", "email is unspecified.");

        return new AuthenticationInteractionRequestResult(
            AuthenticationInteractionStatus.CLIENT_ERROR,
            type,
            response,
            DefaultSecurityEventType.email_verification_failure);
      }

      String providerId = request.optValueAsString("provider_id", "idp-server");
      User user = resolveUser(tenant, transaction, email, providerId, userQueryRepository);

      OneTimePassword oneTimePassword = OneTimePasswordGenerator.generate();
      String sender = emailAuthenticationConfiguration.sender();
      EmailVerificationTemplate emailVerificationTemplate =
          emailAuthenticationConfiguration.findTemplate(
              request.optValueAsString("email_template", "authentication"));
      String subject = emailVerificationTemplate.subject();
      int retryCountLimitation = emailAuthenticationConfiguration.retryCountLimitation();
      int expireSeconds = emailAuthenticationConfiguration.expireSeconds();

      String body =
          emailVerificationTemplate.interpolateBody(oneTimePassword.value(), expireSeconds);

      EmailSendingRequest emailSendingRequest =
          new EmailSendingRequest(sender, email, subject, body);

      EmailSender emailSender = emailSenders.get(emailAuthenticationConfiguration.senderType());
      EmailSendResult sendResult =
          emailSender.send(emailSendingRequest, emailAuthenticationConfiguration.setting());

      if (sendResult.isError()) {

        return AuthenticationInteractionRequestResult.serverError(
            sendResult.data(), type, DefaultSecurityEventType.email_verification_request_failure);
      }

      EmailVerificationChallenge emailVerificationChallenge =
          EmailVerificationChallenge.create(oneTimePassword, retryCountLimitation, expireSeconds);

      interactionCommandRepository.register(
          tenant, transaction.identifier(), "email", emailVerificationChallenge);

      return new AuthenticationInteractionRequestResult(
          AuthenticationInteractionStatus.SUCCESS,
          type,
          user,
          new Authentication(),
          Map.of(),
          DefaultSecurityEventType.email_verification_request_success);
    } catch (UserTooManyFoundResultException tooManyFoundResultException) {

      Map<String, Object> response =
          Map.of(
              "error",
              "invalid_request",
              "error_description",
              "too many users found for email: " + request.getValueAsString("email"));
      return AuthenticationInteractionRequestResult.clientError(
          response, type, DefaultSecurityEventType.email_verification_request_failure);
    }
  }

  private User resolveUser(
      Tenant tenant,
      AuthenticationTransaction transaction,
      String email,
      String providerId,
      UserQueryRepository userQueryRepository) {

    if (transaction.hasUser()) {
      User user = transaction.user();
      user.setEmail(email);
      return user;
    }

    User existingUser = userQueryRepository.findByEmail(tenant, email, providerId);
    if (existingUser.exists()) {
      return existingUser;
    }

    User user = new User();
    String id = UUID.randomUUID().toString();
    user.setSub(id);
    user.setProviderUserId(id);
    user.setEmail(email);
    user.setStatus(UserStatus.REGISTERED);

    return user;
  }

  private String resolveEmail(
      AuthenticationTransaction transaction, AuthenticationInteractionRequest request) {
    if (request.containsKey("email")) {
      return request.getValueAsString("email");
    }
    if (transaction.hasUser()) {
      User user = transaction.user();
      return user.email();
    }
    return "";
  }
}
