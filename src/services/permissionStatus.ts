// Implements 2.权限引导与存活看板
import { httpRequest } from '@/services/httpClient';

export type PermissionStatusType = 'location' | 'notification' | 'uninstall';

export interface PermissionStatusRecord {
  type: PermissionStatusType;
  enabled: boolean;
  traceAt: string;
  pushWarning?: boolean;
  warningMessage?: string;
}

export interface PermissionStatusResponse {
  deviceId: string;
  heartbeatAt: string;
  statuses: PermissionStatusRecord[];
}

const API_PREFIX = '/api/permissions/status';

export const fetchPermissionStatus = (deviceId: string) =>
  httpRequest<PermissionStatusResponse>({
    url: API_PREFIX,
    method: 'GET',
    query: { deviceId },
    fallbackError: '权限存活状态查询失败'
  });