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
package org.idp.server.core.extension.oid4vci.handler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.idp.server.core.extension.oid4vci.exception.CredentialRequestInvalidException;
import org.idp.server.core.extension.oid4vci.io.CredentialRequest;
import org.idp.server.core.extension.oid4vci.io.CredentialResponse;
import org.idp.server.core.extension.oid4vci.issuance.CredentialSubjectSource;
import org.idp.server.core.extension.oid4vci.issuance.SdJwtVcCredentialCreator;
import org.idp.server.core.extension.oid4vci.nonce.CredentialNonceRepository;
import org.idp.server.core.extension.oid4vci.request.CredentialRequestParameters;
import org.idp.server.core.extension.oid4vci.verifier.CredentialAccessTokenVerifier;
import org.idp.server.core.extension.oid4vci.verifier.CredentialRequestVerifier;
import org.idp.server.core.extension.oid4vci.verifier.JwtProofVerifier;
import org.idp.server.core.extension.oid4vci.verifier.VerifiedJwtProof;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.repository.UserQueryRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialConfiguration;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialIssuanceConfiguration;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialIssuanceDefinition;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialIssuerMetadataConfiguration;
import org.idp.server.core.openid.oauth.dpop.DPoPHeaderValidator;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.repository.OAuthTokenQueryRepository;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JwkParser;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * The Credential Endpoint (OpenID4VCI 1.0 Section 8): one credential per key proof, all of the
 * requested credential configuration and the token's End-User.
 *
 * <p>Order of checks: the access token (RFC 6750 errors), then what is asked for, then the proofs,
 * and the nonce last. A proof that is malformed is {@code invalid_proof} whatever its nonce; only a
 * well-formed proof with a stale or unknown nonce is {@code invalid_nonce}, which tells the Wallet
 * that a fresh nonce is all it needs.
 */
public class CredentialHandler {

  static final String JWT_PROOF_TYPE = "jwt";

  OAuthTokenQueryRepository oAuthTokenQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  CredentialNonceRepository credentialNonceRepository;
  UserQueryRepository userQueryRepository;
  SdJwtVcCredentialCreator sdJwtVcCredentialCreator = new SdJwtVcCredentialCreator();

  public CredentialHandler(
      OAuthTokenQueryRepository oAuthTokenQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      CredentialNonceRepository credentialNonceRepository,
      UserQueryRepository userQueryRepository) {
    this.oAuthTokenQueryRepository = oAuthTokenQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.credentialNonceRepository = credentialNonceRepository;
    this.userQueryRepository = userQueryRepository;
  }

  public CredentialResponse handle(CredentialRequest request) throws Exception {
    Tenant tenant = request.tenant();

    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
      return CredentialResponse.notFound();
    }
    CredentialIssuerMetadataConfiguration metadata =
        authorizationServerConfiguration.credentialIssuerMetadata();
    String credentialIssuer = metadata.credentialIssuer(authorizationServerConfiguration.issuer());

    if (!request.hasAccessToken()) {
      throw new TokenInvalidException("access token is required in the Authorization header");
    }
    new DPoPHeaderValidator(request.dpopProofHeaders()).validate();
    OAuthToken oAuthToken = oAuthTokenQueryRepository.find(tenant, request.toAccessToken());
    new CredentialAccessTokenVerifier(oAuthToken, request).verify();

    CredentialRequestParameters parameters = request.parameters();
    new CredentialRequestVerifier(parameters, metadata, oAuthToken).verify();

    String credentialConfigurationId = parameters.credentialConfigurationId();
    CredentialConfiguration configuration =
        metadata.credentialConfiguration(credentialConfigurationId);
    List<VerifiedJwtProof> proofs =
        verifyProofs(parameters, configuration, credentialIssuer, oAuthToken);
    spendNonces(tenant, proofs);

    User user = userQueryRepository.get(tenant, new UserIdentifier(oAuthToken.subject().value()));
    if (!user.exists() || !user.isActive()) {
      throw new TokenInvalidException("the End-User of the token is not active");
    }

    CredentialIssuanceConfiguration issuance =
        authorizationServerConfiguration.credentialIssuance();
    CredentialIssuanceDefinition definition = issuance.definition(credentialConfigurationId);
    JsonWebKey signingKey =
        JwkParser.parseKeys(authorizationServerConfiguration.jwks())
            .findBy(issuance.signingKeyId());
    CredentialSubjectSource subject = new CredentialSubjectSource(user);

    List<String> credentials = new ArrayList<>();
    for (VerifiedJwtProof proof : proofs) {
      credentials.add(
          sdJwtVcCredentialCreator.create(
              credentialIssuer, configuration, definition, subject, proof.holderKey(), signingKey));
    }
    return CredentialResponse.issued(credentials);
  }

  /**
   * Section 8.2: "The proofs parameter MUST be present if the proof_types_supported parameter is
   * present" for the configuration. One proof per request: batch issuance is not published.
   */
  List<VerifiedJwtProof> verifyProofs(
      CredentialRequestParameters parameters,
      CredentialConfiguration configuration,
      String credentialIssuer,
      OAuthToken oAuthToken) {

    if (!parameters.hasProofs()) {
      throw new CredentialRequestInvalidException("invalid_proof", "proofs is required");
    }
    List<String> proofTypes = parameters.proofTypes();
    if (proofTypes.size() != 1) {
      throw new CredentialRequestInvalidException(
          "invalid_proof", "proofs must contain exactly one proof type");
    }
    if (!proofTypes.get(0).equals(JWT_PROOF_TYPE)
        || !configuration.supportsProofType(JWT_PROOF_TYPE)) {
      throw new CredentialRequestInvalidException(
          "invalid_proof", "unsupported proof type: " + proofTypes.get(0));
    }
    List<String> jwtProofs = parameters.proofsOf(JWT_PROOF_TYPE);
    if (jwtProofs.isEmpty() || jwtProofs.size() != parameters.proofCountOf(JWT_PROOF_TYPE)) {
      throw new CredentialRequestInvalidException(
          "invalid_proof", "proofs.jwt must be a non-empty array of JWTs");
    }
    if (jwtProofs.size() > 1) {
      throw new CredentialRequestInvalidException(
          "invalid_credential_request", "batch issuance is not supported: send one proof");
    }

    String clientId = oAuthToken.requestedClientId().value();
    List<VerifiedJwtProof> verified = new ArrayList<>();
    for (String proof : jwtProofs) {
      verified.add(
          new JwtProofVerifier(
                  proof, configuration.proofType(JWT_PROOF_TYPE), credentialIssuer, clientId)
              .verify());
    }
    return verified;
  }

  /** Every nonce the proofs carry is spent once, so none of them can be replayed. */
  void spendNonces(Tenant tenant, List<VerifiedJwtProof> proofs) {
    Set<String> nonces = new LinkedHashSet<>();
    proofs.forEach(proof -> nonces.add(proof.nonce()));
    for (String nonce : nonces) {
      if (!credentialNonceRepository.consume(tenant, nonce)) {
        throw new CredentialRequestInvalidException(
            "invalid_nonce", "the c_nonce is unknown, expired or already used");
      }
    }
  }
}
