// Implements 1.账号与关系管理
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import BindingPage from '@/pages/binding/binding.vue';
import {
  createBindingCode,
  fetchDualConfirmStatus,
  submitPartnerCode
} from '@/services/binding';

vi.mock('@/services/binding', () => ({
  createBindingCode: vi.fn(),
  fetchDualConfirmStatus: vi.fn(),
  submitPartnerCode: vi.fn()
}));

const mockCreateBindingCode = vi.mocked(createBindingCode);
const mockFetchDualConfirmStatus = vi.mocked(fetchDualConfirmStatus);
const mockSubmitPartnerCode = vi.mocked(submitPartnerCode);

const mountBindingPage = () =>
  mount(BindingPage, {
    global: {
      stubs: {
        AppHeader: { template: '<view />' }
      }
    }
  });

const createSessionPayload = () => ({
  code: '246810',
  expiresIn: 90,
  qrcodeUrl: 'qr.png',
  dualConfirm: {
    selfConfirmed: true,
    partnerConfirmed: false,
    updatedAt: '2024-04-01T10:00:00Z'
  }
});

describe('BindingPage flow', () => {
  beforeEach(() => {
    mockCreateBindingCode.mockResolvedValue(createSessionPayload());
    mockFetchDualConfirmStatus.mockResolvedValue(
      createSessionPayload().dualConfirm
    );
    mockSubmitPartnerCode.mockResolvedValue({
      message: '已通知对方确认',
      dualConfirm: {
        selfConfirmed: true,
        partnerConfirmed: true,
        updatedAt: '2024-04-01T10:05:00Z'
      }
    });
    (globalThis as any).uni = {
      showToast: vi.fn(),
      setClipboardData: vi.fn((options: any) => options?.success?.({})),
      request: vi.fn()
    };
  });

  afterEach(() => {
    vi.clearAllMocks();
    delete (globalThis as any).uni;
  });

  it('loads binding code and renders countdown', async () => {
    const wrapper = mountBindingPage();
    await flushPromises();

    expect(mockCreateBindingCode).toHaveBeenCalledTimes(1);
    expect(wrapper.get('[data-test="binding-code"]').text()).toContain('246810');
    expect(wrapper.get('[data-test="countdown"]').text()).toBe('01:30');
  });

  it('submits partner code and refreshes dual confirmations', async () => {
    const wrapper = mountBindingPage();
    await flushPromises();

    await wrapper.get('[data-test="partner-input"]').setValue('135791');
    await wrapper.get('[data-test="partner-submit"]').trigger('click');
    await flushPromises();

    expect(mockSubmitPartnerCode).toHaveBeenCalledWith('135791');
    expect(wrapper.get('[data-test="status-message"]').text()).toContain(
      '已通知对方确认'
    );
    expect(wrapper.get('[data-test="status-partner"]').text()).toContain(
      '已确认'
    );
  });

  it('shows error text when partner submission fails', async () => {
    mockSubmitPartnerCode.mockRejectedValueOnce(new Error('无效绑定码'));
    const wrapper = mountBindingPage();
    await flushPromises();

    await wrapper.get('[data-test="partner-input"]').setValue('000000');
    await wrapper.get('[data-test="partner-submit"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-test="error-message"]').text()).toContain(
      '无效绑定码'
    );
  });

  it('surfaces binding code failures on load', async () => {
    mockCreateBindingCode.mockRejectedValueOnce(new Error('服务器异常'));
    const wrapper = mountBindingPage();
    await flushPromises();

    expect(wrapper.get('[data-test="error-message"]').text()).toContain(
      '服务器异常'
    );
  });
});

**Binding Experience**
- `src/pages/binding/binding.vue:6` wires the binding dashboard—generating 6 位码 with countdown, QR 分享、复制、刷新按钮、伙伴输入以及双向确认 chips synced via binding APIs while handling lifecycle cleanup and toast提示。
- `tests/pages/binding/binding.spec.ts:1` mocks the binding service with Vitest to cover happy load, partner成功、partner失败和初始生成失败，确保倒计时、提示信息和错误分支都被断言。

**Testing**
- `npm test` (Vitest) can’t run in the current read-only sandbox and because `vitest` isn’t installed yet (`sh: vitest: command not found`). Please run `npm install` followed by `npm test` locally once dependencies can be installed.