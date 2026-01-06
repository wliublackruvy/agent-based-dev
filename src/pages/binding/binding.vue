<!-- // Implements 1.账号与关系管理 -->
<template>
  <view class="page">
    <AppHeader title="双人绑定" />
    <view class="card code-card">
      <view class="card-header">
        <view>
          <text class="title">我的绑定码</text>
          <text class="subtitle">有效期 {{ countdownText }}</text>
        </view>
        <button
          class="ghost"
          :disabled="loadingCode"
          @click="handleGenerateCode"
          data-test="refresh-button"
        >
          {{ loadingCode ? '生成中...' : '刷新' }}
        </button>
      </view>

      <view class="code-display">
        <text class="code" data-test="binding-code">{{ bindingCode }}</text>
        <text class="countdown" data-test="countdown">{{ countdownText }}</text>
      </view>

      <view class="qr-row">
        <image class="qr" :src="qrSource" mode="aspectFit" />
        <view class="share-copy">
          <text class="desc">
            扫描二维码或输入绑定码完成双向绑定，确保双方在同一网络环境。
          </text>
          <button class="ghost" @click="handleCopyCode" data-test="copy-code">
            复制绑定码
          </button>
        </view>
      </view>
    </view>

    <view class="card partner-card">
      <text class="title">输入对方绑定码</text>
      <input
        class="text-input"
        type="number"
        inputmode="numeric"
        maxlength="6"
        placeholder="输入对方 6 位绑定码"
        v-model="partnerCode"
        data-test="partner-input"
      />
      <button
        class="cta"
        :disabled="!canSubmitPartner"
        @click="handleSubmitPartner"
        data-test="partner-submit"
      >
        {{ submittingPartner ? '提交中...' : '确认绑定' }}
      </button>
      <text v-if="infoMessage" class="info" data-test="status-message">
        {{ infoMessage }}
      </text>
      <text v-if="errorMessage" class="error" data-test="error-message">
        {{ errorMessage }}
      </text>
    </view>

    <view class="card status-card">
      <view class="status-row">
        <view
          class="status-chip"
          :class="{ success: dualConfirm.selfConfirmed }"
          data-test="status-self"
        >
          <text class="status-title">我方确认</text>
          <text class="status-state">
            {{ dualConfirm.selfConfirmed ? '已确认' : '等待我方确认' }}
          </text>
        </view>
        <view
          class="status-chip"
          :class="{ success: dualConfirm.partnerConfirmed }"
          data-test="status-partner"
        >
          <text class="status-title">对方确认</text>
          <text class="status-state">
            {{ dualConfirm.partnerConfirmed ? '已确认' : '等待对方确认' }}
          </text>
        </view>
      </view>
      <text class="status-updated">最近更新 {{ lastUpdatedText }}</text>
      <button
        class="ghost"
        :disabled="refreshingStatus"
        @click="refreshDualStatus"
        data-test="refresh-status"
      >
        {{ refreshingStatus ? '刷新中...' : '同步确认状态' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 1.账号与关系管理
import { computed, onMounted, onUnmounted, ref } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import {
  createBindingCode,
  fetchDualConfirmStatus,
  submitPartnerCode
} from '@/services/binding';
import type {
  BindingCodePayload,
  DualConfirmState
} from '@/services/binding';

const bindingCode = ref('------');
const qrcodeUrl = ref('');
const countdown = ref(0);
const partnerCode = ref('');
const loadingCode = ref(false);
const submittingPartner = ref(false);
const refreshingStatus = ref(false);
const infoMessage = ref('');
const errorMessage = ref('');
const dualConfirm = ref<DualConfirmState>({
  selfConfirmed: false,
  partnerConfirmed: false,
  updatedAt: ''
});
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const FALLBACK_QR =
  'https://dummyimage.com/320x320/eff2ff/3a41ff&text=Binding';

const safeUni = () => {
  try {
    if (typeof uni !== 'undefined') {
      return uni;
    }
  } catch {
    return undefined;
  }
  return undefined;
};

const showToast = (title: string, icon: 'none' | 'success' = 'none') => {
  safeUni()?.showToast?.({ title, icon });
};

const qrSource = computed(() => qrcodeUrl.value || FALLBACK_QR);
const trimmedPartnerCode = computed(() =>
  partnerCode.value.replace(/\s+/g, '')
);
const canSubmitPartner = computed(
  () => trimmedPartnerCode.value.length === 6 && !submittingPartner.value
);
const countdownText = computed(() => {
  if (countdown.value <= 0) {
    return '已过期，请刷新';
  }
  const minutes = Math.floor(countdown.value / 60)
    .toString()
    .padStart(2, '0');
  const seconds = (countdown.value % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
});
const lastUpdatedText = computed(() => {
  const updated = dualConfirm.value.updatedAt;
  if (!updated) {
    return '尚未同步';
  }
  const date = new Date(updated);
  if (Number.isNaN(date.getTime())) {
    return '尚未同步';
  }
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
});

const parseError = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  return fallback;
};

const startCountdown = (duration: number) => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
  countdown.value = Math.max(0, duration);
  if (duration <= 0) {
    return;
  }
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = null;
      countdown.value = 0;
    }
  }, 1000);
};

