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
