<template>
  <!-- // Implements 5.安全与紧急联系 -->
  <view class="emergency-action" data-test="emergency-action">
    <view v-if="showBanner" class="status-banner" data-test="status-banner">
      <text class="banner-title">{{ bannerTitle }}</text>
      <text class="banner-copy">{{ bannerCopy }}</text>
      <text
        v-if="showOfflineInstruction"
        class="banner-instruction"
        data-test="banner-instruction"
      >
        {{ offlineInstructions }}
      </text>
    </view>

    <button
      class="emergency-fab"
      type="button"
      :disabled="isButtonDisabled"
      @click="handleButtonPress"
      data-test="emergency-btn"
    >
      <text class="fab-eyebrow">SOS</text>
      <text class="fab-label">{{ buttonLabel }}</text>
      <text class="fab-hint">
        {{ showOfflineInstruction ? '离线排队' : '立即震动提醒' }}
      </text>
    </button>

    <view
      v-if="isConfirming"
      class="confirm-layer"
      data-test="confirm-modal"
    >
      <view class="confirm-card">
        <text class="confirm-title">确认发送紧急提醒？</text>
        <text class="confirm-copy">
          {{ confirmCopy }}
        </text>
        <view class="confirm-actions">
          <button
            class="ghost"
            type="button"
            @click="cancelConfirm"
            data-test="cancel-confirm"
          >
            再想想
          </button>
          <button
            class="danger"
            type="button"
            @click="confirmSend"
            data-test="confirm-send"
          >
            立即触发
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 5.安全与紧急联系
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { httpRequest, safeUni } from '@/services/httpClient';

const props = withDefaults(
  defineProps<{
    apiUrl?: string;
    debounceMs?: number;
    offlineInstructions?: string;
    confirmCopy?: string;
  }>(),
  {
    apiUrl: '/api/emergency/alerts',
    debounceMs: 5000,
    offlineInstructions: '离线时请立即拨打紧急联系人或当地报警电话。',
    confirmCopy: '触发后会立即震动提醒对方，即使设备静音。'
  }
);

const emit = defineEmits<{
  (e: 'alert-sent', payload: { queued: boolean }): void;
  (e: 'queued', payload: { count: number }): void;
  (e: 'error', payload: string): void;
}>();

const offlineInstructions = computed(() => props.offlineInstructions);
const confirmCopy = computed(() => props.confirmCopy);

const isConfirming = ref(false);
const isSending = ref(false);
const isDebounced = ref(false);
const isOnline = ref(true);
const queueSize = ref(0);
const errorMessage = ref('');
const DEFAULT_ERROR = '紧急提醒派发失败，请检查网络';
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
let flushingQueue = false;
type CleanupFn = () => void;
let teardownListeners: CleanupFn[] = [];

const showOfflineInstruction = computed(() => !isOnline.value);
const showBanner = computed(
  () =>
    showOfflineInstruction.value || queueSize.value > 0 || !!errorMessage.value
);
const bannerCopy = computed(() => {
  if (!isOnline.value) {
    return '离线状态：紧急提醒将排队等待网络恢复。';
  }
  if (queueSize.value > 0) {
    return `待发送队列：${queueSize.value} 条`;
  }
  return errorMessage.value;
});
const bannerTitle = computed(() => {
  if (!isOnline.value) {
    return '离线排队';
  }
  if (queueSize.value > 0) {
    return '排队中';
  }
  return '提醒状态';
});
const buttonLabel = computed(() => {
  if (isSending.value) {
    return '派发中...';
  }
  if (isDebounced.value) {
    return '已发送';
  }
  return '紧急提醒';
});
const isButtonDisabled = computed(
  () => isSending.value || isDebounced.value || !props.apiUrl
);

const resetDebounce = () => {
  if (debounceTimer) {
    clearTimeout(debounceTimer);
    debounceTimer = null;
  }
  isDebounced.value = false;
};

const startDebounce = () => {
  resetDebounce();
  isDebounced.value = true;
  debounceTimer = setTimeout(() => {
    isDebounced.value = false;
    debounceTimer = null;
  }, props.debounceMs);
};

const vibrateDevice = () => {
  const api = safeUni();
  if (api?.vibrateLong) {
    try {
      api.vibrateLong({});
      return;
    } catch {
      // ignore vibrate failures
    }
  }
  if (
    typeof navigator !== 'undefined' &&
    typeof navigator.vibrate === 'function'
  ) {
    navigator.vibrate(200);
  }
};

