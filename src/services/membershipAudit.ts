// Implements 4.会员审计功能
import { httpRequest } from '@/services/httpClient';

export interface TrajectoryPoint {
  latitude: number;
  longitude: number;
  timestamp: string;
  speedKph?: number;
}

export interface TrajectoryStop {
  id: string;
  latitude: number;
  longitude: number;
  label?: string;
  durationMinutes: number;
  arrivedAt: string;
}

export interface UnlockAuditStat {
  day: string;
  totalUnlocks: number;
  firstUnlockAt?: string;
  lastUnlockAt?: string;
  riskFlag?: boolean;
}

export interface UsageSlice {
  appId: string;
  name: string;
  minutes: number;
  category: string;
  sensitive?: boolean;
  percent?: number;
}

export interface MembershipAuditResponse {
  entitled: boolean;
  entitlementName?: string;
  expiresAt?: string;
  lastSyncedAt?: string;
  replay?: {
    path: TrajectoryPoint[];
    stops: TrajectoryStop[];
  };
  unlocks?: UnlockAuditStat[];
  usage?: UsageSlice[];
}

const AUDIT_API_URL = '/api/membership/audit';

export const fetchMembershipAuditOverview = () =>
  httpRequest<MembershipAuditResponse>({
    url: AUDIT_API_URL,
    method: 'GET',
    fallbackError: '会员审计数据加载失败，请稍后再试'
  });