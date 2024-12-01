---
sidebar_position: 1
---

## specification

| Specification                                           | Description                                                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| **RFC6749 The OAuth 2.0 Authorization Framework**       | Defines a framework for delegated authorization, including multiple grant types for various use cases.    |
| **OpenID Connect Core 1.0**                             | Adds an identity layer to OAuth 2.0 for authentication.                                                   |
| **OpenID Connect Discovery 1.0**                        | Specifies a mechanism for dynamic client configuration using provider metadata.                           |
| **OpenID Connect Client-Initiated Backchannel Auth.**   | Defines backchannel authentication flows initiated by clients.                                            |
| **RFC7009 OAuth 2.0 Token Revocation**                  | Defines a mechanism to revoke access or refresh tokens.                                                   |
| **RFC7636 Proof Key for Code Exchange (PKCE)**          | Mitigates authorization code interception attacks for public clients.                                     |
| **RFC7662 OAuth 2.0 Token Introspection**               | Enables clients to query the status and metadata of an OAuth 2.0 token.                                   |
| **Financial-grade API Security Profile 1.0 - Baseline** | Defines security requirements for APIs handling sensitive financial data at a baseline level.             |
| **Financial-grade API Security Profile 1.0 - Advanced** | Advanced security requirements for highly sensitive financial APIs.                                       |


### RFC6749 The OAuth 2.0 Authorization Framework

| Sub-Specification                                       | Description                                                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Authorization Code Grant                                | A secure, server-to-server grant type requiring user interaction.                                         |
| Implicit Grant                                          | Simplified flow for public clients (e.g., JavaScript apps) with reduced security.                         |
| Resource Owner Password Credentials Grant               | Directly exchanges user credentials for tokens, suitable for trusted clients only.                        |
| Client Credentials Grant                                | Used for application-to-application authentication without user involvement.                              |

### OpenID Connect Core 1.0

| Sub-Specification                                       | Description                                                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Authorization Code Flow                                 | Secure flow using authorization codes and tokens, suitable for server-side applications.                  |
| Implicit Flow                                           | Optimized for clients like SPAs needing immediate access tokens.                                          |
| Hybrid Flow                                             | Combines Authorization Code and Implicit flows, providing more flexibility.                               |
| Request Object                                          | Enables secure, customizable authentication requests.                                                     |
| Signature                                               | Validates the authenticity of the request object.                                                         |
| Encryption                                              | Ensures confidentiality of request objects.                                                               |
| Signature None                                          | Permits unsigned request objects when applicable.                                                         |
| UserInfo                                                | Defines how to obtain additional user attributes from the UserInfo endpoint.                              |

### OpenID Connect Client-Initiated Backchannel Authentication Flow

| Sub-Specification                                       | Description                                                                                               |
|---------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Poll Mode                                               | Client repeatedly polls for authorization result.                                                         |
| Ping Mode                                               | Authorization server notifies the client of completion.                                                   |
| Push Mode                                               | Authorization server proactively pushes completion to the client.                                         |

