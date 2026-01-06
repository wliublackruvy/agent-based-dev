// Implements 2.权限引导与存活看板
import { httpRequest } from '@/services/httpClient';

export type PermissionGuideStepId =
  | 'location'
  | 'notification'
  | 'autostart'
  | 'usage';

export interface PermissionGuideStepPayload {
  id: PermissionGuideStepId;
  completed: boolean;
  completedAt: string | null;
}

export interface PermissionGuideStateResponse {
  deviceId: string;
  steps: PermissionGuideStepPayload[];
  updatedAt: string;
}

export interface SavePermissionGuidePayload {
  deviceId: string;
  steps: PermissionGuideStepPayload[];
}

type RequestMethod = 'GET' | 'PUT';

const API_PREFIX = '/api/permissions/guide';

const permissionRequest = async <T>(
  method: RequestMethod,
  payload?: Record<string, unknown>,
  query?: Record<string, string | number | boolean | null | undefined>
): Promise<T> =>
  httpRequest<T>({
    url: API_PREFIX,
    method,
    payload,
    query,
    fallbackError: '权限引导接口异常，请稍后再试'
  });

export const fetchPermissionGuideState = (deviceId: string) =>
  permissionRequest<PermissionGuideStateResponse>('GET', undefined, {
    deviceId
  });

export const savePermissionGuideState = (
  payload: SavePermissionGuidePayload
) => permissionRequest<PermissionGuideStateResponse>('PUT', payload);