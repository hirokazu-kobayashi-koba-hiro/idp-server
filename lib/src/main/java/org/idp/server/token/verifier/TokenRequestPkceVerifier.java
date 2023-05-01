package org.idp.server.token.verifier;

import org.idp.server.oauth.pkce.CodeChallengeCalculator;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.pkce.CodeChallenge;
import org.idp.server.type.pkce.CodeVerifier;

public class TokenRequestPkceVerifier {

  CodeChallengeCalculator codeChallengeCalculator;

  public TokenRequestPkceVerifier() {
    this.codeChallengeCalculator = new CodeChallengeCalculator();
  }

  public void verify(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    if (!authorizationRequest.isPkceRequest()) {
      return;
    }
    throwIfNotContainsCodeVerifier(tokenRequestContext);
    throwIfUnMatchCodeVerifier(tokenRequestContext, authorizationRequest);
  }

  void throwIfNotContainsCodeVerifier(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasCodeVerifier()) {
      throw new TokenBadRequestException(
          "authorization request has code_challenge, but token request does not contains code verifier");
    }
  }

  void throwIfUnMatchCodeVerifier(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    if (authorizationRequest.isPkceWithS256()) {
      CodeVerifier codeVerifier = tokenRequestContext.codeVerifier();
      CodeChallenge codeChallenge = codeChallengeCalculator.calculateWithS256(codeVerifier);
      if (!codeChallenge.equals(authorizationRequest.codeChallenge())) {
        throw new TokenBadRequestException(
            "code_verifier of token request does not match code_challenge of authorization request");
      }
      return;
    }
    CodeChallenge codeChallenge =
        codeChallengeCalculator.calculateWithPlain(tokenRequestContext.codeVerifier());
    if (!codeChallenge.equals(authorizationRequest.codeChallenge())) {
      throw new TokenBadRequestException(
          "code_verifier of token request does not match code_challenge of authorization request");
    }
  }
}
