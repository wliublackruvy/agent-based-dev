<!-- // Implements 2.权限引导与存活看板 -->
<template>
  <view class="status-board" data-test="permission-board">
    <view class="board-head">
      <view>
        <text class="board-title">权限存活看板</text>
        <text class="board-subtitle" data-test="heartbeat-text">
          {{ heartbeatText }}
        </text>
      </view>
      <view
        class="heartbeat-pill"
        :class="{ stale: isHeartbeatStale }"
        data-test="heartbeat-badge"
      >
        {{ heartbeatBadgeLabel }}
      </view>
    </view>

    <view class="status-grid">
      <view
        v-for="status in orderedStatuses"
        :key="status.type"
        class="status-card"
        :class="{ offline: !status.enabled }"
        :data-test="`status-card-${status.type}`"
      >
        <view class="status-head">
          <text class="status-icon">{{ status.icon }}</text>
          <view class="status-meta">
            <text class="status-label">{{ status.label }}</text>
            <text class="status-state">
              {{ status.enabled ? '常开' : offlineCopy(status.type) }}
            </text>
          </view>
          <view
            v-if="status.pushWarning"
            class="push-badge"
            data-test="push-badge"
          >
            Push 警告
          </view>
        </view>

        <text class="status-trace" data-test="trace-text">
          {{
            status.traceAt
              ? `轨迹 ${formatRelative(status.traceAt)}`
              : '等待轨迹回传'
          }}
        </text>
        <text v-if="status.warningMessage" class="status-warning">
          {{ status.warningMessage }}
        </text>
      </view>
    </view>

    <view class="board-foot">
      <text class="foot-copy" data-test="last-fetch">{{ lastFetchLabel }}</text>
      <button
        class="ghost"
        :disabled="loading"
        @click="handleRefresh"
        data-test="refresh-btn"
      >
        {{ loading ? '同步中...' : '手动刷新' }}
      </button>
    </view>

    <text v-if="error" class="error" data-test="error-text">
      {{ error }}
    </text>
  </view>
</template>

<script setup lang="ts">
// Implements 2.权限引导与存活看板
import { computed, onUnmounted, ref, watch } from 'vue';
import {
  fetchPermissionStatus,
  type PermissionStatusRecord,
  type PermissionStatusType
} from '@/services/permissionStatus';
import { formatRelative, parseErrorMessage } from '@/utils/statusFormat';

const props = withDefaults(
  defineProps<{
    deviceId: string;
    pollIntervalMs?: number;
    staleHeartbeatSeconds?: number;
  }>(),
  {
    pollIntervalMs: 60000,
    staleHeartbeatSeconds: 180
  }
);

const MIN_POLL_INTERVAL_MS = 1000;

type PermissionCardView = PermissionStatusRecord & {
  label: string;
  icon: string;
};

const STATUS_META: Record<PermissionStatusType, { label: string; icon: string }> =
  {
    location: { label: '定位权限', icon: '📍' },
    notification: { label: '通知通道', icon: '🔔' },
    uninstall: { label: '卸载监控', icon: '🛡' }
  };

const statusOrder: PermissionStatusType[] = [
  'location',
  'notification',
  'uninstall'
];

const statuses = ref<PermissionStatusRecord[]>([]);
const heartbeatAt = ref('');
const lastFetchedAt = ref('');
const loading = ref(false);
const error = ref('');
let pollHandle: ReturnType<typeof setInterval> | null = null;

const orderedStatuses = computed<PermissionCardView[]>(() =>
  statusOrder.map((type) => {
    const base: PermissionStatusRecord = {
      type,
      enabled: false,
      traceAt: '',
      pushWarning: false,
      warningMessage: ''
    };
    const match = statuses.value.find((item) => item.type === type);
    const merged = { ...base, ...(match ?? {}) };
    return {
      ...merged,
      label: STATUS_META[type].label,
      icon: STATUS_META[type].icon
    };
  })
);

const heartbeatText = computed(() => {
  if (!props.deviceId) {
    return '等待绑定设备';
  }
  if (!heartbeatAt.value) {
    return '等待心跳';
  }
  return `最后心跳 ${formatRelative(heartbeatAt.value)}`;
});

