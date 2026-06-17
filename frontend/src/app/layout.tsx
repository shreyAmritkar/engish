import type { Metadata } from "next";
import "./globals.css";
import { Nav } from "@/components/Nav";
import { ToastProvider } from "@/components/ui/Toast";

export const metadata: Metadata = {
  title: "Style Communicator",
  description: "Practice written communication in your chosen style",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased">
        <ToastProvider>
          <Nav />
          <main className="max-w-6xl mx-auto px-4 py-8">{children}</main>
        </ToastProvider>
      </body>
    </html>
  );
}
