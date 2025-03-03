package org.idp.server.core.configuration;

import java.util.List;
import org.idp.server.core.basic.json.JsonReadable;

public class VerifiableCredentialDefinitionConfiguration implements JsonReadable {
  List<String> type;
}
