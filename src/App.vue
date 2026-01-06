<!-- // Implements System -->
<!-- // Implements 5.安全与紧急联系 -->
<template>
  <view class="app-shell">
    <AppHeader title="Relationship Sense" />
    <view class="app-body">
      <slot />
    </view>
    <EmergencyActionButton />
  </view>
</template>

<script setup lang="ts">
// Implements System
// Implements 5.安全与紧急联系
import AppHeader from '@/components/AppHeader.vue';
import EmergencyActionButton from '@/components/EmergencyActionButton.vue';
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.app-body {
  flex: 1;
  padding: 24rpx;
}
</style>