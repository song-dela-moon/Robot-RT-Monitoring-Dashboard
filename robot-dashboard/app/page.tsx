'use client';

import { useState, useEffect, useCallback } from 'react';
import RobotSelector from '@/components/RobotSelector';
import PeriodSelector, { Period } from '@/components/PeriodSelector';
import LogChart from '@/components/LogChart';
import SseConnector from '@/components/SseConnector';
import StatsPanel from '@/components/StatsPanel';
import { RobotLog } from '@/types';

export default function Home() {
  const [selectedRobot, setSelectedRobot] = useState<string>('');
  const [period, setPeriod] = useState<Period>('daily');
  const [historicalLogs, setHistoricalLogs] = useState<RobotLog[]>([]);
  const [realtimeLogs, setRealtimeLogs] = useState<RobotLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [sseConnected, setSseConnected] = useState(false);

  // 기간에 따른 from/to 계산 (로컬 타임존 적용)
  const getDateRange = () => {
    const now = new Date();
    // 로컬 타임존 오프셋 보정 (toISOString은 항상 UTC 기준이므로)
    const tzOffsetMs = now.getTimezoneOffset() * 60000;
    const localNow = new Date(now.getTime() - tzOffsetMs);
    const to = localNow.toISOString().slice(0, 19);

    let fromDate: Date;
    if (period === 'minute') {
      fromDate = new Date(now.getTime() - 60000);
    } else if (period === 'daily') {
      fromDate = new Date(now); fromDate.setHours(0, 0, 0, 0);
    } else if (period === 'weekly') {
      fromDate = new Date(now); fromDate.setDate(now.getDate() - 7);
    } else {
      fromDate = new Date(now); fromDate.setDate(now.getDate() - 30);
    }

    const localFrom = new Date(fromDate.getTime() - tzOffsetMs);
    const from = localFrom.toISOString().slice(0, 19);
    return { from, to };
  };

  // 로봇 / 기간 변경 시 과거 데이터 fetch
  useEffect(() => {
    if (!selectedRobot) return;
    const { from, to } = getDateRange();
    setLoading(true);
    setRealtimeLogs([]);
    fetch(`/api/robots/${selectedRobot}/logs?from=${from}&to=${to}`)
      .then(r => r.json())
      .then((data: RobotLog[]) => setHistoricalLogs(data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [selectedRobot, period]);

  // SSE 이벤트 수신 콜백 (High-Res 전용)
  const handleSseEvent = useCallback((log: RobotLog) => {
    // console.log('[SSE-Home] Event Received:', log.id, log.recordedAt);
    setRealtimeLogs(prev => {
      const next = [...prev, log];
      return next.slice(-60); // 고해상도 60개(1분) 윈도우 유지
    });
  }, []);

  const handleSseConnect = useCallback(() => {
    console.log('[SSE-Home] Connected');
    setSseConnected(true);
  }, []);

  const handleSseDisconnect = useCallback(() => {
    console.log('[SSE-Home] Disconnected');
    setSseConnected(false);
  }, []);

  const allLogs = (period === 'minute')
    ? [...historicalLogs, ...realtimeLogs].slice(-60)
    : historicalLogs;

  const latestLog = allLogs[allLogs.length - 1];

  return (
    <div className="space-y-6">
      {/* 컨트롤 패널 */}
      <div className="bg-surface border border-border rounded-xl p-5 flex flex-wrap gap-4 items-center justify-between">
        <div className="flex flex-wrap gap-4 items-center">
          <RobotSelector selected={selectedRobot} onSelect={setSelectedRobot} />
          <PeriodSelector selected={period} onSelect={setPeriod} />
        </div>
        {/* SSE 상태 표시 */}
        <div className="flex items-center gap-2 text-sm">
          {period === 'minute' && selectedRobot && (
            <>
              <span className="relative flex h-3 w-3">
                {sseConnected && <span className="sse-pulse" />}
                <span className={`w-3 h-3 rounded-full ${sseConnected ? 'bg-accent' : 'bg-border'}`} />
              </span>
              <span className={sseConnected ? 'text-accent' : 'text-muted'}>
                {sseConnected ? 'SSE 연결됨 (실시간)' : 'SSE 연결 중...'}
              </span>
            </>
          )}
          {period !== 'minute' && (
            <span className="text-muted">과거 데이터 조회 모드 (SSE 없음)</span>
          )}
        </div>
      </div>

      {/* 선택 안 됐을 때 */}
      {!selectedRobot && (
        <div className="flex flex-col items-center justify-center py-24 text-muted space-y-3">
          <span className="text-5xl">🤖</span>
          <p className="text-lg">로봇을 선택하세요</p>
        </div>
      )}

      {/* 데이터 표시 */}
      {selectedRobot && (
        <>
          {/* 통계 카드 */}
          <StatsPanel log={latestLog} totalCount={allLogs.length} realtimeCount={realtimeLogs.length} />

          {/* 차트 */}
          <div className="bg-surface border border-border rounded-xl p-5">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold text-white">
                CPU 사용률 &amp; 위치 — {selectedRobot}
                <span className="ml-2 text-muted text-sm font-normal">
                  ({period === 'minute' ? '초간 (High-Res)' : period})
                </span>
              </h2>
              {loading && <span className="text-muted text-sm animate-pulse">데이터 로딩 중...</span>}
            </div>
            <LogChart logs={allLogs} period={period} />
          </div>
        </>
      )}

      {/* SSE 커넥터 (High-Res 모드 전용, UI 없음) */}
      {period === 'minute' && selectedRobot && (
        <SseConnector
          robotId={selectedRobot}
          onEvent={handleSseEvent}
          onConnect={handleSseConnect}
          onDisconnect={handleSseDisconnect}
        />
      )}
    </div>
  );
}
