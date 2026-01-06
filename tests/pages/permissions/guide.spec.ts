// Implements 2.权限引导与存活看板
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import PermissionGuidePage from '@/pages/permissions/guide.vue';
import {
  fetchPermissionGuideState,
  savePermissionGuideState
} from '@/services/permissions';
import { usePermissionStore } from '@/stores/permissions';
import { useAuthStore } from '@/stores/auth';

vi.mock('@/services/permissions', () => ({
  fetchPermissionGuideState: vi.fn(),
  savePermissionGuideState: vi.fn()
}));

const mockFetch = vi.mocked(fetchPermissionGuideState);
const mockSave = vi.mocked(savePermissionGuideState);

const baseResponse = {
  deviceId: 'device-test',
  updatedAt: '2024-05-01T08:00:00Z',
  steps: [
    { id: 'location', completed: false, completedAt: null },
    { id: 'notification', completed: false, completedAt: null },
    { id: 'autostart', completed: false, completedAt: null },
    { id: 'usage', completed: false, completedAt: null }
  ]
};

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));
const buildCompleteResponse = () => {
  const response = clone(baseResponse);
  response.steps = response.steps.map((step: any, index: number) => ({
    ...step,
    completed: true,
    completedAt: `2024-05-01T09:0${index}:00Z`
  }));
  return response;
};

const mountGuide = () =>
  mount(PermissionGuidePage, {
    global: {
      stubs: {
        AppHeader: { template: '<view />' }
      }
    }
  });

describe('permission theater flow', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mockFetch.mockResolvedValue(clone(baseResponse));
    mockSave.mockResolvedValue({
      ...clone(baseResponse),
      steps: [
        { id: 'location', completed: true, completedAt: '2024-05-01T08:05:00Z' },
        { id: 'notification', completed: false, completedAt: null },
        { id: 'autostart', completed: false, completedAt: null },
        { id: 'usage', completed: false, completedAt: null }
      ],
      updatedAt: '2024-05-01T08:05:00Z'
    });
    (globalThis as any).uni = {
      showToast: vi.fn(),
      openAppAuthorizeSetting: vi.fn(),
      getSystemInfoSync: vi.fn(() => ({ platform: 'ios', model: 'unit' })),
      setStorageSync: vi.fn(),
      getStorageSync: vi.fn()
    };
  });

  afterEach(() => {
    vi.clearAllMocks();
    delete (globalThis as any).uni;
  });

  it('renders the first permission scene snapshot', async () => {
    const wrapper = mountGuide();
    await flushPromises();

    expect(wrapper.get('[data-test="scene-title"]').text())
      .toMatchInlineSnapshot('"定位权限 · Always On 轨迹同步"');
  });

  it('moves to notification stage after completing location', async () => {
    const wrapper = mountGuide();
    await flushPromises();

    await wrapper.get('[data-test="complete-step"]').trigger('click');
    await flushPromises();

    expect(wrapper.get('[data-test="scene-title"]').text())
      .toMatchInlineSnapshot('"通知权限 · 异常无遗漏"');
  });

  it('allows manual jump through timeline items', async () => {
    const wrapper = mountGuide();
    await flushPromises();

    const timelineItems = wrapper.findAll('.timeline-item');
    expect(timelineItems.length).toBeGreaterThan(2);

    await timelineItems[2].trigger('click');

    expect(wrapper.get('[data-test="scene-title"]').text())
      .toMatchInlineSnapshot('"后台自启动 · 驻留后台不落幕"');
  });

  it('persists completion state via store and backend', async () => {
    const wrapper = mountGuide();
    await flushPromises();

    await wrapper.get('[data-test="complete-step"]').trigger('click');
    await flushPromises();

    const store = usePermissionStore();
    expect(store.steps[0].completed).toBe(true);
    expect(mockSave).toHaveBeenCalledWith(
      expect.objectContaining({
        deviceId: expect.any(String),
        steps: expect.arrayContaining([
          expect.objectContaining({ id: 'location', completed: true })
        ])
      })
    );
  });

  it('re-syncs bootstrap when auth device id arrives later', async () => {
    mountGuide();
    await flushPromises();

    const authStore = useAuthStore();
    const store = usePermissionStore();

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(store.deviceId).toBe('ios-unit');

    authStore.activeDeviceId = 'device-synced';
    await flushPromises();

    expect(mockFetch).toHaveBeenCalledTimes(2);
    expect(mockFetch).toHaveBeenLastCalledWith('device-synced');
    expect(store.deviceId).toBe('device-synced');
  });

  it('shows curtain call snapshot once all scenes are done', async () => {
    mockFetch.mockResolvedValueOnce(buildCompleteResponse());
    const wrapper = mountGuide();
    await flushPromises();

    expect(wrapper.get('[data-test="scene-title"]').text())
      .toMatchInlineSnapshot('"四幕已谢幕"');
    expect(wrapper.get('[data-test="completion-banner"]').text())
      .toMatchInlineSnapshot('"剧场谢幕所有权限保持常开后，监视方将在 60 秒内感知关闭等异常。"');
  });
});