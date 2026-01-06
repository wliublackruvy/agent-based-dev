<!-- // Implements 3.实时感知模块 -->
<template>
  <view class="map-page">
    <AppHeader title="实时地图感知" />
    <view class="map-card">
      <view class="mode-toggle" role="group" aria-label="地图模式切换">
        <button
          class="toggle-button"
          :class="{ active: store.mapMode === 'standard' }"
          type="button"
          :aria-pressed="store.mapMode === 'standard'"
          data-test="mode-standard"
          @click="handleModeChange('standard')"
        >
          标准
        </button>
        <button
          class="toggle-button"
          :class="{ active: store.mapMode === 'satellite' }"
          type="button"
          :aria-pressed="store.mapMode === 'satellite'"
          data-test="mode-satellite"
          @click="handleModeChange('satellite')"
        >
          卫星
        </button>
      </view>

      <map
        class="live-map"
        :key="mapKey"
        :latitude="mapCenter.latitude"
        :longitude="mapCenter.longitude"
        :scale="mapReady ? 13 : 5"
        :markers="markers"
        :polyline="polyline"
        :include-points="includePoints"
        :enable-satellite="store.mapMode === 'satellite'"
        :enable-traffic="store.mapMode === 'standard'"
        :show-location="false"
        data-test="live-map"
      />

      <view class="distance-panel">
        <view class="distance-head">
          <text class="distance-label">实时距离</text>
          <text
            class="distance-subtitle"
            data-test="distance-subtitle"
            :aria-busy="store.loading"
          >
            {{ subtitleText }}
          </text>
        </view>
        <text
          class="distance-value"
          data-test="distance-text"
          aria-live="polite"
        >
          {{ distanceText }}
        </text>
      </view>
    </view>

    <view class="status-card">
      <view class="status-stack">
        <view class="status-row self-chip">
          <text class="status-title">
            {{ store.selfLocation?.nickname ?? '我方设备' }}
          </text>
          <text class="status-coord">
            {{ formatCoordinate(store.selfLocation) }}
          </text>
        </view>
        <view class="status-row partner-chip">
          <text class="status-title">
            {{ store.partnerLocation?.nickname ?? '对方设备' }}
          </text>
          <text class="status-coord">
            {{ formatCoordinate(store.partnerLocation) }}
          </text>
        </view>
      </view>
      <view class="status-foot">
        <text class="status-note">
          {{ mapReady ? '双设备在线' : '等待双方授权定位' }}
        </text>
        <button
          class="ghost"
          type="button"
          :disabled="store.loading"
          data-test="manual-refresh"
          @click="handleRefresh"
        >
          {{ store.loading ? '同步中...' : '刷新' }}
        </button>
      </view>
    </view>

    <view v-if="store.error" class="error-banner" data-test="error-banner">
      {{ store.error }}
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 3.实时感知模块
import { computed, onMounted, onUnmounted } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import { useMapStore } from '@/stores/map';
import type { MapTileMode } from '@/services/location';

const store = useMapStore();
const REFRESH_MS = 20000;
const DEFAULT_CENTER = { latitude: 39.9042, longitude: 116.4074 };
let refreshTimer: ReturnType<typeof setInterval> | null = null;

const mapReady = computed(
  () => Boolean(store.selfLocation && store.partnerLocation)
);

const mapCenter = computed(() => {
  if (mapReady.value && store.selfLocation && store.partnerLocation) {
    return {
      latitude:
        (store.selfLocation.latitude + store.partnerLocation.latitude) / 2,
      longitude:
        (store.selfLocation.longitude + store.partnerLocation.longitude) / 2
    };
  }
  return DEFAULT_CENTER;
});

const markers = computed(() => {
  const result: Array<Record<string, unknown>> = [];
  if (store.selfLocation) {
    result.push({
      id: 'self',
      latitude: store.selfLocation.latitude,
      longitude: store.selfLocation.longitude,
      iconPath: 'https://dummyimage.com/40x40/3d5afe/ffffff&text=我',
      width: 32,
      height: 32,
      callout: {
        content: store.selfLocation.nickname ?? '我方设备',
        color: '#ffffff',
        fontSize: 12,
        bgColor: '#3d5afe',
        borderRadius: 40,
        padding: 6,
        display: 'ALWAYS'
      }
    });
  }
  if (store.partnerLocation) {
    result.push({
      id: 'partner',
      latitude: store.partnerLocation.latitude,
      longitude: store.partnerLocation.longitude,
      iconPath: 'https://dummyimage.com/40x40/ff7043/ffffff&text=TA',
      width: 32,
      height: 32,
      callout: {
        content: store.partnerLocation.nickname ?? '对方设备',
        color: '#ffffff',
        fontSize: 12,
        bgColor: '#ff7043',
        borderRadius: 40,
        padding: 6,
        display: 'ALWAYS'
      }
    });
  }
  return result;
});

const polyline = computed(() => {
  if (!mapReady.value || !store.selfLocation || !store.partnerLocation) {
    return [];
  }
  return [
    {
      points: [
        {
          latitude: store.selfLocation.latitude,
          longitude: store.selfLocation.longitude
        },
        {
          latitude: store.partnerLocation.latitude,
          longitude: store.partnerLocation.longitude
        }
      ],
      color: '#3d5afe',
      width: 3,
      dottedLine: true
    }
  ];
});

