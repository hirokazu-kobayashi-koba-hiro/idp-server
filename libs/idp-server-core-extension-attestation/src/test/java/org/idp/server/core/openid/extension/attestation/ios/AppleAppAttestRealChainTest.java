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

package org.idp.server.core.openid.extension.attestation.ios;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;
import org.junit.jupiter.api.Test;

/**
 * The certificate chain of a real App Attest attestation, verified against the shipped Apple root.
 *
 * <p>{@link IosAppAttestFixture} builds its chains in the test, so it cannot show that what Apple
 * actually issues passes the path validation: issuer and subject names that chain, no critical
 * extension the validator does not understand (Apple's own extensions, {@code
 * 1.2.840.113635.100.8.2} among them, are non-critical), and signature algorithms the JDK has not
 * disabled. This chain is Apple's, so those properties are Apple's too.
 *
 * <p>The chain is the {@code x5c} of {@code test/fixtures/attestation-production.json} in <a
 * href="https://github.com/uebelack/node-app-attest">node-app-attest</a> (MIT License, Copyright
 * (c) 2024 David Übelacker): the credCert and Apple App Attestation CA 1. The credCert was valid
 * from 2024-02-06 to 2024-12-21, so the chain is verified at a moment inside that window.
 */
class AppleAppAttestRealChainTest {

  /** CN=482f3a2d…, OU=AAA Certification, O=Apple Inc. Issued by Apple App Attestation CA 1. */
  static final String CRED_CERT =
      "MIIDNDCCArqgAwIBAgIGAY2FZv9OMAoGCCqGSM49BAMCME8xIzAhBgNVBAMMGkFwcGxlIEFwcCBBdHRl"
          + "c3RhdGlvbiBDQSAxMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMB4XDTI0"
          + "MDIwNjIxMDg1NloXDTI0MTIyMTEyNDI1NlowgZExSTBHBgNVBAMMQDQ4MmYzYTJkOTlhODE1YjJmZjJi"
          + "MTU5ZjdiM2FmYjhhMTgwNDc0YjFjYWYxOWFjMzZkM2MwY2I0MDkwMTA5YjMxGjAYBgNVBAsMEUFBQSBD"
          + "ZXJ0aWZpY2F0aW9uMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlhMFkwEwYH"
          + "KoZIzj0CAQYIKoZIzj0DAQcDQgAE2YKewJpfK9DiLX3l3mLvvKiCiTxVDJqFmLu7THesPxlhY6sjWPjK"
          + "dRRopGtkXUMABTH8lHYATXlb/YMd5VYqhqOCAT0wggE5MAwGA1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQD"
          + "AgTwMIGKBgkqhkiG92NkCAUEfTB7pAMCAQq/iTADAgEBv4kxAwIBAL+JMgMCAQG/iTMDAgEBv4k0KwQp"
          + "VjhINkxROTQ0OC5pby51ZWJlbGFja2VyLkFwcEF0dGVzdEV4YW1wbGWlBgQEc2tzIL+JNgMCAQW/iTcD"
          + "AgEAv4k5AwIBAL+JOgMCAQC/iTsDAgEAMFcGCSqGSIb3Y2QIBwRKMEi/ingIBAYxNy4yLjG/iFAHAgUA"
          + "/////7+KewcEBTIxQzY2v4p9CAQGMTcuMi4xv4p+AwIBAL+LDA8EDTIxLjMuNjYuMC4wLDAwMwYJKoZI"
          + "hvdjZAgCBCYwJKEiBCAcCMADdh/I+YF+luHIBOxxqBxrq6wL7dEutq6MmJD3JTAKBggqhkjOPQQDAgNo"
          + "ADBlAjEA3jTRLIe/NhXI5A9W4on8tXch9c3iWFzc/O15kHkbDdlXNnvI4n8HKaeUgbQjMl3aAjBLZMuJ"
          + "j3LMIc7MunoEICYym5NancnsA/rAPLzTSm/giQacXQdZTAu9BNgPAujBZj8=";

  /** CN=Apple App Attestation CA 1, O=Apple Inc. Issued by Apple App Attestation Root CA. */
  static final String INTERMEDIATE =
      "MIICQzCCAcigAwIBAgIQCbrF4bxAGtnUU5W8OBoIVDAKBggqhkjOPQQDAzBSMSYwJAYDVQQDDB1BcHBs"
          + "ZSBBcHAgQXR0ZXN0YXRpb24gUm9vdCBDQTETMBEGA1UECgwKQXBwbGUgSW5jLjETMBEGA1UECAwKQ2Fs"
          + "aWZvcm5pYTAeFw0yMDAzMTgxODM5NTVaFw0zMDAzMTMwMDAwMDBaME8xIzAhBgNVBAMMGkFwcGxlIEFw"
          + "cCBBdHRlc3RhdGlvbiBDQSAxMRMwEQYDVQQKDApBcHBsZSBJbmMuMRMwEQYDVQQIDApDYWxpZm9ybmlh"
          + "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAErls3oHdNebI1j0Dn0fImJvHCX+8XgC3qs4JqWYdP+NKtFSV4"
          + "mqJmBBkSSLY8uWcGnpjTY71eNw+/oI4ynoBzqYXndG6jWaL2bynbMq9FXiEWWNVnr54mfrJhTcIaZs6Z"
          + "o2YwZDASBgNVHRMBAf8ECDAGAQH/AgEAMB8GA1UdIwQYMBaAFKyREFMzvb5oQf+nDKnl+url5YqhMB0G"
          + "A1UdDgQWBBQ+410cBBmpybQx+IR01uHhV3LjmzAOBgNVHQ8BAf8EBAMCAQYwCgYIKoZIzj0EAwMDaQAw"
          + "ZgIxALu+iI1zjQUCz7z9Zm0JV1A1vNaHLD+EMEkmKe3R+RToeZkcmui1rvjTqFQz97YNBgIxAKs47dDM"
          + "ge0ApFLDukT5k2NlU/7MKX8utN+fXr5aSsq2mVxLgg35BDhveAe7WJQ5tw==";

  static final Date WHILE_VALID = Date.from(Instant.parse("2024-06-01T00:00:00Z"));

  @Test
  void verifiesToTheShippedAppleRoot() throws Exception {
    X509CertificateChain chain = X509CertificateChain.parse(List.of(CRED_CERT, INTERMEDIATE));

    assertDoesNotThrow(
        () -> chain.verifyToTrustAnchor(AppleAttestationRoots.certificates(), WHILE_VALID));
  }

  @Test
  void isRefusedOnceTheCredCertHasExpired() throws Exception {
    // The validation time is what the check runs at: the same chain fails today.
    X509CertificateChain chain = X509CertificateChain.parse(List.of(CRED_CERT, INTERMEDIATE));

    assertThrows(
        X509CertInvalidException.class,
        () -> chain.verifyToTrustAnchor(AppleAttestationRoots.certificates()));
  }
}
