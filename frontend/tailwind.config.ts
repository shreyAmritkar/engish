import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: "#0f172a",
        slate: "#334155",
        accent: "#2563eb",
        success: "#16a34a",
        warning: "#d97706",
        surface: "#f8fafc",
      },
    },
  },
  plugins: [],
};

export default config;
