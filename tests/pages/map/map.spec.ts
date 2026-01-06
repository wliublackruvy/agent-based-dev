// Implements 3.实时感知模块
import { mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import MapPage, {
  formatCoordinate,
  formatDistance,
  formatTime
} from '@/pages/map/map.vue';
import { useMapStore } from '@/stores/map';

vi.mock('@/stores/map', () => ({
  useMapStore: vi.fn()
}));

const mockedUseMapStore = vi.mocked(useMapStore);

const createStoreState = (overrides: Record<string, unknown> = {}) => {
  const store = reactive({
    mapMode: 'standard',
    distanceMeters: 820,
    loading: false,
    error: '',
    lastUpdated: '2024-05-01T10:00:00',
    selfLocation: {
      id: 'self',
      nickname: '我',
      latitude: 31.2304,
      longitude: 121.4737
    },
    partnerLocation: {
      id: 'partner',
      nickname: '对方',
      latitude: 31.2404,
      longitude: 121.4837
    },
    fetchLocations: vi.fn(() => Promise.resolve()),
    setMapMode: vi.fn(),
    hasLocations: true
  });
  return Object.assign(store, overrides);
};

const mountMapPage = () =>
  mount(MapPage, {
    global: {
      stubs: {
        AppHeader: { template: '<div />' },
        map: { template: '<div class="map-stub"></div>' }
      }
    }
  });

describe('MapPage distance formatting and controls', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    mockedUseMapStore.mockReset();
  });

  it('renders meters when distance is below 1km', () => {
    const store = createStoreState({ distanceMeters: 640 });
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    expect(wrapper.get('[data-test="distance-text"]').text()).toContain('640 米');
  });

  it('renders kilometers once the distance exceeds 1km', () => {
    const store = createStoreState({ distanceMeters: 2300 });
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    expect(wrapper.get('[data-test="distance-text"]').text()).toContain(
      '2.3 公里'
    );
  });

  it('falls back to placeholder text when distance is invalid', () => {
    const store = createStoreState({ distanceMeters: 0 });
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    expect(wrapper.get('[data-test="distance-text"]').text()).toContain('--');
  });

  it('switches to satellite mode and refetches tiles', async () => {
    const store = createStoreState();
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    await wrapper.get('[data-test="mode-satellite"]').trigger('click');

    expect(store.setMapMode).toHaveBeenCalledWith('satellite');
    expect(store.fetchLocations).toHaveBeenCalledTimes(2);
  });

  it('ignores duplicate mode selection to prevent redundant reloads', async () => {
    const store = createStoreState();
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    await wrapper.get('[data-test="mode-standard"]').trigger('click');

    expect(store.setMapMode).not.toHaveBeenCalled();
    expect(store.fetchLocations).toHaveBeenCalledTimes(1);
  });

  it('manually refreshes the data stream', async () => {
    const store = createStoreState();
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    await wrapper.get('[data-test="manual-refresh"]').trigger('click');

    expect(store.fetchLocations).toHaveBeenCalledTimes(2);
  });

  it('shows last sync time once data is available', () => {
    const store = createStoreState({
      lastUpdated: '2024-05-01T18:10:00',
      loading: false
    });
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    expect(wrapper.get('[data-test="distance-subtitle"]').text()).toBe(
      '同步于 18:10'
    );
  });

  it('shows loading hint while syncing', () => {
    const store = createStoreState({ loading: true, lastUpdated: '' });
    mockedUseMapStore.mockReturnValue(store as any);

    const wrapper = mountMapPage();
    expect(wrapper.get('[data-test="distance-subtitle"]').text()).toBe('同步中...');
  });

  it('auto refreshes location data on schedule', () => {
    const store = createStoreState();
    mockedUseMapStore.mockReturnValue(store as any);

    mountMapPage();
    vi.advanceTimersByTime(20000);

    expect(store.fetchLocations).toHaveBeenCalledTimes(2);
  });
});

describe('format helpers', () => {
  it('switches units based on distance value', () => {
    expect(formatDistance(NaN)).toBe('--');
    expect(formatDistance(450)).toBe('450 米');
    expect(formatDistance(2300)).toBe('2.3 公里');
    expect(formatDistance(12300)).toBe('12 公里');
  });

  it('parses ISO timestamps deterministically', () => {
    expect(formatTime('2024-05-01T18:10:00')).toBe('18:10');
    expect(formatTime('2024-05-01T08:05:00Z')).toBe('08:05');
    expect(formatTime('invalid')).toBe('');
  });

  it('uses Date fallback for non-ISO timestamps', () => {
    expect(formatTime('2024/05/01 12:05:00')).toBe('12:05');
  });

  it('gracefully handles invalid coordinates', () => {
    expect(formatCoordinate()).toBe('等待定位');
    expect(
      formatCoordinate({ latitude: 31.23045, longitude: 121.47371 })
    ).toBe('31.2305, 121.4737');
  });
});

**Map + Tests**
- Live map UI (`src/pages/map/map.vue:6-262`) renders both participants as markers, joins them with a polyline, shows accessible distance/status text, and toggles UniApp’s satellite vs. standard tiles with a persistent key to reload textures immediately.
- Refresh workflow (`src/pages/map/map.vue:216-258`) restarts the polling interval and forces a fetch whenever the mode changes or manual refresh is tapped, ensuring the distance readout stays current.
- Vitest suite (`tests/pages/map/map.spec.ts:1-181`) mocks the Pinia map store to verify unit auto-switching, deduped mode toggles, manual refreshes, scheduled polling, and coverage for the exported formatting helpers.

Tests not run because the workspace is read-only and node dependencies are missing; once deps are installed, run `npm run test -- tests/pages/map/map.spec.ts` locally to verify.