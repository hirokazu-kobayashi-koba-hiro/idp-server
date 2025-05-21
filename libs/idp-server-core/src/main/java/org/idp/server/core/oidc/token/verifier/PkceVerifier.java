package org.idp.server.core.oidc.token.verifier;

import org.idp.server.basic.type.pkce.CodeChallenge;
import org.idp.server.basic.type.pkce.CodeVerifier;
import org.idp.server.core.oidc.pkce.CodeChallengeCalculator;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class PkceVerifier {

  TokenRequestContext tokenRequestContext;
  AuthorizationRequest authorizationRequest;

  public PkceVerifier(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    this.tokenRequestContext = tokenRequestContext;
    this.authorizationRequest = authorizationRequest;
  }

  public void verify() {
    if (!authorizationRequest.isPkceRequest()) {
      return;
    }
    throwExceptionIfNotContainsCodeVerifier(tokenRequestContext);
    throwExceptionIfUnMatchCodeVerifier(tokenRequestContext, authorizationRequest);
  }

  void throwExceptionIfNotContainsCodeVerifier(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasCodeVerifier()) {
      throw new TokenBadRequestException(
          "authorization request has code_challenge, but token request does not contains code verifier");
    }
  }

  void throwExceptionIfUnMatchCodeVerifier(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    if (authorizationRequest.isPkceWithS256()) {
      CodeVerifier codeVerifier = tokenRequestContext.codeVerifier();
      CodeChallengeCalculator codeChallengeCalculator = new CodeChallengeCalculator(codeVerifier);
      CodeChallenge codeChallenge = codeChallengeCalculator.calculateWithS256();
      if (!codeChallenge.equals(authorizationRequest.codeChallenge())) {
        throw new TokenBadRequestException(
            "code_verifier of token request does not match code_challenge of authorization request");
      }
      return;
    }
    CodeChallengeCalculator codeChallengeCalculator =
        new CodeChallengeCalculator(tokenRequestContext.codeVerifier());
    CodeChallenge codeChallenge = codeChallengeCalculator.calculateWithPlain();
    if (!codeChallenge.equals(authorizationRequest.codeChallenge())) {
      throw new TokenBadRequestException(
          "code_verifier of token request does not match code_challenge of authorization request");
    }
  }
}
