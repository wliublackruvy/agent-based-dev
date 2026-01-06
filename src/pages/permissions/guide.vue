<!-- // Implements 2.权限引导与存活看板 -->
<template>
  <view class="permission-page">
    <AppHeader title="权限剧场引导" />
    <view class="stage-card">
      <view class="stage-head">
        <view>
          <text class="stage-label">剧场状态</text>
          <text class="stage-title" data-test="scene-title">
            {{
              isComplete
                ? '四幕已谢幕'
                : `${activeScene?.title ?? ''} · ${activeScene?.tagline}`
            }}
          </text>
        </view>
        <view class="progress-chip" :class="{ success: isComplete }">
          {{ stageStatusText }}
        </view>
      </view>

      <text class="stage-narration">{{ stageNarration }}</text>

      <view class="stepper" data-test="stepper">
        <view
          v-for="(step, index) in store.steps"
          :key="step.id"
          class="step-node"
        >
          <view
            class="bullet"
            :class="{
              done: step.completed,
              active: index === store.activeIndex && !isComplete
            }"
            data-test="step-pill"
          >
            <text v-if="step.completed" class="icon">✔</text>
            <text v-else>{{ index + 1 }}</text>
          </view>
          <text class="step-name">{{ step.title }}</text>
        </view>
      </view>

      <view class="progress-track">
        <view
          class="progress-value"
          :style="{ width: `${progressWidth}%` }"
        />
      </view>

      <view v-if="store.loading" class="loading">
        <text>同步权限状态中...</text>
      </view>

      <transition name="fade-slide" mode="out-in">
        <view
          v-if="!store.loading"
          class="scene"
          :key="activeScene?.id"
          data-test="active-scene"
        >
          <view class="hero">
            <view class="hero-pill">{{ activeScene?.cueEmoji }}</view>
            <view class="hero-copy">
              <text class="hero-title">{{ activeScene?.title }}</text>
              <text class="hero-tagline">
                {{ activeScene?.tagline }}
              </text>
            </view>
          </view>
          <text class="scene-description" data-test="scene-copy">
            {{ activeScene?.description }}
          </text>
          <text class="scene-cue">{{ activeScene?.cue }}</text>
          <view class="actions">
            <button
              class="ghost"
              @click="handleOpenSettings"
              data-test="open-settings"
            >
              前往系统设置
            </button>
            <button
              class="cta"
              :disabled="store.saving || activeScene?.completed"
              @click="handleCompleteStep"
              data-test="complete-step"
            >
              {{
                store.saving
                  ? '同步中...'
                  : activeScene?.completed
                    ? '已开启'
                    : activeScene?.actionLabel
              }}
            </button>
          </view>
        </view>
      </transition>

      <view class="sync-row">
        <text>{{ syncText }}</text>
        <text class="sync-status" :class="{ success: isComplete }">
          {{ isComplete ? '后台已捕捉所有权限' : '等待完成下一幕' }}
        </text>
      </view>
    </view>

    <view class="timeline-card">
      <text class="timeline-title">权限轨迹</text>
      <view
        class="timeline-item"
        v-for="(step, index) in store.steps"
        :key="`timeline-${step.id}`"
        :class="{ active: index === store.activeIndex }"
        @click="handleJump(index)"
      >
        <view class="timeline-status" :class="{ done: step.completed }" />
        <view class="timeline-copy">
          <text class="timeline-name">{{ step.title }}</text>
          <text class="timeline-desc">
            {{ step.completedAt ? formatTime(step.completedAt) : '待开启' }}
          </text>
        </view>
        <text class="timeline-action">
          {{ step.completed ? '已完成' : '开启' }}
        </text>
      </view>
    </view>

    <view v-if="store.error" class="error">
      <text>{{ store.error }}</text>
    </view>

    <view
      v-if="isComplete"
      class="complete-card"
      data-test="completion-banner"
    >
      <text class="complete-title">剧场谢幕</text>
      <text class="complete-desc">
        所有权限保持常开后，监视方将在 60 秒内感知关闭等异常。
      </text>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 2.权限引导与存活看板
import { computed, onMounted, watch } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import { usePermissionStore } from '@/stores/permissions';
import { useAuthStore } from '@/stores/auth';
import { safeUni } from '@/services/httpClient';

declare const plus:
  | {
      runtime?: {
        openURL?: (url: string) => void;
      };
    }
  | undefined;

const store = usePermissionStore();
const authStore = useAuthStore();

