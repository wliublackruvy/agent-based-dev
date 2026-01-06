<!-- // Implements 3.实时感知模块 -->
<template>
  <view
    class="device-panel"
    :class="panelStateClass"
    data-test="device-status-panel"
  >
    <view class="panel-head">
      <view class="panel-copy">
        <text class="panel-title">设备状态</text>
        <text class="panel-subtitle">{{ subtitleText }}</text>
      </view>
      <button
        class="ghost"
        type="button"
        :disabled="isRefreshDisabled"
        @click="handleManualRefresh"
        data-test="refresh-btn"
      >
        {{ loading ? '同步中...' : '刷新' }}
      </button>
    </view>

    <view class="status-grid">
      <view class="status-card">
        <view class="card-head">
          <text class="card-title">电量</text>
          <text class="card-value" data-test="battery-value">
            {{ batteryLabel }}
          </text>
        </view>
        <view class="battery-shell">
          <view
            class="battery-fill"
            :class="batteryLevelClass"
            :style="{ width: batteryFillWidth }"
            data-test="battery-pill"
          />
        </view>
        <text
          v-if="batteryWarningCopy"
          class="warning-text"
          data-test="battery-warning"
        >
          {{ batteryWarningCopy }}
        </text>
      </view>

      <view
        class="status-card network-card"
        :class="networkStateClass"
        data-test="network-card"
      >
        <view class="card-head">
          <text class="card-title">网络</text>
          <text class="card-value" data-test="network-value">
            {{ networkLabel }}
          </text>
        </view>
        <text class="network-detail" data-test="network-detail">
          {{ networkDetailCopy }}
        </text>
      </view>
    </view>

    <text
      v-if="staleDataWarning"
      class="warning-text data-warning"
      data-test="stale-warning"
    >
      {{ staleDataWarning }}
    </text>

    <view class="heartbeat-row">
      <view class="heartbeat-meta">
        <text class="card-title">心跳</text>
        <text class="heartbeat-detail">{{ heartbeatDetailText }}</text>
      </view>
      <view
        class="heartbeat-badge"
        :class="heartbeatStateClass"
        data-test="heartbeat-badge"
      >
        {{ heartbeatBadgeLabel }}
      </view>
    </view>

    <text
      v-if="heartbeatWarning"
      class="warning-text heartbeat-warning"
      data-test="heartbeat-warning"
    >
      {{ heartbeatWarning }}
    </text>

    <text v-if="error" class="error-text" data-test="error-text">
      {{ error }}
    </text>
  </view>
</template>

<script setup lang="ts">
// Implements 3.实时感知模块
import { computed, onUnmounted, ref, watch } from 'vue';
import {
  fetchDeviceStatus,
  type DeviceStatusSnapshot
} from '@/services/deviceStatus';
import { formatRelative, parseErrorMessage } from '@/utils/statusFormat';

const props = withDefaults(
  defineProps<{
    deviceId: string;
    pollIntervalMs?: number;
    lowBatteryPercent?: number;
    criticalBatteryPercent?: number;
    staleHeartbeatSeconds?: number;
    staleDataSeconds?: number;
  }>(),
  {
    pollIntervalMs: 60000,
    lowBatteryPercent: 30,
    criticalBatteryPercent: 15,
    staleHeartbeatSeconds: 300,
    staleDataSeconds: 180
  }
);

const MIN_POLL_INTERVAL_MS = 1000;
const MIN_STALE_DATA_SECONDS = 60;

const deviceStatus = ref<DeviceStatusSnapshot | null>(null);
const lastFetchedAt = ref('');
const loading = ref(false);
const error = ref('');
let pollHandle: ReturnType<typeof setInterval> | null = null;

const stopPolling = () => {
  if (pollHandle) {
    clearInterval(pollHandle);
    pollHandle = null;
  }
};

const parseTimestamp = (value?: string | null) => {
  if (!value) {
    return null;
  }
  const timestamp = Date.parse(value);
  return Number.isNaN(timestamp) ? null : timestamp;
};

const loadStatus = async () => {
  if (!props.deviceId) {
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const snapshot = await fetchDeviceStatus(props.deviceId);
    deviceStatus.value = snapshot;
    lastFetchedAt.value = snapshot.reportedAt || new Date().toISOString();
  } catch (err) {
    error.value = parseErrorMessage(err, '设备状态同步失败');
  } finally {
    loading.value = false;
  }
};

const startPolling = () => {
  stopPolling();
  if (!props.deviceId) {
    return;
  }
  const interval = Math.max(MIN_POLL_INTERVAL_MS, props.pollIntervalMs);
  void loadStatus();
  pollHandle = setInterval(() => {
    void loadStatus();
  }, interval);
};

watch(
  () => props.deviceId,
  (deviceId) => {
    stopPolling();
    deviceStatus.value = null;
    lastFetchedAt.value = '';
    if (!deviceId) {
      error.value = '未绑定设备，等待后台状态';
      return;
    }
    error.value = '';
    startPolling();
  },
  { immediate: true }
);

watch(
  () => props.pollIntervalMs,
  () => {
    if (!props.deviceId) {
      return;
    }
    startPolling();
  }
);

