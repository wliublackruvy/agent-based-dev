// Implements 2.权限引导与存活看板
import { httpRequest } from '@/services/httpClient';

export interface DualConfirmState {
  selfConfirmed: boolean;
  partnerConfirmed: boolean;
  updatedAt: string;
}

export interface BindingCodePayload {
  code: string;
  expiresIn: number;
  qrcodeUrl: string;
  partnerNickname?: string;
  dualConfirm: DualConfirmState;
}

export interface PartnerBindResponse {
  message: string;
  dualConfirm: DualConfirmState;
}

type RequestMethod = 'GET' | 'POST';

const API_PREFIX = '/api/bind';

const bindingRequest = async <T>(
  path: string,
  method: RequestMethod = 'GET',
  payload?: Record<string, unknown>
): Promise<T> =>
  httpRequest<T>({
    url: `${API_PREFIX}${path}`,
    method,
    payload,
    fallbackError: '绑定接口请求失败，请稍后再试'
  });

export const createBindingCode = () =>
  bindingRequest<BindingCodePayload>('/code', 'POST');

export const submitPartnerCode = (partnerCode: string) =>
  bindingRequest<PartnerBindResponse>('/confirm', 'POST', {
    code: partnerCode
  });

export const fetchDualConfirmStatus = () =>
  bindingRequest<DualConfirmState>('/status', 'GET');