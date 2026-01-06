<!-- // Implements System -->
<template>
  <view class="header">
    <text class="title">{{ title }}</text>
  </view>
</template>

<script setup lang="ts">
// Implements System
defineProps<{
  title: string;
}>();
</script>

<style scoped>
.header {
  padding: 32rpx 24rpx 16rpx;
  background: linear-gradient(135deg, #f07167, #fdbb2d);
  color: #fff;
}

.title {
  font-size: 40rpx;
  font-weight: 600;
}
</style>