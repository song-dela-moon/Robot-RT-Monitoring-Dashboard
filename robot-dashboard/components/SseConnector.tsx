'use client';

import { useEffect, useRef } from 'react';
import { RobotLog } from '@/types';

interface Props {
  robotId: string;
  onEvent: (log: RobotLog) => void;
  onConnect: () => void;
  onDisconnect: () => void;
}

/**
 * SSE 연결 컴포넌트 (UI 없음)
 *
 * daily 모드에서만 마운트되며, robotId 변경 시 기존 연결을 닫고 재연결한다.
 * Back Pressure Zone 1의 /api/robots/{robotId}/stream 을 구독한다.
 */
export default function SseConnector({ robotId, onEvent, onConnect, onDisconnect }: Props) {
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    // 이전 연결 닫기
    if (esRef.current) {
      esRef.current.close();
      onDisconnect();
    }

    const url = `/api/robots/${robotId}/stream?minPriority=5`;
    const es = new EventSource(url);
    esRef.current = es;

    es.addEventListener('open', () => {
      onConnect();
    });

    es.addEventListener('robot-log', (e: MessageEvent) => {
      try {
        const log: RobotLog = JSON.parse(e.data);
        console.log('[SSE] Event Received:', log.id);
        onEvent(log);
      } catch (err) {
        console.error('[SSE] Parse error', err);
      }
    });

    es.addEventListener('error', () => {
      console.warn('[SSE] Connection error, url:', url);
      onDisconnect();
    });

    return () => {
      es.close();
      onDisconnect();
    };
  }, [robotId, onEvent, onConnect, onDisconnect]);

  return null;
}