const dispatchAlert = async (queued: boolean) => {
  if (!props.apiUrl) {
    throw new Error('未配置紧急提醒接口');
  }
  isSending.value = true;
  errorMessage.value = '';
  try {
    await httpRequest({
      url: props.apiUrl,
      method: 'POST',
      payload: {
        triggeredAt: new Date().toISOString(),
        queued
      },
      fallbackError: DEFAULT_ERROR
    });
    vibrateDevice();
    emit('alert-sent', { queued });
    startDebounce();
  } catch (err) {
    const message =
      err instanceof Error && err.message ? err.message : DEFAULT_ERROR;
    errorMessage.value = message;
    emit('error', message);
    throw err;
  } finally {
    isSending.value = false;
  }
};

const flushQueue = async () => {
  if (flushingQueue) {
    return;
  }
  flushingQueue = true;
  try {
    while (queueSize.value > 0 && isOnline.value) {
      queueSize.value -= 1;
      try {
        await dispatchAlert(true);
      } catch {
        queueSize.value += 1;
        break;
      }
    }
  } finally {
    flushingQueue = false;
  }
};

const queueAlert = () => {
  queueSize.value += 1;
  emit('queued', { count: queueSize.value });
  errorMessage.value = '网络不可用，已加入队列';
};

const confirmSend = async () => {
  isConfirming.value = false;
  if (!isOnline.value) {
    queueAlert();
    return;
  }
  try {
    await dispatchAlert(false);
  } catch {
    // errors already surfaced via emit + banner
  }
};

const handleButtonPress = () => {
  if (isButtonDisabled.value) {
    return;
  }
  isConfirming.value = true;
};

const cancelConfirm = () => {
  isConfirming.value = false;
};

const updateNetworkState = (next: boolean) => {
  isOnline.value = next;
  if (next) {
    errorMessage.value = '';
    void flushQueue();
  }
};

const setupNetworkBridge = () => {
  const cleanup: CleanupFn[] = [];
  if (typeof window !== 'undefined' && window.addEventListener) {
    const handleOnline = () => updateNetworkState(true);
    const handleOffline = () => updateNetworkState(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    cleanup.push(() => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    });
    updateNetworkState(window.navigator?.onLine ?? true);
  }
  const api = safeUni();
  if (api?.onNetworkStatusChange) {
    const handler = (res: { isConnected: boolean }) => {
      updateNetworkState(res.isConnected);
    };
    api.onNetworkStatusChange(handler);
    cleanup.push(() => {
      if (typeof api.offNetworkStatusChange === 'function') {
        api.offNetworkStatusChange(handler);
      }
    });
  }
  return cleanup;
};

onMounted(() => {
  teardownListeners = setupNetworkBridge();
});

onUnmounted(() => {
  teardownListeners.forEach((fn) => fn());
  resetDebounce();
});
</script>

<style scoped>
.emergency-action {
  position: fixed;
  right: 32rpx;
  bottom: 48rpx;
  z-index: 90;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 20rpx;
  pointer-events: none;
}

.status-banner {
  width: 520rpx;
  max-width: calc(100% - 64rpx);
  background: rgba(248, 113, 113, 0.15);
  border: 1px solid rgba(248, 113, 113, 0.35);
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  color: #fee2e2;
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.banner-title {
  font-size: 28rpx;
  font-weight: 600;
}

.banner-copy {
  display: block;
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #fecdd3;
}

.banner-instruction {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #fef9c3;
}

.emergency-fab {
  width: 520rpx;
  max-width: calc(100% - 64rpx);
  border: none;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ef4444, #b91c1c);
  box-shadow: 0 30rpx 60rpx rgba(185, 28, 28, 0.4);
  padding: 28rpx 44rpx;
  color: #ffffff;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8rpx;
  pointer-events: auto;
}

.emergency-fab:disabled {
  opacity: 0.7;
  box-shadow: none;
}

.fab-eyebrow {
  font-size: 22rpx;
  letter-spacing: 6rpx;
  opacity: 0.85;
}

.fab-label {
  font-size: 40rpx;
  font-weight: 700;
}

.fab-hint {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.confirm-layer {
  position: fixed;
  inset: 0;
  z-index: 120;
  background: rgba(15, 23, 42, 0.7);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
}

.confirm-card {
  width: 620rpx;
  max-width: 90%;
  background: #111827;
  border-radius: 32rpx;
  border: 1px solid rgba(248, 113, 113, 0.5);
  color: #f9fafb;
  padding: 40rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.confirm-title {
  font-size: 34rpx;
  font-weight: 600;
}

.confirm-copy {
  font-size: 26rpx;
  color: #fcd34d;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 20rpx;
}

button.ghost {
  border: 1px solid #4b5563;
  border-radius: 999rpx;
  padding: 20rpx 36rpx;
  background: transparent;
  color: #f3f4f6;
  font-size: 26rpx;
}

button.danger {
  border: none;
  border-radius: 999rpx;
  padding: 20rpx 36rpx;
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #0f172a;
  font-size: 26rpx;
  font-weight: 600;
}
</style>