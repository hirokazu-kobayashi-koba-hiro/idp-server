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

package org.idp.server.core.adapters.datasource.session.audit;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.session.logout.LogoutNotification;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class LogoutNotificationMysqlExecutor implements LogoutNotificationSqlExecutor {

  @Override
  public void insert(Tenant tenant, LogoutNotification notification) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            INSERT INTO logout_notifications (
            op_session_id,
            client_id,
            sid,
            notification_type,
            logout_token_jti,
            status,
            http_status_code,
            error_message,
            attempted_at,
            completed_at
            )
            VALUES (
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
            );
            """;

    List<Object> params = new ArrayList<>();
    params.add(notification.opSessionId() != null ? notification.opSessionId().value() : null);
    params.add(notification.clientId());
    params.add(notification.sid() != null ? notification.sid().value() : null);
    params.add(notification.notificationType().value());
    params.add(notification.logoutTokenJti());
    params.add(notification.status().value());
    params.add(notification.httpStatusCode());
    params.add(notification.errorMessage());
    params.add(Timestamp.from(notification.attemptedAt()));
    params.add(
        notification.completedAt() != null ? Timestamp.from(notification.completedAt()) : null);

    sqlExecutor.execute(sqlTemplate, params);
  }

  @Override
  public void update(Tenant tenant, LogoutNotification notification) {
    SqlExecutor sqlExecutor = new SqlExecutor();

    String sqlTemplate =
        """
            UPDATE logout_notifications
            SET status = ?,
                http_status_code = ?,
                error_message = ?,
                completed_at = ?
            WHERE id = ?;
            """;

    List<Object> params = new ArrayList<>();
    params.add(notification.status().value());
    params.add(notification.httpStatusCode());
    params.add(notification.errorMessage());
    params.add(
        notification.completedAt() != null ? Timestamp.from(notification.completedAt()) : null);
    params.add(notification.id());

    sqlExecutor.execute(sqlTemplate, params);
  }
}
