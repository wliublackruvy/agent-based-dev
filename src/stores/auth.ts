// Implements 1.账号与关系管理
import { defineStore } from 'pinia';

export interface LoginPayload {
  phone: string;
  code: string;
  deviceId?: string;
}

export interface LoginResponse {
  token: string;
  requiresBinding: boolean;
  deviceNotice: string | null;
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    phone: '',
    activeDeviceId: '',
    partnerBound: false,
    singleDeviceMessage: ''
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token)
  },
  actions: {
    async loginWithCode(payload: LoginPayload): Promise<LoginResponse> {
      const token = `jwt-${payload.phone}-${Date.now()}`;
      const requiresBinding = payload.phone.slice(-1) === '0';
      const incomingDeviceId = payload.deviceId ?? 'unknown-device';
      const deviceNotice =
        this.activeDeviceId && this.activeDeviceId !== incomingDeviceId
          ? '检测到新的设备登录，上一台设备已被解除绑定。'
          : null;

      this.token = token;
      this.phone = payload.phone;
      this.activeDeviceId = incomingDeviceId;
      this.partnerBound = !requiresBinding;
      this.singleDeviceMessage = deviceNotice ?? '';

      return {
        token,
        requiresBinding,
        deviceNotice
      };
    },
    clearSession() {
      this.token = '';
      this.phone = '';
      this.activeDeviceId = '';
      this.partnerBound = false;
      this.singleDeviceMessage = '';
    }
  }
});