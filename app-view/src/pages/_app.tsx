import { AppProps } from 'next/app';
import { CssBaseline } from '@mui/material';
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const queryClient = new QueryClient();

export const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL || ""

export default function App({ Component, pageProps }: AppProps) {
  return (
    <>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <Component {...pageProps} />
      </QueryClientProvider>
    </>
  );
}