import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Robot Monitor — Back Pressure Practice',
  description: '로봇 실시간 모니터링 대시보드 (SSE + Back Pressure)',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <header className="border-b border-border bg-surface sticky top-0 z-50">
          <div className="max-w-7xl mx-auto px-6 h-14 flex items-center gap-3">
            <span className="text-2xl">🤖</span>
            <span className="font-semibold text-white text-lg">Robot Monitor</span>
            <span className="text-muted text-sm ml-2">Back Pressure Practice</span>
          </div>
        </header>
        <main className="max-w-7xl mx-auto px-6 py-8">
          {children}
        </main>
      </body>
    </html>
  );
}
