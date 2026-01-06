// Implements 5.安全与紧急联系
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import EmergencyActionButton from '@/components/EmergencyActionButton.vue';
import { httpRequest, safeUni } from '@/services/httpClient';

vi.mock('@/services/httpClient', () => ({
  httpRequest: vi.fn(),
  safeUni: vi.fn()
}));

const mockHttpRequest = vi.mocked(httpRequest);
const mockSafeUni = vi.mocked(safeUni);

type UniBridgeMock = {
  vibrateLong: ReturnType<typeof vi.fn>;
  onNetworkStatusChange: ReturnType<typeof vi.fn>;
  offNetworkStatusChange: ReturnType<typeof vi.fn>;
};

let uniBridge: UniBridgeMock;

const mountButton = (props: Record<string, unknown> = {}) =>
  mount(EmergencyActionButton, { props });

const openConfirm = async (wrapper: ReturnType<typeof mountButton>) => {
  await wrapper.get('[data-test="emergency-btn"]').trigger('click');
  await flushPromises();
};

beforeEach(() => {
  mockHttpRequest.mockResolvedValue({});
  uniBridge = {
    vibrateLong: vi.fn(),
    onNetworkStatusChange: vi.fn(),
    offNetworkStatusChange: vi.fn()
  };
  mockSafeUni.mockReturnValue(uniBridge as any);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('EmergencyActionButton', () => {
  it('surfaces offline fallback instructions when disconnected', async () => {
    const wrapper = mountButton({
      offlineInstructions: '自定义离线提示'
    });

    window.dispatchEvent(new Event('offline'));
    await flushPromises();

    const bannerText = wrapper.get('[data-test="status-banner"]').text();
    expect(bannerText).toContain('离线排队');
    const instruction = wrapper
      .get('[data-test="banner-instruction"]')
      .text()
      .trim();
    expect(instruction).toBe('自定义离线提示');

    wrapper.unmount();
  });

  it('calls the backend endpoint and vibrates after confirmation', async () => {
    const wrapper = mountButton();

    await openConfirm(wrapper);
    await wrapper.get('[data-test="confirm-send"]').trigger('click');
    await flushPromises();

    expect(mockHttpRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/api/emergency/alerts',
        method: 'POST'
      })
    );
    expect(wrapper.emitted('alert-sent')).toHaveLength(1);
    expect(wrapper.emitted('alert-sent')?.[0][0]).toEqual({ queued: false });
    expect(uniBridge.vibrateLong).toHaveBeenCalled();

    wrapper.unmount();
  });

  it('queues the alert while offline and flushes it once back online', async () => {
    const wrapper = mountButton();

    window.dispatchEvent(new Event('offline'));
    await flushPromises();

    await openConfirm(wrapper);
    await wrapper.get('[data-test="confirm-send"]').trigger('click');
    await flushPromises();

    expect(mockHttpRequest).not.toHaveBeenCalled();
    expect(wrapper.emitted('queued')).toHaveLength(1);

    window.dispatchEvent(new Event('online'));
    await flushPromises();

    expect(mockHttpRequest).toHaveBeenCalledTimes(1);
    expect(wrapper.emitted('alert-sent')).toHaveLength(1);
    expect(wrapper.emitted('alert-sent')?.[0][0]).toEqual({ queued: true });

    wrapper.unmount();
  });

  it('prevents rapid re-triggers according to the debounce timer', async () => {
    vi.useFakeTimers();
    const wrapper = mountButton({ debounceMs: 2000 });

    await openConfirm(wrapper);
    await wrapper.get('[data-test="confirm-send"]').trigger('click');
    await flushPromises();

    expect(mockHttpRequest).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-test="emergency-btn"]').trigger('click');
    expect(wrapper.find('[data-test="confirm-modal"]').exists()).toBe(false);

    vi.advanceTimersByTime(2000);
    await flushPromises();

    await openConfirm(wrapper);
    await wrapper.get('[data-test="confirm-send"]').trigger('click');
    await flushPromises();

    expect(mockHttpRequest).toHaveBeenCalledTimes(2);

    wrapper.unmount();
  });
});

Button is now mounted globally via `App.vue`, so every page (and thus all “key pages”) presents the SOS FAB overlay in a consistent position while leveraging the component’s backend trigger, confirmation dialog, vibration hook, and offline queueing/banner logic. The Vitest suite now also asserts the offline fallback instructions plus the existing success, queueing, and debounce flows to lock in the required behaviors.

Tests were not executed here because `vitest` is not installed in this read-only workspace (`sh: vitest: command not found`). After installing dependencies (`npm install`), run `npm test` locally to verify.