const cacheDeviceId = (deviceId: string, apiInstance = safeUni()) => {
  if (!deviceId) {
    return;
  }
  apiInstance?.setStorageSync?.('permission-device-id', deviceId);
};

function formatTime(timestamp?: string | null) {
  if (!timestamp) {
    return '刚刚';
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '刚刚';
  }
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
}

const activeScene = computed(
  () =>
    store.steps[store.activeIndex] ??
    store.steps[store.steps.length - 1] ??
    null
);
const isComplete = computed(() => store.allComplete);
const stageStatusText = computed(
  () => `${store.completedCount}/${store.steps.length} 完成`
);
const stageNarration = computed(() => {
  if (isComplete.value) {
    return '后台感知稳定，所有权限已进入保活巡检。';
  }
  const title = activeScene.value?.title ?? '当前权限';
  return `请开启 ${title}，确保 60 秒内同步到后台。`;
});
const progressWidth = computed(() => {
  if (!store.steps.length) {
    return 0;
  }
  const ratio = store.completedCount / store.steps.length;
  return Math.min(100, Math.max(0, Math.round(ratio * 100)));
});
const syncText = computed(() => {
  if (!store.lastSyncedAt) {
    return '尚未同步后台';
  }
  return `上次与后台同步：${formatTime(store.lastSyncedAt)}`;
});

const showToast = (title: string, icon: 'none' | 'success' = 'none') => {
  safeUni()?.showToast?.({ title, icon });
};

const handleOpenSettings = () => {
  const scene = activeScene.value;
  if (!scene) {
    return;
  }
  const api = safeUni();
  if (api?.openAppAuthorizeSetting) {
    api.openAppAuthorizeSetting();
    return;
  }
  if (typeof plus !== 'undefined' && scene.deepLink && plus?.runtime?.openURL) {
    plus.runtime.openURL(scene.deepLink);
    return;
  }
  if (typeof window !== 'undefined' && scene.deepLink) {
    window.open(scene.deepLink, '_blank', 'noopener');
    return;
  }
  showToast('请前往系统设置中手动开启');
};

const handleCompleteStep = async () => {
  const scene = activeScene.value;
  if (!scene || scene.completed) {
    return;
  }
  try {
    await store.markStepComplete(scene.id);
    showToast('状态已同步后台', 'success');
  } catch {
    showToast('同步失败，请稍后再试');
  }
};

const handleJump = (index: number) => {
  store.setActiveIndex(index);
};

const resolveDeviceId = () => {
  if (authStore.activeDeviceId) {
    cacheDeviceId(authStore.activeDeviceId);
    return authStore.activeDeviceId;
  }
  const api = safeUni();
  if (api?.getStorageSync) {
    const cached = api.getStorageSync('permission-device-id');
    if (cached) {
      return cached;
    }
  }
  let fallback = 'h5-device';
  if (api?.getSystemInfoSync) {
    try {
      const info = api.getSystemInfoSync();
      fallback = `${info?.platform ?? 'h5'}-${info?.model ?? 'device'}`;
    } catch {
      fallback = 'h5-device';
    }
  }
  cacheDeviceId(fallback, api);
  return fallback;
};

watch(
  () => authStore.activeDeviceId,
  (deviceId) => {
    if (!deviceId || deviceId === store.deviceId) {
      return;
    }
    cacheDeviceId(deviceId);
    store.bootstrap(deviceId);
  }
);

watch(
  () => store.error,
  (value) => {
    if (value) {
      showToast(value);
    }
  }
);

watch(
  () => store.allComplete,
  (done) => {
    if (done) {
      showToast('全部权限已开启', 'success');
    }
  }
);

onMounted(() => {
  const deviceId = resolveDeviceId();
  store.bootstrap(deviceId);
});

defineExpose({ formatTime });
</script>

