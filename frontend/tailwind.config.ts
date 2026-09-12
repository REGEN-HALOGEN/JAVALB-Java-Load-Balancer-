import type { Config } from "tailwindcss";

export default {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./lib/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // token surface
        surface: {
          950: "#020617",
          900: "#0b1120",
          850: "#111a2e",
          800: "#16213a",
          700: "#1e2c47",
        },
      },
    },
  },
  plugins: [],
} satisfies Config;