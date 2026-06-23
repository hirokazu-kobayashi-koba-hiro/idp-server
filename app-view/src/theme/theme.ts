import { createTheme, Theme } from "@mui/material/styles";

/**
 * App-wide MUI theme — a light, spacious, trust-oriented look.
 *
 * Centralizes palette / typography / shape / component defaults so screens stay consistent and
 * don't re-implement styling per page. Pass the active font family (e.g. from next/font).
 */
export const createAppTheme = (fontFamily: string): Theme =>
  createTheme({
    palette: {
      mode: "light",
      primary: { main: "#635bff", dark: "#4b45c6", light: "#8a84ff", contrastText: "#ffffff" },
      background: { default: "#f6f9fc", paper: "#ffffff" },
      text: { primary: "#1a1f36", secondary: "#697386" },
      divider: "#e3e8ee",
      error: { main: "#df1b41" },
      success: { main: "#1a7f5a" },
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily,
      h5: { fontWeight: 700, letterSpacing: "-0.02em" },
      h6: { fontWeight: 600, letterSpacing: "-0.01em" },
      subtitle1: { fontWeight: 600 },
      button: { textTransform: "none", fontWeight: 600 },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          body: { backgroundColor: "#f6f9fc" },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: 8, paddingTop: 10, paddingBottom: 10, fontSize: "0.95rem" },
          containedPrimary: {
            boxShadow: "0 1px 1px rgba(0,0,0,0.03), 0 2px 5px rgba(60,66,87,0.15)",
            "&:hover": {
              backgroundColor: "#4b45c6",
              boxShadow: "0 1px 2px rgba(0,0,0,0.05), 0 4px 10px rgba(60,66,87,0.22)",
            },
          },
        },
      },
      MuiTextField: { defaultProps: { variant: "outlined", fullWidth: true } },
      MuiOutlinedInput: {
        styleOverrides: {
          root: { borderRadius: 8, backgroundColor: "#ffffff" },
          notchedOutline: { borderColor: "#e3e8ee" },
        },
      },
      MuiPaper: { styleOverrides: { root: { backgroundImage: "none" } } },
      MuiLink: { styleOverrides: { root: { fontWeight: 600, cursor: "pointer" } } },
    },
  });
