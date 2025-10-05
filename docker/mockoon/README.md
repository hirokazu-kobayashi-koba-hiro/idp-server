# Mockoon Configuration

This directory contains Mockoon configuration for simulating external services during development and testing.

## Files

- `config.json` - Mockoon API mock configuration

## Usage

Mockoon is automatically started via `docker-compose.yaml`:

```yaml
mockoon:
  image: mockoon/cli:latest
  volumes:
    - ./docker/mockoon/config.json:/data/config.json
  command: ["-d", "/data/config.json", "-p", "4000"]
  ports:
    - "4000:4000"
```

## External Services Mocked

- eKYC (electronic Know Your Customer) verification services
- SMS/Email notification services
- External authentication providers
- Identity verification endpoints

## Endpoint

Mock server runs on `http://localhost:4000`
