package org.idp.server.core.identity.trustframework;

import java.util.UUID;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationDetails;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationRequest;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationStatus;
import org.idp.server.core.identity.trustframework.delegation.WorkflowApplyingResult;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.RequestedClientId;

public class IdentityVerificationApplication {

  IdentityVerificationApplicationIdentifier identifier;
  IdentityVerificationType identityVerificationType;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  IdentityVerificationApplicationDetails applicationDetails;
  String sub;
  String externalApplicationId;

  TrustFramework trustFramework;
  TrustFrameworkDetails trustFrameworkDetails;
  IdentityVerificationApplicationStatus status;
  String comment;

  public IdentityVerificationApplication() {}

  private IdentityVerificationApplication(
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType verificationType,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      IdentityVerificationApplicationDetails details,
      String sub,
      String externalApplicationId,
      IdentityVerificationApplicationStatus status) {
    this.identifier = identifier;
    this.identityVerificationType = verificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.applicationDetails = details;
    this.sub = sub;
    this.externalApplicationId = externalApplicationId;
    this.status = status;
  }

  public IdentityVerificationApplication(
      IdentityVerificationApplicationIdentifier identifier,
      IdentityVerificationType identityVerificationType,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      IdentityVerificationApplicationDetails applicationDetails,
      String sub,
      String externalApplicationId,
      TrustFramework trustFramework,
      TrustFrameworkDetails trustFrameworkDetails,
      String comment) {
    this.identifier = identifier;
    this.identityVerificationType = identityVerificationType;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.applicationDetails = applicationDetails;
    this.sub = sub;
    this.externalApplicationId = externalApplicationId;
    this.trustFramework = trustFramework;
    this.trustFrameworkDetails = trustFrameworkDetails;
    this.comment = comment;
  }

  public static IdentityVerificationApplication create(
      Tenant tenant,
      RequestedClientId requestedClientId,
      User user,
      IdentityVerificationType verificationType,
      IdentityVerificationApplicationRequest request,
      WorkflowApplyingResult applyingResult) {

    IdentityVerificationApplicationIdentifier identifier =
        new IdentityVerificationApplicationIdentifier(UUID.randomUUID().toString());

    TenantIdentifier tenantIdentifier = tenant.identifier();
    String sub = user.sub();

    IdentityVerificationApplicationDetails details =
        new IdentityVerificationApplicationDetails(JsonNodeWrapper.from(request.toMap()));

    String externalApplicationId = applyingResult.extractValueFromBody("application_id");

    return new IdentityVerificationApplication(
        identifier,
        verificationType,
        tenantIdentifier,
        requestedClientId,
        details,
        sub,
        externalApplicationId,
        IdentityVerificationApplicationStatus.REQUESTED);
  }

  public IdentityVerificationApplicationIdentifier identifier() {
    return identifier;
  }

  public IdentityVerificationType identityVerificationType() {
    return identityVerificationType;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public IdentityVerificationApplicationDetails applicationDetails() {
    return applicationDetails;
  }

  public String sub() {
    return sub;
  }

  public String externalApplicationId() {
    return externalApplicationId;
  }

  public TrustFramework trustFramework() {
    return trustFramework;
  }

  public TrustFrameworkDetails trustFrameworkDetails() {
    return trustFrameworkDetails;
  }

  public String comment() {
    return comment;
  }

  public IdentityVerificationApplicationStatus status() {
    return status;
  }
}
