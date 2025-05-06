package org.idp.server.core.authentication.email;

import java.util.*;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class EmailAuthenticationInteractor implements AuthenticationInteractor {

  AuthenticationInteractionCommandRepository commandRepository;
  AuthenticationInteractionQueryRepository queryRepository;

  public EmailAuthenticationInteractor(AuthenticationInteractionCommandRepository commandRepository, AuthenticationInteractionQueryRepository queryRepository) {
    this.commandRepository = commandRepository;
    this.queryRepository = queryRepository;
  }

  @Override
  public AuthenticationInteractionRequestResult interact(Tenant tenant, AuthorizationIdentifier authorizationIdentifier, AuthenticationInteractionType type, AuthenticationInteractionRequest request, AuthenticationTransaction transaction, UserQueryRepository userQueryRepository) {

    EmailVerificationChallenge emailVerificationChallenge = queryRepository.get(tenant, authorizationIdentifier, "email", EmailVerificationChallenge.class);
    String verificationCode = request.optValueAsString("verification_code", "");

    EmailVerificationResult verificationResult = emailVerificationChallenge.verify(verificationCode);

    if (verificationResult.isFailure()) {

      EmailVerificationChallenge countUpEmailVerificationChallenge = emailVerificationChallenge.countUp();
      commandRepository.update(tenant, authorizationIdentifier, "email", countUpEmailVerificationChallenge);

      return new AuthenticationInteractionRequestResult(AuthenticationInteractionStatus.CLIENT_ERROR, type, transaction.user(), new Authentication(), verificationResult.response(), DefaultSecurityEventType.email_verification_failure);
    }

    User user = transaction.user();

    Authentication authentication = new Authentication().setTime(SystemDateTime.now()).addMethods(new ArrayList<>(List.of("otp"))).addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    Map<String, Object> response = new HashMap<>();
    response.put("user", user.toMap());
    response.put("authentication", authentication.toMap());

    return new AuthenticationInteractionRequestResult(AuthenticationInteractionStatus.SUCCESS, type, user, authentication, response, DefaultSecurityEventType.email_verification_success);
  }
}
