import { jest } from "@jest/globals";
import dotenv from "dotenv";

dotenv.config({ path: ".env.local" });

jest.setTimeout(30000);
