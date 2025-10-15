import { jest } from "@jest/globals";
import dotenv from "dotenv";
import path from "path";

// __dirname is available in CommonJS, but in ESM we need to construct it
// However, since this is being processed by Jest (CommonJS), we can use require
const __dirname = process.cwd();

// Load from parent directory .env file (shared with setup scripts)
const rootEnvPath = path.resolve(__dirname, '..', '.env');
dotenv.config({ path: rootEnvPath });

// Optional: Override with local .env.local if exists
dotenv.config({ path: ".env.local" });

console.log(`📋 Loaded environment from: ${rootEnvPath}`);

jest.setTimeout(30000);
