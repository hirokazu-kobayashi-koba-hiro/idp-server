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

import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

import java.util.List;

public class AuditLogWriters {

    List<AuditLogWriter> writers;
    LoggerWrapper log = LoggerWrapper.getLogger(AuditLogWriters.class);

    public AuditLogWriters(List<AuditLogWriter> writers) {
        this.writers = writers;
    }

    public void write(Tenant tenant, AuditLog auditLog) {
        for (AuditLogWriter writer : writers) {
            if (writer.shouldExecute(tenant, auditLog)) {
                log.info("AuditLogWriter execute: " + writer.getClass().getSimpleName());
                writer.write(tenant, auditLog);
            }
        }
    }
}