<style scoped>
.permission-page {
  min-height: 100vh;
  padding-bottom: 120rpx;
  background: linear-gradient(180deg, #f4f6ff 0%, #ffffff 100%);
}
.stage-card,
.timeline-card,
.complete-card {
  margin: 24rpx;
  border-radius: 32rpx;
  box-shadow: 0 20rpx 40rpx rgba(20, 38, 90, 0.08);
  background: #ffffff;
}
.stage-card {
  padding: 32rpx;
}
.stage-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.stage-label {
  font-size: 24rpx;
  color: #8b8fa9;
}
.stage-title {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: #161a33;
}
.stage-narration {
  display: block;
  font-size: 24rpx;
  color: #8b8fa9;
  margin-bottom: 12rpx;
}
.progress-chip {
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #3a41ff;
  background: rgba(58, 65, 255, 0.12);
}
.progress-chip.success {
  color: #18b05d;
  background: rgba(24, 176, 93, 0.12);
}
.stepper {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16rpx;
}
.step-node {
  flex: 1;
  text-align: center;
}
.step-name {
  font-size: 22rpx;
  color: #6f76a7;
}
.bullet {
  width: 64rpx;
  height: 64rpx;
  margin: 0 auto 8rpx;
  border-radius: 999rpx;
  border: 2rpx solid #cdd3f9;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #6f76a7;
  background: #ffffff;
  transition: all 0.2s;
}
.bullet.active {
  border-color: #3a41ff;
  color: #3a41ff;
  box-shadow: 0 12rpx 24rpx rgba(58, 65, 255, 0.18);
}
.bullet.done {
  background: #3a41ff;
  color: #ffffff;
  border-color: #3a41ff;
}
.progress-track {
  height: 8rpx;
  border-radius: 999rpx;
  background: #edf0ff;
  overflow: hidden;
  margin-bottom: 16rpx;
}
.progress-value {
  height: 100%;
  background: linear-gradient(90deg, #3a41ff, #7c4dff);
  transition: width 0.3s ease;
}
.loading {
  padding: 48rpx 0;
  text-align: center;
  color: #6f76a7;
  font-size: 26rpx;
}
.scene {
  margin-top: 12rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  border: 1rpx solid #eceffd;
  background: linear-gradient(135deg, #f7f8ff, #ffffff);
}
.hero {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
}
.hero-pill {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  margin-right: 24rpx;
  box-shadow: 0 16rpx 32rpx rgba(58, 65, 255, 0.15);
  animation: floating 3s ease-in-out infinite;
}
.hero-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #161a33;
}
.hero-tagline {
  font-size: 26rpx;
  color: #6f76a7;
  margin-top: 4rpx;
}
.scene-description {
  font-size: 28rpx;
  line-height: 1.6;
  color: #2f3144;
  display: block;
  margin-bottom: 10rpx;
}
.scene-cue {
  font-size: 24rpx;
  color: #8b8fa9;
  display: block;
  margin-bottom: 20rpx;
}
.actions {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}
button {
  border: none;
}
.ghost,
.cta {
  flex: 1;
  min-width: 46%;
  border-radius: 16rpx;
  padding: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.ghost {
  border: 1rpx solid #3a41ff;
  color: #3a41ff;
  background: transparent;
}
.cta {
  background: linear-gradient(90deg, #3a41ff, #7c4dff);
  color: #ffffff;
}
.cta:disabled {
  opacity: 0.5;
}
.sync-row {
  display: flex;
  justify-content: space-between;
  margin-top: 24rpx;
  font-size: 24rpx;
  color: #6f76a7;
}
.sync-status {
  color: #f39b1b;
}
.sync-status.success {
  color: #18b05d;
}
.timeline-card {
  padding: 24rpx;
}
.timeline-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
  color: #1c2033;
}
.timeline-item {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f0f2ff;
}
.timeline-item:last-child {
  border-bottom: none;
}
.timeline-item.active {
  background: rgba(58, 65, 255, 0.08);
  border-radius: 20rpx;
  padding: 24rpx 16rpx;
  margin: 8rpx -16rpx;
}
.timeline-status {
  width: 16rpx;
  height: 16rpx;
  border-radius: 999rpx;
  border: 2rpx solid #cdd3f9;
  margin-right: 16rpx;
}
.timeline-status.done {
  background: #18b05d;
  border-color: #18b05d;
}
.timeline-copy {
  flex: 1;
}
.timeline-name {
  font-size: 26rpx;
  color: #1c2033;
}
.timeline-desc {
  font-size: 22rpx;
  color: #8b8fa9;
}
.timeline-action {
  font-size: 22rpx;
  color: #3a41ff;
}
.error {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: rgba(255, 86, 115, 0.08);
  color: #d9304f;
  font-size: 26rpx;
}
.complete-card {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #3a41ff, #18b05d);
  color: #ffffff;
}
.complete-title {
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}
.complete-desc {
  font-size: 26rpx;
  opacity: 0.9;
}
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.35s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(16rpx);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-16rpx);
}
@keyframes floating {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-8rpx);
  }
}
</style>