// Implements 1.账号与关系管理
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import LoginPage from '@/pages/login/login.vue';
import { useAuthStore } from '@/stores/auth';

const createUniMock = () =>
  ({
    showToast: vi.fn(),
    showModal: vi.fn(),
    redirectTo: vi.fn(),
    getSystemInfoSync: vi.fn(() => ({ platform: 'test', model: 'unit' }))
  } as any);

const mountLogin = () =>
  mount(LoginPage, {
    global: {
      stubs: {
        AppHeader: { template: '<div />' }
      }
    }
  });

describe('LoginPage interactions', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    (globalThis as any).uni = createUniMock();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.restoreAllMocks();
    delete (globalThis as any).uni;
  });

  it('disables OTP request until phone is valid and runs countdown', async () => {
    vi.useFakeTimers();
    const wrapper = mountLogin();
    const requestBtn = wrapper.get('[data-test="request-code"]');
    expect((requestBtn.element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[data-test="phone-input"]').setValue('13800138000');
    expect((requestBtn.element as HTMLButtonElement).disabled).toBe(false);

    await requestBtn.trigger('click');
    expect((globalThis as any).uni.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '验证码已发送' })
    );
    expect((requestBtn.element as HTMLButtonElement).disabled).toBe(true);
    expect(requestBtn.text()).toContain('60s');

    vi.advanceTimersByTime(2000);
    await nextTick();
    expect(requestBtn.text()).toContain('58s');
    vi.useRealTimers();
  });

  it('submits login and handles single-device notice', async () => {
    const wrapper = mountLogin();
    const store = useAuthStore();
    const loginSpy = vi.spyOn(store, 'loginWithCode').mockResolvedValue({
      token: 'jwt-13800138000',
      requiresBinding: false,
      deviceNotice: '新设备提醒'
    });

    await wrapper.get('[data-test="phone-input"]').setValue('13800138000');
    await wrapper.get('[data-test="code-input"]').setValue('654321');
    await wrapper.get('[data-test="login-button"]').trigger('click');
    await flushPromises();

    expect(loginSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        phone: '13800138000',
        code: '654321'
      })
    );
    expect((globalThis as any).uni.showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '登录成功' })
    );
    expect((globalThis as any).uni.showModal).toHaveBeenCalledWith(
      expect.objectContaining({ content: '新设备提醒' })
    );
    expect((globalThis as any).uni.redirectTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/pages/index/index' })
    );
  });
});

describe('auth store mutations', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('persists jwt token after login', async () => {
    const store = useAuthStore();
    const result = await store.loginWithCode({
      phone: '13800138000',
      code: '123456',
      deviceId: 'device-a'
    });

    expect(store.token).toContain('13800138000');
    expect(result.token).toBe(store.token);
    expect(store.partnerBound).toBe(true);
  });

  it('flags single-device notice on device change', async () => {
    const store = useAuthStore();
    await store.loginWithCode({
      phone: '13800138000',
      code: '123456',
      deviceId: 'device-a'
    });
    const nextLogin = await store.loginWithCode({
      phone: '13800138000',
      code: '123456',
      deviceId: 'device-b'
    });

    expect(nextLogin.deviceNotice).toMatch(/设备/);
    expect(store.singleDeviceMessage).toMatch(/设备/);
  });
});

Login flow now enforces phone validation before requesting OTP, provides a visible countdown, funnels login submissions through the Pinia store, and reacts to single-device notices/navigations on success (`src/pages/login/login.vue`). Vitest coverage exercises the countdown guard, successful login path, and Pinia state mutations (token persistence plus single-device messaging) (`tests/pages/login/login.spec.ts`).

Tests were not executed because the read-only sandbox blocks installing the `vitest` binary. Recommended next step: run `npm install` then `npm run test` locally or in a writable environment.