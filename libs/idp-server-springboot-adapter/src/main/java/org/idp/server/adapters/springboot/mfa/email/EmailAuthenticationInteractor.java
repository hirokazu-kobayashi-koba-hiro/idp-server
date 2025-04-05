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

  MfaTransactionCommandRepository commandRepository;
  MfaTransactionQueryRepository queryRepository;

  public EmailAuthenticationInteractor(
      MfaTransactionCommandRepository commandRepository,
      MfaTransactionQueryRepository queryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
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
        queryRepository.get(mfaTransactionIdentifier, "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult =
        emailVerificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpEmailVerificationChallenge =
          emailVerificationChallenge.countUp();
      commandRepository.update(
          mfaTransactionIdentifier, "email", countUpEmailVerificationChallenge);

      return new MfaInteractionResult(
          MfaInteractionStatus.CLIENT_ERROR,
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

    return new MfaInteractionResult(
        MfaInteractionStatus.SUCCESS,
        type,
        user,
        authentication,
        response,
        DefaultSecurityEventType.email_verification_success);
  }
}