onUnmounted(stopPolling);

const handleManualRefresh = () => {
  if (!props.deviceId || loading.value) {
    return;
  }
  void loadStatus();
};

const batteryPercent = computed<number | null>(() => {
  if (
    deviceStatus.value &&
    typeof deviceStatus.value.batteryPercent === 'number'
  ) {
    const clamped = Math.round(
      Math.min(100, Math.max(0, deviceStatus.value.batteryPercent))
    );
    return clamped;
  }
  return null;
});

const normalizedCriticalThreshold = computed(() =>
  Math.min(props.criticalBatteryPercent, props.lowBatteryPercent)
);

const normalizedLowThreshold = computed(() =>
  Math.max(normalizedCriticalThreshold.value, props.lowBatteryPercent)
);

const isBatteryCritical = computed(
  () =>
    batteryPercent.value !== null &&
    batteryPercent.value <= normalizedCriticalThreshold.value
);

const isBatteryLow = computed(
  () =>
    batteryPercent.value !== null &&
    batteryPercent.value <= normalizedLowThreshold.value &&
    !isBatteryCritical.value
);

const batteryLevelClass = computed(() => {
  if (batteryPercent.value === null) {
    return 'battery-idle';
  }
  if (isBatteryCritical.value) {
    return 'battery-critical';
  }
  if (isBatteryLow.value) {
    return 'battery-low';
  }
  return 'battery-healthy';
});

const batteryFillWidth = computed(() => `${batteryPercent.value ?? 0}%`);

const batteryLabel = computed(() => {
  if (!props.deviceId || batteryPercent.value === null) {
    return '--%';
  }
  return `${batteryPercent.value}%`;
});

const batteryWarningCopy = computed(() => {
  if (isBatteryCritical.value) {
    return `电量跌破 ${normalizedCriticalThreshold.value}% ，请立刻充电`;
  }
  if (isBatteryLow.value) {
    return `电量低于 ${normalizedLowThreshold.value}% ，注意补能`;
  }
  return '';
});

const networkLabel = computed(() => {
  if (!props.deviceId) {
    return '等待设备';
  }
  const networkType = deviceStatus.value?.networkType;
  if (!networkType) {
    return '网络未知';
  }
  if (networkType === 'wifi') {
    return 'WiFi';
  }
  if (networkType === 'offline') {
    return '离线';
  }
  if (networkType === 'unknown') {
    return '未知';
  }
  return networkType.toUpperCase();
});

const networkDetailCopy = computed(() => {
  if (!props.deviceId) {
    return '绑定后展示网络详情';
  }
  if (!deviceStatus.value) {
    return '等待网络回传';
  }
  switch (deviceStatus.value.networkType) {
    case 'wifi':
      return deviceStatus.value.wifiName
        ? `连接 ${deviceStatus.value.wifiName}`
        : 'WiFi 名称未知';
    case 'offline':
      return '无蜂窝信号，定位异常';
    case 'unknown':
      return '网络类型未上报';
    default:
      return `${deviceStatus.value.networkType.toUpperCase()} 数据通道`;
  }
});

const networkStateClass = computed(() => {
  if (!deviceStatus.value) {
    return 'network-idle';
  }
  switch (deviceStatus.value.networkType) {
    case 'wifi':
      return 'network-wifi';
    case 'offline':
      return 'network-offline';
    default:
      return 'network-cellular';
  }
});

const staleDataThresholdSeconds = computed(() =>
  Math.max(MIN_STALE_DATA_SECONDS, props.staleDataSeconds)
);

const lastReportLatencySeconds = computed(() => {
  const timestamp = parseTimestamp(deviceStatus.value?.reportedAt);
  if (timestamp === null) {
    return null;
  }
  const difference = Date.now() - timestamp;
  if (difference <= 0) {
    return 0;
  }
  return Math.round(difference / 1000);
});

const isDataStale = computed(() => {
  if (!props.deviceId || !deviceStatus.value) {
    return false;
  }
  const latency = lastReportLatencySeconds.value;
  if (latency === null) {
    return true;
  }
  return latency > staleDataThresholdSeconds.value;
});

const staleDataWarning = computed(() => {
  if (!props.deviceId || !isDataStale.value) {
    return '';
  }
  const latency = lastReportLatencySeconds.value;
  if (latency === null) {
    return '设备状态未更新，等待最新上报';
  }
  return `状态 ${latency} 秒未更新，超出 ${staleDataThresholdSeconds.value} 秒阈值`;
});

const panelStateClass = computed(() => {
  const classes: string[] = [];
  if (!props.deviceId) {
    classes.push('panel-idle');
  }
  if (isDataStale.value) {
    classes.push('panel-stale');
  }
  if (isBatteryCritical.value) {
    classes.push('panel-critical');
  } else if (isBatteryLow.value) {
    classes.push('panel-caution');
  }
  return classes;
});

const subtitleText = computed(() => {
  if (!props.deviceId) {
    return '未绑定设备';
  }
  if (!lastFetchedAt.value) {
    return '等待后台同步';
  }
  return `更新于 ${formatRelative(lastFetchedAt.value)}`;
});

