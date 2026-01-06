// Implements 4.会员审计功能
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AuditPage, {
  formatStopDuration,
  formatUnlockCount,
  formatUnlockWindow,
  formatUsageMinutes
} from '@/pages/membership/audit.vue';
import {
  fetchMembershipAuditOverview,
  type MembershipAuditResponse
} from '@/services/membershipAudit';

vi.mock('@/services/membershipAudit', () => ({
  fetchMembershipAuditOverview: vi.fn()
}));

const mockFetch = vi.mocked(fetchMembershipAuditOverview);

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));

const entitledResponse: MembershipAuditResponse = {
  entitled: true,
  entitlementName: '年度会员',
  lastSyncedAt: '2024-05-01T10:10:00Z',
  replay: {
    path: [
      { latitude: 31.23, longitude: 121.47, timestamp: '2024-05-01T08:00:00Z' },
      { latitude: 31.24, longitude: 121.48, timestamp: '2024-05-01T09:00:00Z' },
      { latitude: 31.25, longitude: 121.49, timestamp: '2024-05-01T10:00:00Z' }
    ],
    stops: [
      {
        id: 'stop-1',
        latitude: 31.235,
        longitude: 121.475,
        label: '家',
        durationMinutes: 42,
        arrivedAt: '2024-05-01T08:10:00Z'
      }
    ]
  },
  unlocks: [
    {
      day: '2024-05-01',
      totalUnlocks: 26,
      firstUnlockAt: '2024-05-01T07:20:00Z',
      lastUnlockAt: '2024-05-01T23:40:00Z',
      riskFlag: true
    },
    {
      day: '2024-04-30',
      totalUnlocks: 18,
      firstUnlockAt: '2024-04-30T08:00:00Z',
      lastUnlockAt: '2024-04-30T22:10:00Z',
      riskFlag: false
    }
  ],
  usage: [
    { appId: 'social', name: 'WeChat', minutes: 120, category: '社交' },
    { appId: 'video', name: 'Bilibili', minutes: 60, category: '视频' }
  ]
};

const nonMemberResponse: MembershipAuditResponse = {
  entitled: false,
  entitlementName: '体验版',
  replay: { path: [], stops: [] },
  unlocks: [],
  usage: []
};

const mountAuditPage = () =>
  mount(AuditPage, {
    global: {
      stubs: {
        AppHeader: { template: '<view />' },
        map: { template: '<view class="map-stub" />' }
      }
    }
  });

describe('membership audit entitlement guard', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('gates premium cards behind entitlement flag', async () => {
    mockFetch.mockResolvedValue(clone(nonMemberResponse));
    const wrapper = mountAuditPage();
    await flushPromises();

    expect(wrapper.get('[data-test="audit-upsell"]').text()).toContain('解锁');
    expect(wrapper.find('[data-test="trajectory-map"]').exists()).toBe(false);

    wrapper.unmount();
  });

  it('renders map replay and statistics for entitled users', async () => {
    mockFetch.mockResolvedValue(clone(entitledResponse));
    const wrapper = mountAuditPage();
    await flushPromises();

    expect(wrapper.find('[data-test="audit-upsell"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-test="unlock-card"]').length).toBe(3);
    expect(wrapper.get('[data-test="usage-item"]').text()).toContain('社交');
    expect(wrapper.get('[data-test="replay-status"]').text()).toContain('回放');

    wrapper.unmount();
  });
});

describe('membership audit formatting helpers', () => {
  it('normalizes unlock counts and placeholders', () => {
    expect(formatUnlockCount(28)).toBe('28 次');
    expect(formatUnlockCount(-1)).toBe('--');
    expect(formatUnlockCount()).toBe('--');
  });

  it('renders unlock windows with partial fallbacks', () => {
    expect(
      formatUnlockWindow('2024-05-01T08:10:00Z', '2024-05-01T22:40:00Z')
    ).toBe('08:10 - 22:40');
    expect(formatUnlockWindow('2024-05-01T08:10:00Z')).toBe('08:10');
    expect(formatUnlockWindow(undefined, 'invalid')).toBe('--');
  });

  it('summarizes usage minutes into human readable text', () => {
    expect(formatUsageMinutes(35)).toBe('35 分钟');
    expect(formatUsageMinutes(180)).toBe('3 小时');
    expect(formatUsageMinutes(95)).toBe('1.6 小时');
    expect(formatUsageMinutes(NaN)).toBe('--');
  });

  it('describes stop durations with hour/min granularity', () => {
    expect(formatStopDuration(20)).toBe('停留 20 分钟');
    expect(formatStopDuration(160)).toBe('停留 2 小时 40 分钟');
    expect(formatStopDuration()).toBe('停留 <1 分钟');
  });
});

// NOTE: Vitest CLI isn’t wired up in the read-only harness; run `npm install && npm test` locally.

Implemented entitlement-gated membership audit dashboard with animated trajectory replay, unlock stats, timeline, and usage visualization plus formatter helpers (`src/pages/membership/audit.vue:1`). Added Vitest coverage that mocks the audit service to verify the entitlement guard and exported formatting helpers (`tests/pages/membership/audit.spec.ts:1`). Tests not run because the harness lacks `vitest`; after installing dependencies run `npm install && npm test`.