const applySession = (payload: BindingCodePayload) => {
  bindingCode.value = payload.code;
  qrcodeUrl.value = payload.qrcodeUrl;
  infoMessage.value = '新的绑定码已生成';
  errorMessage.value = '';
  if (payload.dualConfirm) {
    dualConfirm.value = payload.dualConfirm;
  }
  startCountdown(payload.expiresIn);
};

const loadBindingCode = async () => {
  loadingCode.value = true;
  try {
    const session = await createBindingCode();
    applySession(session);
  } catch (error) {
    errorMessage.value = parseError(error, '生成绑定码失败，请稍后再试');
    infoMessage.value = '';
  } finally {
    loadingCode.value = false;
  }
};

const refreshDualStatus = async () => {
  refreshingStatus.value = true;
  try {
    dualConfirm.value = await fetchDualConfirmStatus();
  } catch (error) {
    errorMessage.value = parseError(error, '同步确认状态失败');
  } finally {
    refreshingStatus.value = false;
  }
};

const handleGenerateCode = () => {
  loadBindingCode();
  refreshDualStatus();
};

const handleCopyCode = async () => {
  const code = bindingCode.value;
  if (!code || code === '------') {
    showToast('暂无可复制的绑定码');
    return;
  }
  const api = safeUni();
  if (api?.setClipboardData) {
    api.setClipboardData({
      data: code,
      success: () => showToast('已复制绑定码', 'success'),
      fail: () => showToast('复制失败，请稍后再试')
    });
    return;
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(code);
      showToast('已复制绑定码', 'success');
      return;
    } catch {
      // ignore clipboard failures because we fall back to toast
    }
  }
  showToast('请手动复制绑定码');
};

const handleSubmitPartner = async () => {
  if (!canSubmitPartner.value) {
    showToast('请输入 6 位绑定码');
    return;
  }
  submittingPartner.value = true;
  errorMessage.value = '';
  try {
    const result = await submitPartnerCode(trimmedPartnerCode.value);
    infoMessage.value = result.message || '绑定请求已提交';
    if (result.dualConfirm) {
      dualConfirm.value = result.dualConfirm;
    }
    partnerCode.value = '';
    showToast('已通知对方确认', 'success');
  } catch (error) {
    infoMessage.value = '';
    errorMessage.value = parseError(error, '绑定失败，请稍后重试');
    showToast(errorMessage.value, 'none');
  } finally {
    submittingPartner.value = false;
  }
};

const initialize = async () => {
  await Promise.all([loadBindingCode(), refreshDualStatus()]);
};

onMounted(() => {
  initialize();
});

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx;
  padding-bottom: 80rpx;
  background: linear-gradient(180deg, #f6f7ff 0%, #ffffff 100%);
}

.card {
  margin-bottom: 24rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 12rpx 40rpx rgba(53, 60, 150, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1d1e33;
}

.subtitle {
  font-size: 26rpx;
  color: #7d8199;
}

.code-display {
  margin-top: 24rpx;
  text-align: center;
}

.code {
  display: inline-flex;
  justify-content: center;
  width: 100%;
  font-size: 64rpx;
  letter-spacing: 14rpx;
  font-weight: 700;
  color: #222553;
}

.countdown {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #9395b1;
}

.qr-row {
  margin-top: 32rpx;
  display: flex;
  gap: 24rpx;
  align-items: center;
}

.qr {
  width: 220rpx;
  height: 220rpx;
  border-radius: 16rpx;
  background: #f2f3fb;
}

.share-copy {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.desc {
  font-size: 26rpx;
  color: #535469;
  line-height: 1.6;
}

.text-input {
  width: 100%;
  margin-top: 24rpx;
  padding: 26rpx 24rpx;
  border-radius: 22rpx;
  background: #f6f6fb;
  font-size: 30rpx;
  color: #1d1e33;
}

.ghost {
  border-radius: 24rpx;
  border: 2rpx solid #4a4bff;
  color: #4a4bff;
  background: transparent;
  padding: 24rpx 16rpx;
  font-size: 28rpx;
}

.ghost:disabled {
  border-color: #b9bad8;
  color: #b9bad8;
  background: #f1f1fa;
}

.cta {
  margin-top: 24rpx;
  width: 100%;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #4a4bff, #7a7bff);
  color: #fff;
  text-align: center;
  padding: 26rpx 16rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.cta:disabled {
  opacity: 0.6;
}

.info {
  display: block;
  margin-top: 16rpx;
  color: #2a9d8f;
  font-size: 26rpx;
}

.error {
  display: block;
  margin-top: 16rpx;
  color: #d64545;
  font-size: 26rpx;
}

.status-row {
  display: flex;
  gap: 20rpx;
  margin-bottom: 16rpx;
}

.status-chip {
  flex: 1;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #f5f6fb;
  border: 2rpx solid transparent;
}

.status-chip.success {
  border-color: #32c48d;
  background: #f1fff8;
}

.status-title {
  font-size: 24rpx;
  color: #7a7b93;
}

.status-state {
  margin-top: 12rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #1e2040;
}

.status-chip.success .status-state {
  color: #1fa06c;
}

.status-updated {
  display: block;
  font-size: 24rpx;
  color: #8a8baa;
}
</style>