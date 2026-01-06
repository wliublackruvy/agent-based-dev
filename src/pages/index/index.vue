<!-- // Implements System -->
<template>
  <view class="page">
    <AppHeader title="Relationship Sense" />
    <view class="card">
      <text class="headline">实时关系看板</text>
      <text class="body">
        当前版本展示权限状态、实时定位与监控指标的摘要视图。
      </text>
      <navigator url="/pages/login/login" class="cta">前往登录</navigator>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements System
import AppHeader from '@/components/AppHeader.vue';
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #fafafa;
}

.card {
  margin: 32rpx;
  padding: 32rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.05);
}

.headline {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.body {
  display: block;
  font-size: 28rpx;
  color: #5c5c5c;
  margin-bottom: 24rpx;
}

.cta {
  display: inline-block;
  padding: 16rpx 32rpx;
  border-radius: 999px;
  background: #3d5afe;
  color: #fff;
  text-align: center;
}
</style>