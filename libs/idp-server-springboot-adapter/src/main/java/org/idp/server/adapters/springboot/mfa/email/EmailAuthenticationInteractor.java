package org.idp.server.adapters.springboot.mfa.email;

import java.util.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.mfa.*;
import org.idp.server.core.mfa.email.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class EmailAuthenticationInteractor implements MfaInteractor {

  MfaTransactionQueryRepository transactionQueryRepository;

  public EmailAuthenticationInteractor(MfaTransactionQueryRepository transactionQueryRepository) {
    this.transactionQueryRepository = transactionQueryRepository;
  }

  @Override
  public MfaInteractionResult interact(
      Tenant tenant,
      MfaTransactionIdentifier mfaTransactionIdentifier,
      MfaInteractionType type,
      MfaInteractionRequest request,
      OAuthSession oAuthSession,
      UserRepository userRepository) {

    EmailVerificationChallenge emailVerificationChallenge =
        transactionQueryRepository.get(
            mfaTransactionIdentifier, "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    if (!emailVerificationChallenge.match(verificationCode)) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "email verification code is not matched");

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
          type,
          oAuthSession.user(),
          oAuthSession.authentication(),
          response,
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

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.email_verification_success);
  }
}
