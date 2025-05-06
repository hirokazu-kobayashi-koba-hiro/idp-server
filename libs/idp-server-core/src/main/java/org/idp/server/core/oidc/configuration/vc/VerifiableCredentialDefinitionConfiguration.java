package org.idp.server.core.oidc.configuration.vc;

import java.util.List;
import org.idp.server.basic.json.JsonReadable;

public class VerifiableCredentialDefinitionConfiguration implements JsonReadable {
  List<String> type;
}
