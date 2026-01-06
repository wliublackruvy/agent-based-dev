<!-- // Implements 4.会员审计功能 -->
<template>
  <view class="audit-page">
    <AppHeader title="会员审计看板" />

    <view class="hero-card">
      <view>
        <text class="hero-label">{{ entitlementLabel }}</text>
        <text class="hero-title">关系状态审计</text>
      </view>
      <view class="hero-meta">
        <text class="hero-sync">{{ lastSyncedText }}</text>
        <button
          class="ghost"
          type="button"
          :disabled="loading"
          data-test="refresh-audit"
          @click="handleRefresh"
        >
          {{ loading ? '同步中...' : '刷新' }}
        </button>
      </view>
    </view>

    <view v-if="loading" class="state-block">正在加载会员审计...</view>

    <view v-else-if="error" class="state-block error">
      <text>{{ error }}</text>
      <button class="ghost" type="button" @click="handleRefresh">重试</button>
    </view>

    <view v-else>
      <view v-if="!isEntitled" class="upsell-card" data-test="audit-upsell">
        <text class="upsell-title">解锁会员审计数据</text>
        <text class="upsell-copy">
          非会员仅保留 60 分钟轨迹缓存。升级后可回放全天路线、停留点以及解锁/使用记录。
        </text>
        <button class="primary" type="button" data-test="upgrade-btn" @click="handleUpgrade">
          立即开通
        </button>
      </view>

      <view v-else class="audit-content">
        <view class="map-card">
          <view class="map-head">
            <text class="map-title">轨迹回放</text>
            <text class="map-status" data-test="replay-status">{{ replayStatusText }}</text>
          </view>

          <map
            v-if="shouldRenderMap"
            class="replay-map"
            :key="mapKey"
            :latitude="mapCenter.latitude"
            :longitude="mapCenter.longitude"
            :polyline="polyline"
            :markers="markers"
            :include-points="includePoints"
            :scale="12"
            :show-location="false"
            data-test="trajectory-map"
          />
          <view v-else class="map-placeholder">等待轨迹同步</view>

          <view class="map-foot">
            <text class="map-foot-text">{{ lastSyncedText }}</text>
            <button
              class="ghost"
              type="button"
              :disabled="pathPoints.length < 2"
              data-test="restart-replay"
              @click="handleReplayRestart"
            >
              重新播放
            </button>
          </view>
        </view>

        <view class="stat-grid">
          <view
            v-for="card in unlockSummaryCards"
            :key="card.id"
            class="stat-card"
            data-test="unlock-card"
          >
            <text class="stat-label">{{ card.label }}</text>
            <text class="stat-value">{{ card.value }}</text>
          </view>
        </view>

        <view class="timeline-card">
          <text class="timeline-title">解锁审计轨迹</text>
          <view
            v-for="row in unlockTimeline"
            :key="row.id"
            class="timeline-row"
            :class="{ alert: row.flagged }"
          >
            <view>
              <text class="timeline-row-title">{{ row.dayLabel }}</text>
              <text class="timeline-row-desc">{{ row.window }}</text>
            </view>
            <text class="timeline-row-count">{{ row.value }}</text>
          </view>
          <text v-if="!unlockTimeline.length" class="timeline-empty">暂无解锁审计数据</text>
        </view>

        <view class="usage-card">
          <text class="usage-title">App 使用分布</text>
          <view
            v-for="slice in usageSegments"
            :key="slice.id"
            class="usage-row"
            data-test="usage-item"
          >
            <view class="usage-info">
              <text class="usage-name">{{ slice.label }}</text>
              <text class="usage-meta">{{ slice.category }} · {{ slice.percentText }}</text>
            </view>
            <view class="usage-bar">
              <view class="usage-bar-fill" :style="{ width: slice.width }" />
              <text class="usage-minutes">{{ slice.minutesText }}</text>
            </view>
          </view>
          <text v-if="!usageSegments.length" class="usage-empty">
            等待 Android 使用情况授权...
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 4.会员审计功能
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import {
  fetchMembershipAuditOverview,
  type MembershipAuditResponse,
  type TrajectoryPoint,
  type UnlockAuditStat,
  type UsageSlice
} from '@/services/membershipAudit';
import { safeUni } from '@/services/httpClient';

