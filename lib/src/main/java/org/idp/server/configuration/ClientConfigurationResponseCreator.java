package org.idp.server.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientConfigurationResponseCreator {

    public static Map<String, Object> create(List<ClientConfiguration> clientConfigurations) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> list = clientConfigurations.stream().map((ClientConfigurationResponseCreator::create)).toList();
        response.put("list", list);
        return response;
    }

    public static Map<String, Object> create(ClientConfiguration config) {
        Map<String, Object> responseMap = new HashMap<>();

        if (config.clientId != null) responseMap.put("client_id", config.clientId);
        if (config.clientSecret != null) responseMap.put("client_secret", config.clientSecret);
        if (config.redirectUris != null && !config.redirectUris.isEmpty()) responseMap.put("redirect_uris", config.redirectUris);
        if (config.tokenEndpointAuthMethod != null) responseMap.put("token_endpoint_auth_method", config.tokenEndpointAuthMethod);
        if (config.grantTypes != null && !config.grantTypes.isEmpty()) responseMap.put("grant_types", config.grantTypes);
        if (config.responseTypes != null && !config.responseTypes.isEmpty()) responseMap.put("response_types", config.responseTypes);
        if (config.clientName != null) responseMap.put("client_name", config.clientName);
        if (config.clientUri != null) responseMap.put("client_uri", config.clientUri);
        if (config.logoUri != null) responseMap.put("logo_uri", config.logoUri);
        if (config.scope != null) responseMap.put("scope", config.scope);
        if (config.contacts != null) responseMap.put("contacts", config.contacts);
        if (config.tosUri != null) responseMap.put("tos_uri", config.tosUri);
        if (config.policyUri != null) responseMap.put("policy_uri", config.policyUri);
        if (config.jwksUri != null) responseMap.put("jwks_uri", config.jwksUri);
        if (config.jwks != null) responseMap.put("jwks", config.jwks);
        if (config.softwareId != null) responseMap.put("software_id", config.softwareId);
        if (config.softwareVersion != null) responseMap.put("software_version", config.softwareVersion);
        if (config.requestUris != null && !config.requestUris.isEmpty()) responseMap.put("request_uris", config.requestUris);
        if (config.backchannelTokenDeliveryMode != null) responseMap.put("backchannel_token_delivery_mode", config.backchannelTokenDeliveryMode);
        if (config.backchannelClientNotificationEndpoint != null) responseMap.put("backchannel_client_notification_endpoint", config.backchannelClientNotificationEndpoint);
        if (config.backchannelAuthenticationRequestSigningAlg != null) responseMap.put("backchannel_authentication_request_signing_alg", config.backchannelAuthenticationRequestSigningAlg);
        if (config.backchannelUserCodeParameter != null) responseMap.put("backchannel_user_code_parameter", config.backchannelUserCodeParameter);
        if (config.applicationType != null) responseMap.put("application_type", config.applicationType);
        if (config.idTokenEncryptedResponseAlg != null) responseMap.put("id_token_encrypted_response_alg", config.idTokenEncryptedResponseAlg);
        if (config.idTokenEncryptedResponseEnc != null) responseMap.put("id_token_encrypted_response_enc", config.idTokenEncryptedResponseEnc);
        if (config.authorizationDetailsTypes != null && !config.authorizationDetailsTypes.isEmpty()) responseMap.put("authorization_details_types", config.authorizationDetailsTypes);
        if (config.tlsClientAuthSubjectDn != null) responseMap.put("tls_client_auth_subject_dn", config.tlsClientAuthSubjectDn);
        if (config.tlsClientAuthSanDns != null) responseMap.put("tls_client_auth_san_dns", config.tlsClientAuthSanDns);
        if (config.tlsClientAuthSanUri != null) responseMap.put("tls_client_auth_san_uri", config.tlsClientAuthSanUri);
        if (config.tlsClientAuthSanIp != null) responseMap.put("tls_client_auth_san_ip", config.tlsClientAuthSanIp);
        if (config.tlsClientAuthSanEmail != null) responseMap.put("tls_client_auth_san_email", config.tlsClientAuthSanEmail);
        responseMap.put("tls_client_certificate_bound_access_tokens", config.tlsClientCertificateBoundAccessTokens);
        if (config.authorizationSignedResponseAlg != null) responseMap.put("authorization_signed_response_alg", config.authorizationSignedResponseAlg);
        if (config.authorizationEncryptedResponseAlg != null) responseMap.put("authorization_encrypted_response_alg", config.authorizationEncryptedResponseAlg);
        if (config.authorizationEncryptedResponseEnc != null) responseMap.put("authorization_encrypted_response_enc", config.authorizationEncryptedResponseEnc);
        responseMap.put("supported_jar", config.supportedJar);
        if (config.issuer != null) responseMap.put("issuer", config.issuer);

        return responseMap;
    }
}
