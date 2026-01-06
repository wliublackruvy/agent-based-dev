<!-- // Implements 1.账号与关系管理 -->
<template>
  <view class="page">
    <AppHeader title="手机号登录" />
    <view class="hero">
      <text class="hero-title">持续授权，单设备守护</text>
      <text class="hero-desc">
        使用绑定手机号接收验证码，换新设备需重新验证并自动解绑旧设备。
      </text>
    </view>
    <view class="card">
      <view class="form-item">
        <text class="label">手机号</text>
        <input
          class="input"
          type="number"
          inputmode="numeric"
          maxlength="11"
          placeholder="输入 11 位手机号"
          v-model="phone"
          data-test="phone-input"
        />
      </view>
      <view class="form-item">
        <text class="label">验证码</text>
        <view class="code-row">
          <input
            class="input"
            type="number"
            inputmode="numeric"
            maxlength="6"
            placeholder="输入验证码"
            v-model="code"
            data-test="code-input"
          />
          <button
            class="ghost"
            :disabled="!canRequestCode"
            @click="handleRequestCode"
            data-test="request-code"
          >
            {{ requestButtonLabel }}
          </button>
        </view>
      </view>
      <view class="notice">
        <text class="notice-title">单设备策略</text>
        <text class="notice-desc">
          每次成功登录将解绑其他设备，保障双方授权安全，60 秒内验证码有效。
        </text>
      </view>
      <button
        class="cta"
        :disabled="loggingIn"
        @click="handleLogin"
        data-test="login-button"
      >
        {{ loggingIn ? '登录中...' : '登录 / 绑定' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 1.账号与关系管理
import { computed, onUnmounted, ref } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import { useAuthStore } from '@/stores/auth';

const COUNTDOWN_TOTAL = 60;
const phone = ref('');
const code = ref('');
const countdown = ref(0);
const loggingIn = ref(false);
const authStore = useAuthStore();
let timer: ReturnType<typeof setInterval> | null = null;

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

const resolveDeviceId = () => {
  const api = safeUni();
  if (api && typeof api.getSystemInfoSync === 'function') {
    try {
      const info = api.getSystemInfoSync();
      const platform = info?.platform || 'device';
      const model = info?.model || 'web';
      return `${platform}-${model}`;
    } catch {
      return 'h5-web';
    }
  }
  return 'h5-web';
};

const validatePhone = (value: string) => /^1[3-9]\d{9}$/.test(value);

const deviceId = resolveDeviceId();
const isCounting = computed(() => countdown.value > 0);
const isPhoneValid = computed(() => validatePhone(phone.value));
const canRequestCode = computed(() => isPhoneValid.value && !isCounting.value);
const requestButtonLabel = computed(() =>
  isCounting.value ? `${countdown.value}s 后重发` : '发送验证码'
);

const showToast = (title: string, icon: 'none' | 'success' = 'none') => {
  safeUni()?.showToast?.({
    title,
    icon
  });
};

const startCountdown = () => {
  if (timer) {
    clearInterval(timer);
  }
  countdown.value = COUNTDOWN_TOTAL;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && timer) {
      clearInterval(timer);
      timer = null;
      countdown.value = 0;
    }
  }, 1000);
};

const handleRequestCode = async () => {
  if (!isPhoneValid.value) {
    showToast('请输入正确的手机号');
    return;
  }
  if (isCounting.value) {
    return;
  }
  startCountdown();
  showToast('验证码已发送', 'success');
};

const handleLogin = async () => {
  if (!isPhoneValid.value) {
    showToast('请输入正确的手机号');
    return;
  }
  if (!code.value || code.value.length < 4) {
    showToast('请输入验证码');
    return;
  }
  loggingIn.value = true;
  try {
    const result = await authStore.loginWithCode({
      phone: phone.value,
      code: code.value,
      deviceId
    });
    showToast('登录成功', 'success');
    if (result.deviceNotice) {
      safeUni()?.showModal?.({
        title: '单设备提醒',
        content: result.deviceNotice,
        showCancel: false,
        confirmText: '知道了'
      });
    }
    const targetUrl = result.requiresBinding
      ? '/pages/index/index?stage=binding'
      : '/pages/index/index';
    safeUni()?.redirectTo?.({ url: targetUrl });
  } catch (error) {
    showToast('登录失败，请稍后再试');
  } finally {
    loggingIn.value = false;
  }
};

onUnmounted(() => {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
});
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: 80rpx;
  background: linear-gradient(180deg, #f5f7ff 0%, #ffffff 100%);
}

.hero {
  margin: 32rpx;
  padding: 32rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #3239ff, #6c6bff);
  color: #fff;
  box-shadow: 0 20rpx 40rpx rgba(50, 57, 255, 0.2);
}

.hero-title {
  font-size: 40rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.hero-desc {
  font-size: 26rpx;
  line-height: 1.6;
  opacity: 0.85;
}

.card {
  margin: 0 32rpx;
  padding: 36rpx 32rpx 48rpx;
  border-radius: 28rpx;
  background: #fff;
  box-shadow: 0 12rpx 48rpx rgba(0, 0, 0, 0.08);
}

.form-item + .form-item {
  margin-top: 32rpx;
}

.label {
  font-size: 26rpx;
  color: #6b6c7e;
  margin-bottom: 12rpx;
  display: block;
}

.input {
  width: 100%;
  padding: 26rpx 24rpx;
  border-radius: 20rpx;
  background: #f5f6fb;
  font-size: 30rpx;
  color: #1b1c33;
}

.code-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.ghost {
  min-width: 200rpx;
  border-radius: 20rpx;
  border: 2rpx solid #4a4bff;
  color: #4a4bff;
  background: transparent;
  padding: 24rpx 16rpx;
  font-size: 28rpx;
}

.ghost:disabled {
  border-color: #c7c7d2;
  color: #c7c7d2;
  background: #f0f1f6;
}

.notice {
  margin: 36rpx 0;
  padding: 24rpx 28rpx;
  border-radius: 20rpx;
  background: #f6f3ff;
  color: #4b3fb2;
}

.notice-title {
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.notice-desc {
  font-size: 24rpx;
  line-height: 1.5;
}

.cta {
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
  opacity: 0.7;
}
</style>