# E2E Tests for idp-server

This directory contains end-to-end tests for the idp-server project.

## Test Categories

The E2E tests are organized into three main categories:

- **📘 spec/** - Specification compliance tests (RFC and standard protocol verification)
- **📕 scenario/** - Real-world integration scenario tests  
- **🐒 monkey/** - Chaos testing and edge case validation

## Quick Start

### Prerequisites

- Node.js 18+
- idp-server running locally or accessible via URL
- Test tenant(s) configured in your idp-server instance

### Setup

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Configure test environment:**
   
   Copy the example environment file and customize it:
   ```bash
   cp .env.example .env.local
   ```
   
   Edit `.env.local` with your tenant IDs and configuration:
   ```bash
   # Example configuration
   IDP_SERVER_URL=http://localhost:8080
   IDP_SERVER_TENANT_ID=your-tenant-id-here
   CIBA_USER_EMAIL=test@example.com
   ```

3. **Run tests:**
   ```bash
   # Run all tests
   npm test
   
   # Run specific test category
   npm test spec/
   npm test scenario/
   npm test monkey/
   
   # Run with coverage
   npm run test-coverage
   ```

## Configuration

### Environment Variables

The tests support dynamic configuration through environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `IDP_SERVER_URL` | Backend server URL | `http://localhost:8080` |
| `IDP_SERVER_TENANT_ID` | Main tenant ID for tests | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `IDP_SERVER_FEDERATION_TENANT_ID` | Federation tenant ID | `1e68932e-ed4a-43e7-b412-460665e42df3` |
| `IDP_SERVER_UNSUPPORTED_TENANT_ID` | Unsupported features tenant ID | `94d8598e-f238-4150-85c2-c4accf515784` |
| `CIBA_USER_SUB` | Test user subject identifier | `3ec055a8-8000-44a2-8677-e70ebff414e2` |
| `CIBA_USER_EMAIL` | Test user email address | `ito.ichiro@gmail.com` |
| `CIBA_USER_DEVICE_ID` | Test user device ID | `7736a252-60b4-45f5-b817-65ea9a540860` |
| `CIBA_USERNAME` | Test username | `ito.ichiro` |
| `CIBA_PASSWORD` | Test password | `successUserCode001` |

### Dynamic Configuration

You can run tests against different tenants without modifying code:

```bash
# Using .env.local file (recommended for local development)
npm test

# Test against a specific tenant using environment variables
IDP_SERVER_TENANT_ID=my-test-tenant npm test

# Test against staging environment
IDP_SERVER_URL=https://staging.example.com \
IDP_SERVER_TENANT_ID=staging-tenant \
npm test

# CI environment example
IDP_SERVER_URL=https://ci-idp.example.com \
IDP_SERVER_TENANT_ID=ci-tenant-123 \
CIBA_USER_EMAIL=ci-test@example.com \
npm test

# Using custom .env file
cp .env.example .env.staging
# Edit .env.staging with your staging configuration
# The tests will automatically load .env.local if it exists
```

## Test Structure

```
src/
├── api/           # API client helpers
├── lib/           # Utility libraries (JWT, OAuth, HTTP, etc.)
├── oauth/         # OAuth/OIDC flow helpers
├── tests/
│   ├── spec/      # RFC compliance tests
│   ├── scenario/  # Integration scenario tests
│   └── monkey/    # Chaos/edge case tests
└── user/          # User management helpers
```

## Development

### Creating Custom Test Configurations

You can create custom configurations for specific test scenarios:

```javascript
import { createServerConfig } from './testConfig.js';

// Create configuration for a specific tenant
const myTenantConfig = createServerConfig('my-custom-tenant-id');

// Create configuration with custom base URL
const stagingConfig = createServerConfig('staging-tenant', 'https://staging.example.com');
```

### Adding New Tests

1. Choose the appropriate category (spec/, scenario/, or monkey/)
2. Follow existing patterns for imports and test structure
3. Use the shared configuration from `testConfig.js`
4. Add appropriate error handling and cleanup

## Tips & Certificates

### SSL Certificates

For testing with self-signed certificates, use OpenSSL (not LibreSSL):

```shell
CN=test.example.com
date=$(date +%Y%m%d%H%M%S)
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 > ${date}_private_key.pem
openssl pkey -pubout -in ${date}_private_key.pem > ${date}_public_key.pem
openssl req -x509 -key ${date}_private_key.pem -subj /CN=${CN} -days 1000 > ${date}_certificate.pem
```

## Troubleshooting

### Common Issues

1. **Tenant not found errors:**
   - Verify your `IDP_SERVER_TENANT_ID` is correct
   - Ensure the tenant exists in your idp-server instance

2. **User not found errors:**
   - Check that the CIBA user configuration matches your tenant's users
   - Verify `CIBA_USER_EMAIL` and `CIBA_USER_SUB` are correct

3. **Connection errors:**
   - Ensure `IDP_SERVER_URL` is accessible
   - Check that idp-server is running and responding

### Debug Mode

Enable debug logging by setting environment variables:

```bash
DEBUG=* npm test  # Enable all debug logs
NODE_ENV=test npm test  # Enable test-specific logging
```

## Contributing

When adding new tests or modifying existing ones:

1. Ensure tests work with dynamic tenant configuration
2. Use the shared configuration from `testConfig.js`
3. Add appropriate documentation for new environment variables
4. Test with different tenant IDs to ensure portability