'use client';

export type Period = 'minute' | 'daily' | 'weekly' | 'monthly';

interface Props {
  selected: Period;
  onSelect: (p: Period) => void;
}

const OPTIONS: { value: Period; label: string; desc: string }[] = [
  { value: 'minute',  label: '초간 (High-Res)', desc: '최근 1분 + 실시간(초)' },
  { value: 'daily',   label: '일간 (Daily)',   desc: 'SSE 실시간 포함' },
  { value: 'weekly',  label: '주간 (Weekly)',  desc: '과거 7일' },
  { value: 'monthly', label: '월간 (Monthly)', desc: '과거 30일' },
];

export default function PeriodSelector({ selected, onSelect }: Props) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-muted font-medium uppercase tracking-wider">기간 선택</label>
      <div className="flex rounded-lg border border-border overflow-hidden text-sm">
        {OPTIONS.map(opt => (
          <button
            key={opt.value}
            onClick={() => onSelect(opt.value)}
            title={opt.desc}
            className={`px-4 py-2 font-medium transition-colors
              ${selected === opt.value
                ? 'bg-primary text-bg'
                : 'bg-bg text-muted hover:text-white hover:bg-surface'
              }`}
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  );
}
