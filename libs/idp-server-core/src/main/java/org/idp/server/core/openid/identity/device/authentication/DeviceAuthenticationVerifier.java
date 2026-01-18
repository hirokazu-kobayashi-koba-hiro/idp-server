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

package org.idp.server.core.openid.identity.device.authentication;

import java.util.Optional;
import org.idp.server.core.openid.identity.device.AuthenticationDeviceIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.JwtBearerCredentialData;
import org.idp.server.core.openid.identity.device.credential.repository.DeviceCredentialQueryRepository;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JwtCredential;
import org.idp.server.platform.jose.JwtSignatureVerifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.policy.DeviceAuthenticationType;

/**
 * DeviceAuthenticationVerifier verifies device authentication using JWT assertions.
 *
 * <p>Supports both symmetric (device_secret_jwt) and asymmetric (private_key_jwt) authentication.
 *
 * <p>This class provides both high-level API (throwing UnauthorizedException) and low-level API
 * (throwing JoseInvalidException) for different use cases:
 *
 * <ul>
 *   <li>High-level: {@link #verify} - for API endpoint authentication
 *   <li>Low-level: {@link #verifySignature} - for JWT Bearer Grant and other token operations
 * </ul>
 */
public class DeviceAuthenticationVerifier {

  private final DeviceCredentialQueryRepository deviceCredentialQueryRepository;

  public DeviceAuthenticationVerifier(
      DeviceCredentialQueryRepository deviceCredentialQueryRepository) {
    this.deviceCredentialQueryRepository = deviceCredentialQueryRepository;
  }

  /**
   * Verifies device authentication using JWT assertion (high-level API).
   *
   * @param tenant the tenant
   * @param deviceIdentifier the device identifier
   * @param assertion the JWT assertion
   * @param authenticationType the authentication type
   * @throws UnauthorizedException if verification fails
   */
  public void verify(
      Tenant tenant,
      AuthenticationDeviceIdentifier deviceIdentifier,
      String assertion,
      DeviceAuthenticationType authenticationType) {

    try {
      JsonWebSignature jws = JsonWebSignature.parse(assertion);
      verifySignature(tenant, deviceIdentifier, jws);
    } catch (JoseInvalidException e) {
      throw new UnauthorizedException("Invalid JWT assertion: " + e.getMessage());
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnauthorizedException("Device authentication failed: " + e.getMessage());
    }
  }

  /**
   * Verifies device JWT signature (low-level API).
   *
   * <p>This method finds the device credential by device ID and algorithm, then verifies the
   * signature. Use this for JWT Bearer Grant and other token operations where you need to control
   * exception handling.
   *
   * @param tenant the tenant
   * @param deviceIdentifier the device identifier
   * @param jws the parsed JSON Web Signature
   * @throws JoseInvalidException if signature verification fails or credential not found
   */
  public void verifySignature(
      Tenant tenant, AuthenticationDeviceIdentifier deviceIdentifier, JsonWebSignature jws)
      throws JoseInvalidException {

    String algorithm = jws.algorithm();

    Optional<DeviceCredential> credentialOpt =
        deviceCredentialQueryRepository.findActiveByDeviceIdAndAlgorithm(
            tenant, deviceIdentifier, algorithm);

    if (credentialOpt.isEmpty()) {
      throw new JoseInvalidException(
          String.format(
              "No active credential found for device '%s' with algorithm '%s'",
              deviceIdentifier.value(), algorithm));
    }

    DeviceCredential credential = credentialOpt.get();
    verifyWithCredential(jws, credential);
  }

  /**
   * Verifies JWT signature using the provided credential.
   *
   * @param jws the JSON Web Signature
   * @param credential the device credential
   * @throws JoseInvalidException if verification fails
   */
  public void verifyWithCredential(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {

    if (credential.isSymmetric()) {
      verifySymmetric(jws, credential);
    } else if (credential.isAsymmetric()) {
      verifyAsymmetric(jws, credential);
    } else {
      throw new JoseInvalidException("Unsupported credential type: " + credential.type());
    }
  }

  private void verifySymmetric(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {
    JwtBearerCredentialData data = credential.jwtBearerData();
    if (!data.hasSecretValue()) {
      throw new JoseInvalidException(
          "Device credential does not have a secret value for symmetric signing");
    }

    JwtCredential jwtCredential = JwtCredential.symmetric(data.secretValue());
    JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
    signatureVerifier.verify(jws, jwtCredential);
  }

  private void verifyAsymmetric(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {
    JwtBearerCredentialData data = credential.jwtBearerData();
    if (!data.hasJwks()) {
      throw new JoseInvalidException("Device credential does not have JWKS for asymmetric signing");
    }

    JwtCredential jwtCredential = JwtCredential.asymmetric(data.jwks());
    JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
    signatureVerifier.verify(jws, jwtCredential);
  }
}
