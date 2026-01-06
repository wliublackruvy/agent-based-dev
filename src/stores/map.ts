// Implements 3.实时感知模块
import { defineStore } from 'pinia';
import { safeUni } from '@/services/httpClient';
import {
  fetchRealtimeDistance,
  type MapParticipantLocation,
  type MapTileMode
} from '@/services/location';

interface MapState {
  selfLocation: MapParticipantLocation | null;
  partnerLocation: MapParticipantLocation | null;
  distanceMeters: number;
  lastUpdated: string;
  loading: boolean;
  error: string;
  mapMode: MapTileMode;
}

const MODE_STORAGE_KEY = 'relationship-map-mode';
const FALLBACK_MODE: MapTileMode = 'standard';

const readPersistedMode = (): MapTileMode => {
  try {
    const api = safeUni();
    const cached =
      api?.getStorageSync?.(MODE_STORAGE_KEY) ??
      (typeof localStorage !== 'undefined'
        ? localStorage.getItem(MODE_STORAGE_KEY)
        : null);
    return cached === 'satellite' ? 'satellite' : FALLBACK_MODE;
  } catch {
    return FALLBACK_MODE;
  }
};

const persistMode = (mode: MapTileMode) => {
  const api = safeUni();
  try {
    api?.setStorageSync?.(MODE_STORAGE_KEY, mode);
  } catch {
    // ignore storage failures in mini-program
  }
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(MODE_STORAGE_KEY, mode);
    }
  } catch {
    // ignore localStorage failures
  }
};

export const useMapStore = defineStore('map', {
  state: (): MapState => ({
    selfLocation: null,
    partnerLocation: null,
    distanceMeters: 0,
    lastUpdated: '',
    loading: false,
    error: '',
    mapMode: readPersistedMode()
  }),
  getters: {
    hasLocations: (state) =>
      Boolean(state.selfLocation && state.partnerLocation)
  },
  actions: {
    setMapMode(mode: MapTileMode) {
      if (this.mapMode === mode) {
        return;
      }
      this.mapMode = mode;
      persistMode(mode);
    },
    async fetchLocations() {
      this.loading = true;
      this.error = '';
      try {
        const payload = await fetchRealtimeDistance(this.mapMode);
        this.selfLocation = payload.selfUser;
        this.partnerLocation = payload.partnerUser;
        this.distanceMeters = payload.distanceMeters ?? 0;
        this.lastUpdated = payload.updatedAt ?? '';
      } catch (error) {
        this.error =
          error instanceof Error
            ? error.message
            : '实时定位同步失败，请稍后再试';
      } finally {
        this.loading = false;
      }
    }
  }
});