// Implements 3.实时感知模块
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DeviceStatusPanel from '@/components/DeviceStatusPanel.vue';
import { fetchDeviceStatus } from '@/services/deviceStatus';

vi.mock('@/services/deviceStatus', () => ({
  fetchDeviceStatus: vi.fn()
}));

const mockFetch = vi.mocked(fetchDeviceStatus);

const basePayload = {
  deviceId: 'device-1',
  batteryPercent: 72,
  networkType: 'wifi' as const,
  wifiName: 'HomeLab',
  heartbeatAt: '2024-06-01T10:00:00Z',
  reportedAt: '2024-06-01T10:00:20Z'
};

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));

const mountPanel = (
  props: Partial<{
    deviceId: string;
    pollIntervalMs: number;
    lowBatteryPercent: number;
    criticalBatteryPercent: number;
    staleHeartbeatSeconds: number;
    staleDataSeconds: number;
  }> = {}
) =>
  mount(DeviceStatusPanel, {
    props: {
      deviceId: 'device-1',
      ...props
    }
  });

describe('DeviceStatusPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-06-01T10:05:00Z'));
    mockFetch.mockResolvedValue(clone(basePayload));
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it('renders battery, network, and heartbeat details from the API payload', async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(mockFetch).toHaveBeenCalledWith('device-1');
    expect(wrapper.get('[data-test="battery-value"]').text()).toBe('72%');
    expect(wrapper.get('[data-test="network-value"]').text()).toBe('WiFi');
    expect(wrapper.get('[data-test="network-detail"]').text()).toContain(
      'HomeLab'
    );
    expect(wrapper.get('[data-test="heartbeat-badge"]').text()).toBe('实时在线');

    wrapper.unmount();
  });

  it('applies the critical battery class and warning copy once under the threshold', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      batteryPercent: 9
    });

    const wrapper = mountPanel({ criticalBatteryPercent: 12 });
    await flushPromises();

    expect(wrapper.get('[data-test="battery-pill"]').classes()).toContain(
      'battery-critical'
    );
    expect(wrapper.get('[data-test="battery-warning"]').text()).toContain(
      '12%'
    );

    wrapper.unmount();
  });

  it('applies the low battery class and warning when within the caution range', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      batteryPercent: 25
    });

    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.get('[data-test="battery-pill"]').classes()).toContain(
      'battery-low'
    );
    expect(wrapper.get('[data-test="battery-warning"]').text()).toContain('30%');

    wrapper.unmount();
  });

  it('elevates the whole panel to critical when the battery is under the threshold', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      batteryPercent: 8
    });

    const wrapper = mountPanel({ criticalBatteryPercent: 10 });
    await flushPromises();

    expect(
      wrapper.get('[data-test="device-status-panel"]').classes()
    ).toContain('panel-critical');

    wrapper.unmount();
  });

  it('flags the panel as caution when battery is low but not critical', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      batteryPercent: 22
    });

    const wrapper = mountPanel({
      lowBatteryPercent: 25,
      criticalBatteryPercent: 15
    });
    await flushPromises();

    const classes = wrapper.get('[data-test="device-status-panel"]').classes();
    expect(classes).toContain('panel-caution');
    expect(classes).not.toContain('panel-critical');

    wrapper.unmount();
  });

  it('marks the heartbeat badge as stale when last heartbeat exceeds the limit', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      heartbeatAt: '2024-06-01T09:50:00Z'
    });

    const wrapper = mountPanel({ staleHeartbeatSeconds: 300 });
    await flushPromises();

    expect(wrapper.get('[data-test="heartbeat-badge"]').classes()).toContain(
      'badge-stale'
    );
    expect(wrapper.get('[data-test="heartbeat-badge"]').text()).toBe('心跳失联');
    const warningCopy = wrapper.get('[data-test="heartbeat-warning"]').text();
    expect(warningCopy).toContain('心跳 900 秒未回传');
    expect(warningCopy).toContain('300 秒');

    wrapper.unmount();
  });

  it('treats malformed heartbeat timestamps as stale and surfaces warnings', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      heartbeatAt: 'invalid-date'
    });

    const wrapper = mountPanel({ staleHeartbeatSeconds: 120 });
    await flushPromises();

    expect(wrapper.get('[data-test="heartbeat-badge"]').classes()).toContain(
      'badge-stale'
    );
    expect(wrapper.get('[data-test="heartbeat-warning"]').text()).toBe(
      '心跳超过 120 秒未回传'
    );

    wrapper.unmount();
  });

  it('sets the network card to offline styling when the device loses signal', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      networkType: 'offline' as const
    });

    const wrapper = mountPanel();
    await flushPromises();

    expect(wrapper.get('[data-test="network-card"]').classes()).toContain(
      'network-offline'
    );

    wrapper.unmount();
  });

  it('surfaces stale data warnings and styling when reportedAt is too old', async () => {
    mockFetch.mockResolvedValueOnce({
      ...clone(basePayload),
      reportedAt: '2024-06-01T09:55:00Z'
    });

    const wrapper = mountPanel({ staleDataSeconds: 120 });
    await flushPromises();

    expect(
      wrapper.get('[data-test="device-status-panel"]').classes()
    ).toContain('panel-stale');
    expect(wrapper.get('[data-test="stale-warning"]').text()).toContain('未更新');

    wrapper.unmount();
  });

  it('disables the manual refresh action when no device is bound', async () => {
    const wrapper = mountPanel({ deviceId: '' });
    await flushPromises();

    const refreshBtn = wrapper.get('[data-test="refresh-btn"]').element;
    expect(refreshBtn.getAttribute('disabled')).not.toBeNull();

    wrapper.unmount();
  });
});