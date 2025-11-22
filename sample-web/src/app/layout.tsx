import { SessionProvider } from "next-auth/react";
import AuthHandler from "@/app/AuthHandler";
import { CssBaseline } from "@mui/material";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <SessionProvider>
          <CssBaseline />
          <AuthHandler>{children}</AuthHandler>
        </SessionProvider>
      </body>
    </html>
  );
}