const lastFetchLabel = computed(() => {
  if (!lastFetchedAt.value) {
    return '最近拉取：尚未同步';
  }
  return `最近拉取：${formatRelative(lastFetchedAt.value)}`;
});

const isHeartbeatStale = computed(() => {
  if (!props.deviceId) {
    return false;
  }
  const date = new Date(heartbeatAt.value);
  if (!heartbeatAt.value || Number.isNaN(date.getTime())) {
    return true;
  }
  const diffSeconds = (Date.now() - date.getTime()) / 1000;
  return diffSeconds > props.staleHeartbeatSeconds;
});

const heartbeatBadgeLabel = computed(() =>
  isHeartbeatStale.value ? '心跳失联' : '实时在线'
);

const stopPolling = () => {
  if (pollHandle) {
    clearInterval(pollHandle);
    pollHandle = null;
  }
};

const startPolling = () => {
  stopPolling();
  if (!props.deviceId || (props.pollIntervalMs ?? 0) <= 0) {
    return;
  }
  const interval = Math.max(props.pollIntervalMs, MIN_POLL_INTERVAL_MS);
  pollHandle = setInterval(() => {
    void fetchLatest();
  }, interval);
};

const fetchLatest = async () => {
  if (!props.deviceId) {
    error.value = '未绑定设备，等待后台心跳';
    return;
  }
  loading.value = true;
  try {
    const response = await fetchPermissionStatus(props.deviceId);
    statuses.value = response.statuses ?? [];
    heartbeatAt.value = response.heartbeatAt ?? '';
    error.value = '';
  } catch (err) {
    error.value = parseErrorMessage(err, '权限状态同步失败');
  } finally {
    lastFetchedAt.value = new Date().toISOString();
    loading.value = false;
  }
};

const handleRefresh = async () => {
  await fetchLatest();
};

const resetWhenNoDevice = () => {
  statuses.value = [];
  heartbeatAt.value = '';
  lastFetchedAt.value = '';
};

watch(
  () => props.deviceId,
  (deviceId) => {
    stopPolling();
    if (!deviceId) {
      resetWhenNoDevice();
      error.value = '未绑定设备，等待后台心跳';
      return;
    }
    void fetchLatest();
    startPolling();
  },
  { immediate: true }
);

watch(
  () => props.pollIntervalMs,
  () => {
    if (pollHandle) {
      startPolling();
    }
  }
);

onUnmounted(() => {
  stopPolling();
});

const offlineCopy = (type: PermissionStatusType) =>
  type === 'uninstall' ? '疑似卸载' : '关闭';
</script>

<style scoped>
.status-board {
  background: #111827;
  border-radius: 28rpx;
  padding: 28rpx;
  color: #fff;
}

.board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.board-title {
  font-size: 36rpx;
  font-weight: 600;
}

.board-subtitle {
  font-size: 24rpx;
  color: #9ca3af;
}

.heartbeat-pill {
  font-size: 24rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: rgba(74, 222, 128, 0.15);
  color: #4ade80;
}

.heartbeat-pill.stale {
  background: rgba(248, 113, 113, 0.15);
  color: #f87171;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220rpx, 1fr));
  gap: 16rpx;
}

.status-card {
  padding: 20rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  min-height: 180rpx;
}

.status-card.offline {
  border: 1rpx solid rgba(248, 113, 113, 0.4);
}

.status-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.status-icon {
  font-size: 40rpx;
}

.status-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.status-label {
  font-size: 28rpx;
  font-weight: 600;
}

.status-state {
  font-size: 22rpx;
  color: #cbd5f5;
}

.push-badge {
  margin-left: auto;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(250, 204, 21, 0.18);
  color: #facc15;
  font-size: 20rpx;
}

.status-trace {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #cbd5f5;
}

.status-warning {
  display: block;
  margin-top: 8rpx;
  color: #f87171;
  font-size: 24rpx;
}

.board-foot {
  margin-top: 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 24rpx;
  color: #9ca3af;
}

.ghost {
  border: 1rpx solid rgba(255, 255, 255, 0.45);
  background: transparent;
  padding: 8rpx 32rpx;
  border-radius: 24rpx;
  color: #fff;
}

.ghost:disabled {
  opacity: 0.6;
}

.error {
  display: block;
  margin-top: 20rpx;
  font-size: 24rpx;
  color: #f87171;
}
</style>