interface SummaryCard {
  id: string;
  label: string;
  value: string;
}

interface UnlockTimelineRow {
  id: string;
  dayLabel: string;
  window: string;
  value: string;
  flagged: boolean;
}

interface UsageViewSlice {
  id: string;
  label: string;
  category: string;
  percentText: string;
  width: string;
  minutesText: string;
}

const DEFAULT_CENTER = { latitude: 31.2304, longitude: 121.4737 };
const FALLBACK_ERROR = '会员审计数据加载失败，请稍后再试';

const loading = ref(true);
const error = ref('');
const payload = ref<MembershipAuditResponse | null>(null);
const mapKey = ref(0);
const replayIndex = ref(0);
let replayTimer: ReturnType<typeof setInterval> | null = null;

const isEntitled = computed(() => Boolean(payload.value?.entitled));
const entitlementLabel = computed(() => payload.value?.entitlementName ?? '高级权益未开通');

const pathPoints = computed<TrajectoryPoint[]>(() => payload.value?.replay?.path ?? []);

const visiblePath = computed(() => {
  const points = pathPoints.value;
  if (!points.length) {
    return [];
  }
  const limit = Math.min(points.length, Math.max(1, replayIndex.value));
  return points.slice(0, limit).map((point) => ({
    latitude: point.latitude,
    longitude: point.longitude
  }));
});

const includePoints = computed(() => {
  if (visiblePath.value.length) {
    return visiblePath.value;
  }
  if (pathPoints.value.length) {
    return pathPoints.value.map((point) => ({
      latitude: point.latitude,
      longitude: point.longitude
    }));
  }
  return [DEFAULT_CENTER];
});

const shouldRenderMap = computed(() => isEntitled.value && pathPoints.value.length > 0);

const mapCenter = computed(() => {
  if (visiblePath.value.length) {
    return visiblePath.value[visiblePath.value.length - 1];
  }
  if (pathPoints.value.length) {
    const first = pathPoints.value[0];
    return { latitude: first.latitude, longitude: first.longitude };
  }
  return DEFAULT_CENTER;
});

const polyline = computed(() => {
  if (visiblePath.value.length < 2) {
    return [];
  }
  return [
    {
      points: visiblePath.value,
      color: '#f97316',
      width: 4,
      dottedLine: false
    }
  ];
});

const markers = computed(() => {
  const stops = payload.value?.replay?.stops ?? [];
  return stops.map((stop, index) => ({
    id: stop.id ?? `stop-${index}`,
    latitude: stop.latitude,
    longitude: stop.longitude,
    iconPath: 'https://dummyimage.com/32x32/0ea5e9/ffffff&text=停',
    width: 32,
    height: 32,
    callout: {
      content: `${stop.label ?? `停留 ${index + 1}`}\n${formatStopDuration(stop.durationMinutes)}`,
      color: '#0f172a',
      fontSize: 12,
      bgColor: '#e0f2fe',
      borderRadius: 6,
      padding: 6,
      display: 'ALWAYS'
    }
  }));
});

const replayStatusText = computed(() => {
  const total = pathPoints.value.length;
  if (!total) {
    return '等待轨迹同步';
  }
  if (replayIndex.value >= total) {
    return '回放完成';
  }
  return `回放中 ${Math.min(replayIndex.value, total)}/${total}`;
});

const lastSyncedText = computed(() => {
  const clock = formatClock(payload.value?.lastSyncedAt);
  return clock ? `同步于 ${clock}` : '等待后台同步';
});

const unlockStats = computed<UnlockAuditStat[]>(() => payload.value?.unlocks ?? []);

const unlockSummaryCards = computed<SummaryCard[]>(() => {
  const stat = unlockStats.value[0];
  return [
    {
      id: 'total',
      label: '今日解锁',
      value: formatUnlockCount(stat?.totalUnlocks)
    },
    {
      id: 'window',
      label: '解锁窗口',
      value: formatUnlockWindow(stat?.firstUnlockAt, stat?.lastUnlockAt)
    },
    {
      id: 'flag',
      label: '异常标签',
      value: stat?.riskFlag ? '发现夜间解锁' : '未检测到异常'
    }
  ];
});