const isRefreshDisabled = computed(
  () => loading.value || !props.deviceId
);

const heartbeatLatencySeconds = computed(() => {
  const timestamp = parseTimestamp(deviceStatus.value?.heartbeatAt);
  if (timestamp === null) {
    return null;
  }
  const difference = Date.now() - timestamp;
  if (difference <= 0) {
    return 0;
  }
  return Math.round(difference / 1000);
});

const heartbeatDetailText = computed(() => {
  if (!props.deviceId) {
    return '未绑定设备';
  }
  if (!deviceStatus.value?.heartbeatAt) {
    return '等待心跳回传';
  }
  return `最近 ${formatRelative(deviceStatus.value.heartbeatAt)}`;
});

const isHeartbeatStale = computed(() => {
  if (!props.deviceId || !deviceStatus.value) {
    return false;
  }
  const latency = heartbeatLatencySeconds.value;
  if (latency === null) {
    return true;
  }
  return latency > props.staleHeartbeatSeconds;
});

const heartbeatStateClass = computed(() => {
  if (!props.deviceId) {
    return 'badge-idle';
  }
  return isHeartbeatStale.value ? 'badge-stale' : 'badge-healthy';
});

const heartbeatBadgeLabel = computed(() => {
  if (!props.deviceId) {
    return '待绑定';
  }
  return isHeartbeatStale.value ? '心跳失联' : '实时在线';
});

const heartbeatWarning = computed(() => {
  if (!props.deviceId || !deviceStatus.value || !isHeartbeatStale.value) {
    return '';
  }
  const latency = heartbeatLatencySeconds.value;
  if (latency === null) {
    return `心跳超过 ${props.staleHeartbeatSeconds} 秒未回传`;
  }
  return `心跳 ${latency} 秒未回传，阈值 ${props.staleHeartbeatSeconds} 秒`;
});
</script>

<style scoped>
.device-panel {
  background-color: #0f172a;
  border: 1px solid #1f2937;
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  color: #f3f4f6;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  flex-wrap: wrap;
}

.panel-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.panel-title {
  font-size: 34rpx;
  font-weight: 600;
}

.panel-subtitle {
  font-size: 26rpx;
  color: #9ca3af;
}

button.ghost {
  border: 1px solid #374151;
  border-radius: 999rpx;
  background: transparent;
  color: #f3f4f6;
  padding: 10rpx 28rpx;
  font-size: 26rpx;
  transition: opacity 0.2s ease, border-color 0.2s ease;
}

button.ghost:disabled {
  opacity: 0.5;
  border-color: #4b5563;
}

.status-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
}

.status-card {
  flex: 1 1 320rpx;
  border-radius: 20rpx;
  background: #1f2937;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12rpx;
}

.card-title {
  font-size: 26rpx;
  color: #9ca3af;
}

.card-value {
  font-size: 44rpx;
  font-weight: 600;
}

.battery-shell {
  border: 1px solid #374151;
  border-radius: 26rpx;
  padding: 4rpx;
  height: 26rpx;
}

.battery-fill {
  height: 100%;
  border-radius: 26rpx;
  transition: width 0.3s ease, background 0.3s ease;
  background: linear-gradient(90deg, #34d399, #10b981);
}

.battery-critical {
  background: #dc2626;
}

.battery-low {
  background: #f97316;
}

.battery-idle {
  background: #4b5563;
}

.warning-text {
  font-size: 24rpx;
  color: #fbbf24;
}

.data-warning {
  color: #f97316;
}

.network-card {
  border: 1px solid transparent;
}

.network-detail {
  font-size: 26rpx;
  color: #d1d5db;
}

.network-wifi {
  border-color: #10b981;
}

.network-offline {
  border-color: #dc2626;
  background: rgba(220, 38, 38, 0.12);
}

.network-cellular {
  border-color: #2563eb;
}

.network-idle {
  border-color: #374151;
}

.heartbeat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  flex-wrap: wrap;
}

.heartbeat-meta {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.heartbeat-detail {
  font-size: 26rpx;
  color: #9ca3af;
}

.heartbeat-badge {
  border-radius: 999rpx;
  padding: 10rpx 30rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.badge-healthy {
  background: rgba(16, 185, 129, 0.16);
  color: #34d399;
}

.badge-stale {
  background: rgba(220, 38, 38, 0.16);
  color: #f87171;
}

.badge-idle {
  background: rgba(75, 85, 99, 0.32);
  color: #e5e7eb;
}

.heartbeat-warning {
  color: #f87171;
}

.error-text {
  color: #f87171;
  font-size: 26rpx;
}

.panel-critical {
  border-color: #dc2626;
  box-shadow: 0 0 0 1rpx rgba(220, 38, 38, 0.4);
}

.panel-caution {
  border-color: #f97316;
}

.panel-stale {
  box-shadow: inset 0 0 0 1rpx rgba(249, 115, 22, 0.6);
}

.panel-idle {
  border-style: dashed;
  border-color: #4b5563;
}

@media (min-width: 600px) {
  .status-card {
    flex: 1;
  }
}
</style>