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

package org.idp.server.platform.audit;

import org.idp.server.platform.dependency.ApplicationComponentContainer;

public class AuditLogDataBaseWriterProvider implements AuditLogWriterProvider {

  @Override
  public AuditLogWriter provide(ApplicationComponentContainer container) {

    AuditLogCommandRepository auditLogCommandRepository =
        container.resolve(AuditLogCommandRepository.class);
    return new AuditLogDataBaseWriter(auditLogCommandRepository);
  }
}
