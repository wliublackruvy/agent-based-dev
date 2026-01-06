// Implements 2.权限引导与存活看板
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PermissionStatusBoard from '@/components/PermissionStatusBoard.vue';
import { fetchPermissionStatus } from '@/services/permissionStatus';

vi.mock('@/services/permissionStatus', () => ({
  fetchPermissionStatus: vi.fn()
}));

const mockFetch = vi.mocked(fetchPermissionStatus);

const basePayload = {
  deviceId: 'device-x',
  heartbeatAt: '2024-06-01T10:00:00Z',
  statuses: [
    {
      type: 'location',
      enabled: true,
      traceAt: '2024-06-01T09:59:30Z',
      pushWarning: false,
      warningMessage: ''
    },
    {
      type: 'notification',
      enabled: false,
      traceAt: '2024-06-01T09:57:00Z',
      pushWarning: true,
      warningMessage: '推送延迟 80 秒'
    },
    {
      type: 'uninstall',
      enabled: true,
      traceAt: '2024-06-01T09:40:00Z',
      pushWarning: false,
      warningMessage: ''
    }
  ]
};

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));

const mountBoard = (
  props: Partial<{
    deviceId: string;
    pollIntervalMs: number;
    staleHeartbeatSeconds: number;
  }> = {}
) =>
  mount(PermissionStatusBoard, {
    props: {
      deviceId: 'device-x',
      ...props
    }
  });

describe('PermissionStatusBoard', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-01T10:01:00Z'));
    mockFetch.mockResolvedValue(clone(basePayload));
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('renders live permission cells with trace text and push badge', async () => {
    const wrapper = mountBoard();

    await flushPromises();

    expect(mockFetch).toHaveBeenCalledWith('device-x');
    expect(wrapper.get('[data-test="status-card-location"]').text()).toContain(
      '定位权限'
    );
    expect(
      wrapper.get('[data-test="status-card-notification"]').text()
    ).toContain('Push 警告');
    expect(
      wrapper.get('[data-test="status-card-uninstall"]').text()
    ).toContain('卸载监控');
    expect(wrapper.get('[data-test="last-fetch"]').text()).toContain('最近拉取');
  });

  it('shows heartbeat text with relative timestamp once data loads', async () => {
    const wrapper = mountBoard();
    await flushPromises();

    expect(wrapper.get('[data-test="heartbeat-text"]').text()).toBe(
      '最后心跳 1 分钟前'
    );
  });

  it('polls backend and marks stale heartbeat when threshold is exceeded', async () => {
    mockFetch
      .mockResolvedValueOnce(clone(basePayload))
      .mockResolvedValueOnce({
        ...clone(basePayload),
        heartbeatAt: '2024-06-01T09:55:00Z'
      });

    const wrapper = mountBoard({
      pollIntervalMs: 1000,
      staleHeartbeatSeconds: 120
    });
    await flushPromises();

    expect(wrapper.get('[data-test="heartbeat-badge"]').text()).toBe('实时在线');

    await vi.advanceTimersByTimeAsync(1000);
    await flushPromises();

    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(wrapper.get('[data-test="heartbeat-badge"]').text()).toBe('心跳失联');
  });

  it('shows error text when request fails and clears after manual refresh', async () => {
    mockFetch.mockRejectedValueOnce(new Error('网络异常'));

    const wrapper = mountBoard();
    await flushPromises();

    expect(wrapper.get('[data-test="error-text"]').text()).toBe('网络异常');

    mockFetch.mockResolvedValueOnce(clone(basePayload));
    await wrapper.get('[data-test="refresh-btn"]').trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-test="error-text"]').exists()).toBe(false);
  });

  it('resets heartbeat, traces, and labels after device unbind', async () => {
    const wrapper = mountBoard();
    await flushPromises();

    await wrapper.setProps({ deviceId: '' });
    await flushPromises();

    expect(wrapper.get('[data-test="error-text"]').text()).toBe(
      '未绑定设备，等待后台心跳'
    );
    expect(wrapper.get('[data-test="status-card-location"]').text()).toContain(
      '等待轨迹回传'
    );
    expect(wrapper.get('[data-test="last-fetch"]').text()).toContain('尚未同步');
  });
});

- Introduced a `heartbeatBadgeLabel` computed plus a minimum poll interval guard so the UI always reflects stale status logic even when custom polling values are supplied, and ensured the board footer explicitly uses `flex` to satisfy the reviewer’s layout concern (`src/components/PermissionStatusBoard.vue:1`).
- Expanded the Vitest suite with an assertion around the relative heartbeat subtitle to lock in the computed state formatting while keeping the existing polling, error, and reset flows covered (`tests/components/PermissionStatusBoard.spec.ts:1`).

Tests were not executed locally because the sandbox is read-only and lacks the Vitest binary (`vitest: command not found`). After syncing these changes, please run `npx vitest run tests/components/PermissionStatusBoard.spec.ts` to verify.