const unlockTimeline = computed<UnlockTimelineRow[]>(() =>
  unlockStats.value.slice(0, 4).map((stat) => ({
    id: stat.day,
    dayLabel: stat.day,
    window: formatUnlockWindow(stat.firstUnlockAt, stat.lastUnlockAt),
    value: formatUnlockCount(stat.totalUnlocks),
    flagged: Boolean(stat.riskFlag)
  }))
);

const usageSegments = computed<UsageViewSlice[]>(() => {
  const slices: UsageSlice[] = payload.value?.usage ?? [];
  if (!slices.length) {
    return [];
  }
  const totalMinutes = slices.reduce(
    (sum, item) => sum + (Number.isFinite(item.minutes) ? item.minutes : 0),
    0
  );
  return slices.map((item) => {
    const ratio =
      totalMinutes > 0 ? item.minutes / totalMinutes : (item.percent ?? 0) / 100;
    const percent = Math.min(100, Math.max(0, Math.round(ratio * 100)));
    return {
      id: item.appId,
      label: item.name,
      category: item.category,
      percentText: `${percent}%`,
      width: `${Math.max(12, percent)}%`,
      minutesText: formatUsageMinutes(item.minutes)
    };
  });
});

const handleRefresh = async () => {
  await loadAuditOverview();
};

const handleReplayRestart = () => {
  startReplay();
};

const handleUpgrade = () => {
  const api = safeUni();
  api?.navigateTo?.({
    url: '/pages/index/index?upsell=membership'
  });
};

const loadAuditOverview = async () => {
  loading.value = true;
  error.value = '';
  try {
    const response = await fetchMembershipAuditOverview();
    payload.value = response;
    mapKey.value += 1;
    replayIndex.value = response.replay?.path?.length ? 1 : 0;
    if (response.entitled) {
      startReplay();
    } else {
      stopReplay();
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : FALLBACK_ERROR;
  } finally {
    loading.value = false;
  }
};

const startReplay = () => {
  stopReplay();
  const points = pathPoints.value;
  if (!isEntitled.value || points.length === 0) {
    replayIndex.value = points.length ? 1 : 0;
    return;
  }
  if (points.length === 1) {
    replayIndex.value = 1;
    return;
  }
  replayIndex.value = 1;
  replayTimer = setInterval(() => {
    replayIndex.value = Math.min(replayIndex.value + 1, points.length);
    if (replayIndex.value >= points.length) {
      stopReplay();
    }
  }, 1500);
};

const stopReplay = () => {
  if (replayTimer) {
    clearInterval(replayTimer);
    replayTimer = null;
  }
};

onMounted(() => {
  loadAuditOverview();
});

onBeforeUnmount(() => {
  stopReplay();
});

function formatClock(timestamp?: string | null) {
  if (!timestamp) {
    return '';
  }
  const match = timestamp.match(/T(\d{2}):(\d{2})/);
  if (match) {
    return `${match[1]}:${match[2]}`;
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function formatUnlockCount(count?: number) {
  if (!Number.isFinite(count) || (count ?? 0) < 0) {
    return '--';
  }
  return `${Math.round(count as number)} 次`;
}

export function formatUnlockWindow(first?: string, last?: string) {
  const start = formatClock(first);
  const end = formatClock(last);
  if (start && end) {
    return `${start} - ${end}`;
  }
  return start || end || '--';
}

export function formatUsageMinutes(minutes?: number) {
  if (!Number.isFinite(minutes) || (minutes ?? 0) <= 0) {
    return '--';
  }
  const value = Number(minutes);
  if (value >= 60) {
    const hours = value / 60;
    const text = Number.isInteger(hours) ? hours.toFixed(0) : hours.toFixed(1);
    return `${text.replace(/\.0$/, '')} 小时`;
  }
  return `${Math.round(value)} 分钟`;
}

export function formatStopDuration(minutes?: number) {
  if (!Number.isFinite(minutes) || (minutes ?? 0) <= 0) {
    return '停留 <1 分钟';
  }
  const value = Math.round(minutes);
  if (value < 60) {
    return `停留 ${value} 分钟`;
  }
  const hours = Math.floor(value / 60);
  const remain = value % 60;
  if (!remain) {
    return `停留 ${hours} 小时`;
  }
  return `停留 ${hours} 小时 ${remain} 分钟`;
}
</script>

<style scoped>
.audit-page {
  min-height: 100vh;
  padding-bottom: 64rpx;
  background: #f4f6fb;
}

.hero-card {
  margin: 24rpx;
  padding: 32rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #0ea5e9, #6366f1);
  color: #ffffff;
  display: flex;
  justify-content: space-between;
  gap: 24rpx;
}

.hero-label {
  font-size: 24rpx;
  color: #bae6fd;
}

.hero-title {
  display: block;
  margin-top: 8rpx;
  font-size: 40rpx;
  font-weight: 600;
}

.hero-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
}

.hero-sync {
  font-size: 24rpx;
  color: #e0f2fe;
}

.state-block {
  margin: 32rpx;
  padding: 48rpx;
  text-align: center;
  border-radius: 24rpx;
  background: #ffffff;
  color: #0f172a;
}

.state-block.error {
  border: 2rpx solid #ef4444;
  color: #b91c1c;
}

.upsell-card {
  margin: 24rpx;
  padding: 40rpx;
  border-radius: 32rpx;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  box-shadow: 0 20rpx 40rpx rgba(14, 165, 233, 0.15);
}

.upsell-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #0f172a;
}

.upsell-copy {
  color: #475569;
  line-height: 1.5;
}

.audit-content {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  padding: 0 24rpx 32rpx;
}

.map-card,
.timeline-card,
.usage-card {
  border-radius: 32rpx;
  background: #ffffff;
  padding: 32rpx;
  box-shadow: 0 12rpx 36rpx rgba(2, 132, 199, 0.08);
}

.map-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.map-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
}

