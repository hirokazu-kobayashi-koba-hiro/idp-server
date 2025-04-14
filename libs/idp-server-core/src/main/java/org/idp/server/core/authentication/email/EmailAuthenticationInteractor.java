package org.idp.server.core.authentication.email;

import java.util.*;
import org.idp.server.core.authentication.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class EmailAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationTransactionCommandRepository commandRepository;
  AuthenticationTransactionQueryRepository queryRepository;

  public EmailAuthenticationInteractor(
      AuthenticationTransactionCommandRepository commandRepository,
      AuthenticationTransactionQueryRepository queryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationInteractionResult interact(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    EmailVerificationChallenge emailVerificationChallenge =
        queryRepository.get(
            tenant, authenticationTransactionIdentifier, "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult =
        emailVerificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpEmailVerificationChallenge =
          emailVerificationChallenge.countUp();
      commandRepository.update(
          tenant, authenticationTransactionIdentifier, "email", countUpEmailVerificationChallenge);

      return new AuthenticationInteractionResult(
          AuthenticationInteractionStatus.CLIENT_ERROR,
          type,
          oAuthSession.user(),
          oAuthSession.authentication(),
          verificationResult.response(),
          DefaultSecurityEventType.email_verification_failure);
    }

    User user = oAuthSession.user();

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("otp")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new AuthenticationInteractionResult(
        AuthenticationInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.email_verification_success);
  }
}
