'use client';

import { useEffect, useState } from 'react';

interface Props {
  selected: string;
  onSelect: (id: string) => void;
}

export default function RobotSelector({ selected, onSelect }: Props) {
  const [robots, setRobots] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/robots')
      .then(r => r.json())
      .then((data: string[]) => {
        setRobots(data);
        if (data.length > 0 && !selected) onSelect(data[0]);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-muted font-medium uppercase tracking-wider">로봇 선택</label>
      <select
        value={selected}
        onChange={e => onSelect(e.target.value)}
        disabled={loading}
        className="bg-bg border border-border text-white rounded-lg px-3 py-2 text-sm
                   focus:outline-none focus:border-primary transition-colors min-w-[160px]
                   disabled:opacity-50 cursor-pointer"
      >
        {loading && <option>로딩 중...</option>}
        {robots.map(id => (
          <option key={id} value={id}>{id}</option>
        ))}
      </select>
    </div>
  );
}
