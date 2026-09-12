import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "JavaLB — Load Balancer Showcase",
  description:
    "Interactive load balancing demo: watch a Java load balancer route traffic across mock backends using round-robin, weighted, least-connections, least-response-time, ip-hash and random strategies.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark">
      <body className="bg-slate-950 text-slate-100 antialiased">{children}</body>
    </html>
  );
}