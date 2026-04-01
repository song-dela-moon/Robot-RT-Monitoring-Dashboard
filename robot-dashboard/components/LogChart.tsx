'use client';

import {
  ResponsiveContainer,
  ComposedChart,
  LineChart,
  Line,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ReferenceLine,
} from 'recharts';
import { RobotLog } from '@/types';

interface Props {
  logs: RobotLog[];
  period: string;
}

// 차트 표시용 데이터 포인트 수 제한 (성능)
const MAX_POINTS = 500;

function formatTime(ts: string) {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}

function formatDate(ts: string) {
  const d = new Date(ts);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

const PRIORITY_COLORS: Record<number, string> = {
  1: '#f85149',
  2: '#d29922',
  3: '#58a6ff',
  4: '#3fb950',
  5: '#8b949e',
};

export default function LogChart({ logs, period }: Props) {
  if (logs.length === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-muted text-sm">
        데이터가 없습니다
      </div>
    );
  }

  // 포인트 수 제한: 고해상도(minute) 모드에서는 모든 샘플링 없이 모든 포인트 표시
  const isHighRes = period === 'minute';
  const step = isHighRes ? 1 : Math.max(1, Math.floor(logs.length / MAX_POINTS));
  const sampled = logs.filter((_, i) => i % step === 0);

  const fmt = (period === 'minute' || period === 'daily') ? formatTime : formatDate;

  const chartData = sampled.map(log => ({
    time: fmt(log.recordedAt),
    cpu: parseFloat(log.cpuUsage.toFixed(1)),
    memPct: parseFloat(((log.memUsed / log.memTotal) * 100).toFixed(1)),
    posX: parseFloat(log.posX.toFixed(3)),
    posY: parseFloat(log.posY.toFixed(3)),
    velLinearX: parseFloat(log.velLinearX.toFixed(4)),
    priority: log.priority,
    priorityColor: PRIORITY_COLORS[log.priority] ?? '#8b949e',
  }));

  const tooltipStyle = {
    backgroundColor: '#161b22',
    border: '1px solid #30363d',
    borderRadius: '8px',
    color: '#e6edf3',
    fontSize: '12px',
  };

  return (
    <div className="space-y-6">
      {/* CPU 사용률 */}
      <div>
        <p className="text-xs text-muted mb-2 font-medium">CPU 사용률 (%)</p>
        <ResponsiveContainer width="100%" height={180}>
          <ComposedChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
            <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#8b949e' }} interval={isHighRes ? 9 : "preserveStartEnd"} />
            <YAxis domain={[0, 100]} tick={{ fontSize: 10, fill: '#8b949e' }} />
            <Tooltip contentStyle={tooltipStyle} />
            <ReferenceLine y={80} stroke="#f85149" strokeDasharray="4 2" label={{ value: '80%', fill: '#f85149', fontSize: 10 }} />
            <ReferenceLine y={60} stroke="#d29922" strokeDasharray="4 2" />
            <Area
              type="monotone"
              dataKey="cpu"
              stroke="#58a6ff"
              fill="#58a6ff22"
              dot={false}
              name="CPU %"
              strokeWidth={1.5}
              isAnimationActive={!isHighRes}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 메모리 사용률 */}
      <div>
        <p className="text-xs text-muted mb-2 font-medium">메모리 사용률 (%)</p>
        <ResponsiveContainer width="100%" height={140}>
          <ComposedChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
            <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#8b949e' }} interval={isHighRes ? 9 : "preserveStartEnd"} />
            <YAxis domain={[0, 100]} tick={{ fontSize: 10, fill: '#8b949e' }} />
            <Tooltip contentStyle={tooltipStyle} />
            <Area
              type="monotone"
              dataKey="memPct"
              stroke="#3fb950"
              fill="#3fb95022"
              dot={false}
              name="MEM %"
              strokeWidth={1.5}
              isAnimationActive={!isHighRes}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>

      {/* 오도메트리 (X/Y 위치) */}
      <div>
        <p className="text-xs text-muted mb-2 font-medium">오도메트리 위치 (pos_x / pos_y, m)</p>
        <ResponsiveContainer width="100%" height={140}>
          <LineChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
            <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#8b949e' }} interval={isHighRes ? 9 : "preserveStartEnd"} />
            <YAxis tick={{ fontSize: 10, fill: '#8b949e' }} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend wrapperStyle={{ fontSize: '11px', color: '#8b949e' }} />
            <Line
              type="monotone"
              dataKey="posX"
              stroke="#f0883e"
              dot={false}
              name="pos_x"
              strokeWidth={1.2}
              isAnimationActive={!isHighRes}
            />
            <Line
              type="monotone"
              dataKey="posY"
              stroke="#bc8cff"
              dot={false}
              name="pos_y"
              strokeWidth={1.2}
              isAnimationActive={!isHighRes}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* 선형 속도 */}
      <div>
        <p className="text-xs text-muted mb-2 font-medium">선형 속도 vel_linear_x (m/s)</p>
        <ResponsiveContainer width="100%" height={120}>
          <ComposedChart data={chartData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#30363d" />
            <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#8b949e' }} interval={isHighRes ? 9 : "preserveStartEnd"} />
            <YAxis tick={{ fontSize: 10, fill: '#8b949e' }} />
            <Tooltip contentStyle={tooltipStyle} />
            <ReferenceLine y={0} stroke="#30363d" />
            <Area
              type="monotone"
              dataKey="velLinearX"
              stroke="#79c0ff"
              fill="#79c0ff22"
              dot={false}
              name="vel_x"
              strokeWidth={1.2}
              isAnimationActive={!isHighRes}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
