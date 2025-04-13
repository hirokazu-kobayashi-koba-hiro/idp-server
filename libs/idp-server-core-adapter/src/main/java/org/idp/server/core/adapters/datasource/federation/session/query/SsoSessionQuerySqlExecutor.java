package org.idp.server.core.adapters.datasource.federation.session.query;

import java.util.Map;
import org.idp.server.core.federation.SsoSessionIdentifier;

public interface SsoSessionQuerySqlExecutor {

  Map<String, String> selectOne(SsoSessionIdentifier ssoSessionIdentifier);
}
