import { AppProps } from "next/app";
import { CssBaseline } from "@mui/material";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createContext, useContext, useState } from "react";

const queryClient = new QueryClient();

export const backendUrl =
  process.env.NEXT_PUBLIC_BACKEND_URL ||
  "https://idp-server-0d10773f8944.herokuapp.com";

interface AppContextType {
  userId: string | null;
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
  const [userId, setUserId] = useState<string>("");

  return (
    <>
      <AppContext.Provider value={{ userId, setUserId }}>
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <Component {...pageProps} />
        </QueryClientProvider>
      </AppContext.Provider>
    </>
  );
}
