import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // basePath only needed for Spring Boot static resources (not Docker)
  basePath: process.env.BASE_PATH || "",
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  output: "export",
};

export default nextConfig;
