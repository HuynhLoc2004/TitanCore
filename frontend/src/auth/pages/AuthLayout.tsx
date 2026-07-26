import type { ReactNode } from 'react';

export function AuthLayout({ children, title, subtitle }: {
  children: ReactNode;
  title: string;
  subtitle: string;
}) {
  return (
    <main className="min-h-screen overflow-hidden bg-[var(--tc-bg)] text-white">
      <section className="tc-auth-stage">
        <div className="tc-stars" aria-hidden="true" />
        <div className="tc-moon" aria-hidden="true" />
        <div className="tc-auth-art" aria-hidden="true">
          <div className="tc-boss-eye" />
          <div className="tc-sword" />
          <div className="tc-shield" />
        </div>
        <div className="tc-auth-copy">
          <p className="tc-eyebrow">TitanCore Raidworks</p>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        {children}
      </section>
    </main>
  );
}
