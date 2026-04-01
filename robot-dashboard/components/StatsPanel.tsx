'use client';

import { RobotLog } from '@/types';

interface Props {
  log?: RobotLog;
  totalCount: number;
  realtimeCount: number;
}

const PRIORITY_LABEL: Record<number, { label: string; color: string }> = {
  1: { label: 'P1 긴급', color: 'text-danger' },
  2: { label: 'P2 경고', color: 'text-warn' },
  3: { label: 'P3 보통', color: 'text-primary' },
  4: { label: 'P4 낮음', color: 'text-accent' },
  5: { label: 'P5 정보', color: 'text-muted' },
};

function StatCard({ label, value, sub, valueColor }: {
  label: string; value: string; sub?: string; valueColor?: string;
}) {
  return (
    <div className="bg-bg border border-border rounded-lg p-4 flex flex-col gap-1">
      <p className="text-xs text-muted font-medium uppercase tracking-wider">{label}</p>
      <p className={`text-2xl font-semibold tabular-nums ${valueColor ?? 'text-white'}`}>{value}</p>
      {sub && <p className="text-xs text-muted">{sub}</p>}
    </div>
  );
}

export default function StatsPanel({ log, totalCount, realtimeCount }: Props) {
  if (!log) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[...Array(4)].map((_, i) => (
          <div key={i} className="bg-bg border border-border rounded-lg p-4 h-20 animate-pulse" />
        ))}
      </div>
    );
  }

  const memPct = ((log.memUsed / log.memTotal) * 100).toFixed(1);
  const memUsedGB = (log.memUsed / 1024 / 1024).toFixed(1);
  const memTotalGB = (log.memTotal / 1024 / 1024).toFixed(0);
  const pInfo = PRIORITY_LABEL[log.priority] ?? { label: 'Unknown', color: 'text-muted' };

  const cpuColor = log.cpuUsage >= 80 ? 'text-danger' : log.cpuUsage >= 60 ? 'text-warn' : 'text-accent';

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-4">
      <StatCard
        label="CPU 사용률"
        value={`${log.cpuUsage.toFixed(1)}%`}
        sub={`r:${log.procsRunning} b:${log.procsBlocked}`}
        valueColor={cpuColor}
      />
      <StatCard
        label="메모리"
        value={`${memPct}%`}
        sub={`${memUsedGB} / ${memTotalGB} GB`}
      />
      <StatCard
        label="우선순위"
        value={pInfo.label}
        valueColor={pInfo.color}
      />
      <StatCard
        label="위치 (x, y)"
        value={`${log.posX.toFixed(2)}, ${log.posY.toFixed(2)}`}
        sub="odom (m)"
      />
      <StatCard
        label="속도 linear_x"
        value={`${log.velLinearX.toFixed(3)} m/s`}
        sub={`angular_z: ${log.velAngularZ.toFixed(3)}`}
      />
      <StatCard
        label="데이터 포인트"
        value={totalCount.toLocaleString()}
        sub={realtimeCount > 0 ? `실시간 +${realtimeCount}` : '과거 데이터'}
      />
    </div>
  );
}
