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

package org.idp.server.core.openid.clientinstance.registration;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.clientinstance.registration.handler.ClientInstanceRegistrationErrorHandler;
import org.idp.server.platform.datasource.SqlDuplicateKeyException;
import org.junit.jupiter.api.Test;

/**
 * A rejection is answered and the transaction commits, keeping the challenge consumed; a database
 * failure is rethrown so that the transaction rolls back rather than committing half of it.
 */
class ClientInstanceRegistrationErrorHandlerTest {

  ClientInstanceRegistrationErrorHandler handler = new ClientInstanceRegistrationErrorHandler();

  @Test
  void answersARejectedRegistration() {
    assertEquals(
        400,
        handler
            .handle("registration", new ClientInstanceRegistrationException("nonce mismatch"))
            .statusCode());
  }

  @Test
  void rethrowsADuplicateKeySoThatTheTransactionRollsBack() {
    SqlDuplicateKeyException duplicate =
        new SqlDuplicateKeyException("uq_client_instance_active_user");

    assertSame(
        duplicate,
        assertThrows(
            SqlDuplicateKeyException.class, () -> handler.handle("registration", duplicate)));
  }
}