const includePoints = computed(() => {
  if (mapReady.value && store.selfLocation && store.partnerLocation) {
    return [
      {
        latitude: store.selfLocation.latitude,
        longitude: store.selfLocation.longitude
      },
      {
        latitude: store.partnerLocation.latitude,
        longitude: store.partnerLocation.longitude
      }
    ];
  }
  return [DEFAULT_CENTER];
});

const mapKey = computed(() => `${store.mapMode}-${store.lastUpdated}`);

const distanceText = computed(() => formatDistance(store.distanceMeters));
const subtitleText = computed(() => {
  if (store.loading) {
    return '同步中...';
  }
  const formatted = formatTime(store.lastUpdated);
  return formatted ? `同步于 ${formatted}` : '等待定位授权';
});

const handleModeChange = (mode: MapTileMode) => {
  if (store.mapMode === mode) {
    return;
  }
  store.setMapMode(mode);
  handleRefresh();
  restartTimer();
};

const handleRefresh = () => {
  store.fetchLocations();
};

function restartTimer() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  if (typeof setInterval === 'function') {
    refreshTimer = setInterval(() => {
      store.fetchLocations();
    }, REFRESH_MS);
  }
}

onMounted(() => {
  handleRefresh();
  restartTimer();
});

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
});

function formatDistance(distance: number) {
  if (!Number.isFinite(distance) || distance <= 0) {
    return '--';
  }
  if (distance < 1000) {
    return `${Math.round(distance)} 米`;
  }
  const km = distance / 1000;
  const precision = km >= 10 ? 0 : 1;
  return `${km.toFixed(precision)} 公里`;
}

function formatTime(timestamp?: string) {
  if (!timestamp) {
    return '';
  }
  const isoMatch = timestamp.match(/T(\d{2}):(\d{2})/);
  if (isoMatch) {
    return `${isoMatch[1]}:${isoMatch[2]}`;
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
}

function formatCoordinate(
  point?: { latitude: number; longitude: number } | null
) {
  if (!point) {
    return '等待定位';
  }
  return `${point.latitude.toFixed(4)}, ${point.longitude.toFixed(4)}`;
}

export { formatDistance, formatTime, formatCoordinate };
</script>

<style scoped>
.map-page {
  min-height: 100vh;
  padding-bottom: 48rpx;
  background: #f4f5fa;
}

.map-card {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 16rpx 40rpx rgba(39, 60, 117, 0.08);
}

.mode-toggle {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
  flex-wrap: wrap;
  align-items: center;
}

.toggle-button {
  flex: 1;
  padding: 16rpx 0;
  border-radius: 999px;
  border: 1px solid #c5c9ff;
  background: #f8f9ff;
  color: #4a52ff;
  font-size: 28rpx;
  outline: none;
  transition: color 0.25s ease, background 0.25s ease,
    box-shadow 0.25s ease, border-color 0.25s ease;
}

.toggle-button[aria-pressed='true'] {
  font-weight: 600;
}

.toggle-button:focus-visible {
  outline: 3rpx solid rgba(74, 82, 255, 0.4);
  outline-offset: 4rpx;
}

.toggle-button.active {
  background: linear-gradient(135deg, #4a52ff, #7a5cfd);
  color: #ffffff;
  border-color: transparent;
  box-shadow: 0 12rpx 32rpx rgba(74, 82, 255, 0.32);
}

.live-map {
  width: 100%;
  height: 520rpx;
  border-radius: 24rpx;
  overflow: hidden;
}

.distance-panel {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f5f7ff;
}

.distance-head {
  display: flex;
  justify-content: space-between;
  color: #7c849b;
  font-size: 24rpx;
}

.distance-label {
  font-weight: 600;
  color: #374151;
}

.distance-subtitle {
  font-size: 24rpx;
}

.distance-value {
  margin-top: 12rpx;
  font-size: 56rpx;
  color: #1f2a44;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.status-card {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.06);
}

.status-stack {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.status-row {
  padding: 20rpx;
  border-radius: 20rpx;
}

.status-row.self-chip {
  background: rgba(61, 90, 254, 0.08);
}

.status-row.partner-chip {
  background: rgba(255, 112, 67, 0.08);
}

.status-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2a44;
}

.status-coord {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #5f6c85;
}

.status-foot {
  margin-top: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-note {
  font-size: 24rpx;
  color: #7c849b;
}

.ghost {
  padding: 16rpx 36rpx;
  border-radius: 999px;
  border: 1px solid #3d5afe;
  color: #3d5afe;
  background: transparent;
  font-size: 28rpx;
  transition: border-color 0.2s ease, color 0.2s ease, opacity 0.2s ease;
}

.ghost:focus-visible {
  outline: 3rpx solid rgba(61, 90, 254, 0.45);
  outline-offset: 6rpx;
}

.ghost:disabled {
  opacity: 0.5;
  border-color: #d1d5db;
  color: #9ca3af;
}

.error-banner {
  margin: 24rpx;
  padding: 18rpx 24rpx;
  border-radius: 20rpx;
  background: rgba(255, 76, 76, 0.08);
  color: #d62424;
  font-size: 26rpx;
}
</style>