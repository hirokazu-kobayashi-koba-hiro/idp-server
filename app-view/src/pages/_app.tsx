import { AppProps } from "next/app";
import { CssBaseline } from "@mui/material";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Inter } from "next/font/google";
import { createContext, useContext, useState } from "react";
import { createAppTheme } from "@/theme/theme";

const queryClient = new QueryClient();

const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  display: "swap",
});
const theme = createAppTheme(inter.style.fontFamily);

// Use empty string for same-origin deployment (via nginx proxy at /auth-views/)
// This ensures AUTH_SESSION cookies are sent with SameSite=Lax
export const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";

interface AppContextType {
  id: string | null;
  setId: (id: string) => void;
  tenantId: string | null;
  setTenantId: (id: string) => void;
  userId: string | null;
  email: string | null;
  setEmail: (email: string) => void;
  setUserId: (id: string) => void;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error("undefined app context");
  }
  return context;
};

export default function App({ Component, pageProps }: AppProps) {
  const [id, setId] = useState<string>("");
  const [tenantId, setTenantId] = useState<string>("");
  const [userId, setUserId] = useState<string>("");
  const [email, setEmail] = useState<string>("");

  return (
    <AppContext.Provider
      value={{ id, setId, tenantId, setTenantId, userId, setUserId, email, setEmail }}
    >
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <main className={inter.className}>
            <Component {...pageProps} />
          </main>
        </QueryClientProvider>
      </ThemeProvider>
    </AppContext.Provider>
  );
}