.map-status {
  font-size: 24rpx;
  color: #0284c7;
}

.replay-map {
  width: 100%;
  height: 360rpx;
  border-radius: 24rpx;
  margin: 32rpx 0 16rpx;
}

.map-placeholder {
  height: 360rpx;
  border-radius: 24rpx;
  margin: 32rpx 0 16rpx;
  background: repeating-linear-gradient(
    -45deg,
    #e2e8f0,
    #e2e8f0 12rpx,
    #f8fafc 12rpx,
    #f8fafc 24rpx
  );
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.map-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.map-foot-text {
  color: #475569;
  font-size: 24rpx;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24rpx;
}

.stat-card {
  background: #ffffff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, 0.08);
}

.stat-label {
  display: block;
  font-size: 26rpx;
  color: #64748b;
  margin-bottom: 12rpx;
}

.stat-value {
  font-size: 36rpx;
  font-weight: 600;
  color: #0f172a;
}

.timeline-title,
.usage-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #0f172a;
}

.timeline-row {
  margin-top: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx;
  border-radius: 24rpx;
  background: #f8fafc;
  color: #0f172a;
}

.timeline-row.alert {
  border: 2rpx solid #f97316;
  background: #fff7ed;
}

.timeline-row-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
}

.timeline-row-desc {
  display: block;
  font-size: 24rpx;
  color: #64748b;
}

.timeline-row-count {
  font-size: 32rpx;
  font-weight: 600;
}

.timeline-empty,
.usage-empty {
  display: block;
  margin-top: 24rpx;
  text-align: center;
  color: #94a3b8;
}

.usage-row {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.usage-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.usage-name {
  font-size: 28rpx;
  font-weight: 600;
}

.usage-meta {
  font-size: 24rpx;
  color: #64748b;
}

.usage-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.usage-bar-fill {
  height: 16rpx;
  border-radius: 16rpx;
  background: linear-gradient(90deg, #22d3ee, #0ea5e9);
  min-width: 12%;
}

.usage-minutes {
  font-size: 24rpx;
  color: #0f172a;
}

button.primary,
button.ghost {
  border: none;
  border-radius: 999rpx;
  padding: 12rpx 32rpx;
  font-size: 28rpx;
}

button.primary {
  background: #0ea5e9;
  color: #ffffff;
}

button.ghost {
  background: rgba(255, 255, 255, 0.2);
  color: #1d4ed8;
}
</style>