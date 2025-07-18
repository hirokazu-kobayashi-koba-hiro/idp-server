import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  basePath: "/auth-views",
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  output: "export",
};

export default nextConfig;
