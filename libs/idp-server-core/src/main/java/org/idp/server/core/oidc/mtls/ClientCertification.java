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

package org.idp.server.core.oidc.mtls;

import java.util.Objects;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509Certification;

public class ClientCertification {
  X509Certification x509Certification;

  public ClientCertification() {}

  public ClientCertification(X509Certification x509Certification) {
    this.x509Certification = x509Certification;
  }

  public static ClientCertification parse(String value) throws X509CertInvalidException {
    X509Certification x509Certification = X509Certification.parse(value);
    return new ClientCertification(x509Certification);
  }

  public boolean exists() {
    return Objects.nonNull(x509Certification);
  }

  public String subject() {
    return x509Certification.subject();
  }

  public byte[] der() {
    return x509Certification.der();
  }
}
