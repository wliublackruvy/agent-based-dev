<!-- // Implements 3.实时感知模块 -->
<template>
  <view class="feed-page">
    <AppHeader title="行为动态" />
    <view class="filter-panel">
      <view class="filter-head">
        <text class="filter-title">筛选事件</text>
        <text class="panel-subtitle" data-test="feed-status">
          {{ statusText }}
        </text>
      </view>
      <view class="filter-row">
        <button
          v-for="option in filterOptions"
          :key="option.key"
          class="filter-chip"
          :class="{ active: normalizedFilterSet.has(option.key) }"
          type="button"
          :aria-pressed="normalizedFilterSet.has(option.key)"
          :data-test="`filter-${option.key}`"
          @click="handleToggleFilter(option.key)"
        >
          {{ option.label }}
          <text
            v-if="badgeCounts[option.key]"
            class="filter-badge"
            data-test="filter-badge"
          >
            {{ badgeCounts[option.key] }}
          </text>
        </button>
      </view>
    </view>

    <view v-if="error" class="error-banner" data-test="feed-error">
      <text class="error-text">{{ error }}</text>
      <button class="ghost" type="button" @click="handleManualRefresh">
        重试
      </button>
    </view>

    <scroll-view
      class="feed-scroll"
      scroll-y
      lower-threshold="200"
      @scrolltolower="handleLoadMore"
    >
      <view v-if="loadingInitial && !timelineItems.length" class="loading-block">
        正在同步行为轨迹...
      </view>

      <view v-else-if="showEmptyState" class="empty-block" data-test="empty-state">
        <text class="empty-title">暂无符合的行为记录</text>
        <text class="empty-hint">尝试调整筛选条件或等待后台推送</text>
      </view>

      <view
        v-for="item in visibleItems"
        :key="item.id"
        class="feed-card"
        :class="{ 'feed-card--critical': item.critical }"
        data-test="feed-item"
        :data-category="item.category"
      >
        <view class="feed-card-head">
          <view class="feed-card-title">
            <text class="feed-title">{{ item.title }}</text>
            <text class="feed-chip">{{ filterLabelMap[item.category] }}</text>
          </view>
          <text class="feed-clock">{{ item.clockText }}</text>
        </view>
        <text class="feed-description">{{ item.description }}</text>
        <text v-if="item.location" class="feed-location">
          {{ item.location }}
        </text>
        <view class="feed-badges">
          <text v-if="item.critical" class="feed-badge feed-badge-warning">
            警报
          </text>
          <text
            v-if="item.pushPending"
            class="feed-badge feed-badge-push"
            data-test="push-indicator"
          >
            推送待补
          </text>
        </view>
      </view>

      <view v-if="loadingMore" class="loading-more" data-test="loading-more">
        加载更多中...
      </view>
      <view
        v-else-if="!hasMore && visibleItems.length"
        class="no-more"
        data-test="no-more"
      >
        已展示全部动态
      </view>
    </scroll-view>

    <view class="footer">
      <button
        class="primary"
        type="button"
        data-test="refresh-btn"
        @click="handleManualRefresh"
        :disabled="loadingInitial"
      >
        {{ loadingInitial ? '同步中...' : '刷新动态' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
// Implements 3.实时感知模块
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import AppHeader from '@/components/AppHeader.vue';
import {
  fetchBehaviorFeedPage,
  type BehaviorEventPayload,
  type BehaviorFeedCategory
} from '@/services/behaviorFeed';
import { safeUni } from '@/services/httpClient';

interface FilterOption {
  key: BehaviorFeedCategory;
  label: string;
}

export interface FeedViewItem {
  id: string;
  title: string;
  description: string;
  location: string;
  clockText: string;
  category: BehaviorFeedCategory;
  eventType: BehaviorEventPayload['eventType'];
  critical: boolean;
  pushPending: boolean;
}

const FILTER_OPTIONS: FilterOption[] = [
  { key: 'arrivals', label: '地点报备' },
  { key: 'rides', label: '出行动态' },
  { key: 'reminders', label: '平安提醒' }
];

const FILTER_LABEL_MAP: Record<BehaviorFeedCategory, string> = {
  arrivals: '地点',
  rides: '出行',
  reminders: '平安'
};

const CATEGORY_META: Record<
  BehaviorEventPayload['eventType'],
  { category: BehaviorFeedCategory; title: string }
> = {
  arrival: { category: 'arrivals', title: '到达提醒' },
  departure: { category: 'arrivals', title: '离开提醒' },
  ride_start: { category: 'rides', title: '上车提醒' },
  ride_end: { category: 'rides', title: '下车提醒' },
  peace_reminder: { category: 'reminders', title: '平安提醒' }
};

const AUTO_REFRESH_MS = 60000;
const INITIAL_BADGE_COUNTS: Record<BehaviorFeedCategory, number> = {
  arrivals: 0,
  rides: 0,
  reminders: 0
};

const resolveMeta = (
  type: BehaviorEventPayload['eventType']
): { category: BehaviorFeedCategory; title: string } =>
  CATEGORY_META[type] ?? CATEGORY_META.arrival;

const buildDescription = (event: BehaviorEventPayload) => {
  const segments: string[] = [];
  if (event.placeType) {
    segments.push(event.placeType);
  }
  if (event.vehicle && event.eventType.startsWith('ride')) {
    segments.push(event.vehicle);
  }
  if (event.note) {
    segments.push(event.note);
  }
  return segments.length ? segments.join(' ｜ ') : '后台自动识别';
};

const parseTimestamp = (timestamp: string) => {
  const value = new Date(timestamp).getTime();
  return Number.isNaN(value) ? 0 : value;
};

export const formatTimelineTime = (timestamp: string) => {
  if (!timestamp) {
    return '';
  }
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  const hours = date.getHours().toString().padStart(2, '0');
  the minutes = date.getMinutes().toString().padStart(2, '0');
  return `${hours}:${minutes}`;
};

export const normalizeFeedItems = (
  events: BehaviorEventPayload[]
): FeedViewItem[] =>
  events
    .slice()
    .sort((a, b) => parseTimestamp(b.occurredAt) - parseTimestamp(a.occurredAt))
    .map((event) => {
      const meta = resolveMeta(event.eventType);
      return {
        id: event.id,
        title: meta.title,
        description: buildDescription(event),
        location: event.locationName ?? '',
        clockText: formatTimelineTime(event.occurredAt),
        category: meta.category,
        eventType: event.eventType,
        critical: event.severity === 'critical',
        pushPending: Boolean(
          event.pushChannel?.required && !event.pushChannel?.delivered
        )
      };
    });

export const filterFeedItems = (
  items: FeedViewItem[],
  filters: Set<BehaviorFeedCategory>
) => {
  if (!filters?.size) {
    return items;
  }
  return items.filter((item) => filters.has(item.category));
};

const filterOptions = FILTER_OPTIONS;
const filterLabelMap = FILTER_LABEL_MAP;
const timelineItems = ref<FeedViewItem[]>([]);
const loadingInitial = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);
const pageCursor = ref<string | null>(null);
const error = ref('');
const refreshTimestamp = ref('');
const activeFilters = ref<BehaviorFeedCategory[]>(
  filterOptions.map((option) => option.key)
);
const badgeCounts = ref<Record<BehaviorFeedCategory, number>>({
  ...INITIAL_BADGE_COUNTS
});
const uniApi = safeUni();
let refreshTimer: ReturnType<typeof setInterval> | null = null;

const normalizedFilterSet = computed(
  () => new Set<BehaviorFeedCategory>(activeFilters.value)
);
const visibleItems = computed(() =>
  filterFeedItems(timelineItems.value, normalizedFilterSet.value)
);
const showEmptyState = computed(
  () => !loadingInitial.value && !visibleItems.value.length && !error.value
);
const statusText = computed(() => {
  if (loadingInitial.value) {
    return '同步中...';
  }
  const formatted = formatTimelineTime(refreshTimestamp.value);
  return formatted ? `同步于 ${formatted}` : '等待后台推送';
});

const applyBadgeCounts = (
  counts?: Partial<Record<BehaviorFeedCategory, number>>
) => {
  const next = { ...INITIAL_BADGE_COUNTS };
  (Object.keys(next) as BehaviorFeedCategory[]).forEach((key) => {
    if (typeof counts?.[key] === 'number') {
      next[key] = Math.max(0, Number(counts[key]));
    }
  });
  badgeCounts.value = next;
};

const loadFeed = async (options: { reset?: boolean } = {}) => {
  const { reset = false } = options;
  if (reset && loadingInitial.value) {
    return;
  }
  if (!reset && (loadingMore.value || !hasMore.value)) {
    return;
  }
  const target = reset ? loadingInitial : loadingMore;
  target.value = true;
  if (reset) {
    error.value = '';
    timelineItems.value = [];
    pageCursor.value = null;
    hasMore.value = true;
  }
  try {
    const response = await fetchBehaviorFeedPage({
      cursor: reset ? null : pageCursor.value,
      filters: [...activeFilters.value],
      limit: 20
    });
    const normalized = normalizeFeedItems(response.items ?? []);
    timelineItems.value = reset
      ? normalized
      : [...timelineItems.value, ...normalized];
    pageCursor.value = response.cursor ?? null;
    hasMore.value = Boolean(response.hasMore);
    refreshTimestamp.value = response.syncedAt ?? new Date().toISOString();
    applyBadgeCounts(response.pendingPushCounts);
    error.value = '';
  } catch (err) {
    const message =
      err instanceof Error ? err.message : '行为动态加载失败，请稍后再试';
    if (!timelineItems.value.length) {
      error.value = message;
    } else {
      uniApi?.showToast?.({
        title: message,
        icon: 'none'
      });
    }
  } finally {
    target.value = false;
  }
};

const restartAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  if (typeof setInterval === 'function') {
    refreshTimer = setInterval(() => {
      loadFeed({ reset: true });
    }, AUTO_REFRESH_MS);
  }
};

const handleLoadMore = () => {
  if (loadingInitial.value || loadingMore.value || !hasMore.value) {
    return;
  }
  loadFeed();
};

const handleManualRefresh = () => {
  loadFeed({ reset: true });
  restartAutoRefresh();
};

const handleToggleFilter = (filter: BehaviorFeedCategory) => {
  const next = new Set(activeFilters.value);
  if (next.has(filter)) {
    if (next.size === 1) {
      uniApi?.showToast?.({
        title: '至少保留一个筛选',
        icon: 'none'
      });
      return;
    }
    next.delete(filter);
  } else {
    next.add(filter);
  }
  activeFilters.value = Array.from(next);
  loadFeed({ reset: true });
};

onMounted(() => {
  loadFeed({ reset: true });
  restartAutoRefresh();
});

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
});
</script>

<style scoped>
.feed-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f7f8fb;
}

.filter-panel {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 16rpx 32rpx rgba(26, 31, 68, 0.08);
}

.filter-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.filter-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111a34;
}

.panel-subtitle {
  font-size: 24rpx;
  color: #6b7280;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.filter-chip {
  flex: 1;
  min-width: 180rpx;
  padding: 16rpx 24rpx;
  border-radius: 999px;
  border: 1px solid #d6d9e4;
  background: #f5f7fb;
  font-size: 26rpx;
  color: #4a5568;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 10rpx;
}

.filter-chip.active {
  background: linear-gradient(135deg, #4f7cf8, #5d9dff);
  color: #ffffff;
  border-color: transparent;
  box-shadow: 0 12rpx 28rpx rgba(79, 124, 248, 0.35);
}

.filter-chip:focus-visible {
  outline: 3rpx solid rgba(79, 124, 248, 0.45);
  outline-offset: 6rpx;
}

.filter-badge {
  min-width: 32rpx;
  padding: 2rpx 12rpx;
  border-radius: 999px;
  background: #ff8d72;
  color: #ffffff;
  font-size: 22rpx;
  line-height: 32rpx;
}

.error-banner {
  margin: 0 24rpx 16rpx;
  padding: 20rpx;
  border-radius: 20rpx;
  background: rgba(255, 99, 71, 0.12);
  color: #b91c1c;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.error-text {
  flex: 1;
  font-size: 26rpx;
}

.feed-scroll {
  flex: 1;
  margin: 0 24rpx;
  padding-bottom: 32rpx;
  height: calc(100vh - 420rpx);
}

.loading-block,
.empty-block,
.loading-more,
.no-more {
  margin: 24rpx 0;
  padding: 40rpx 24rpx;
  border-radius: 20rpx;
  background: #ffffff;
  text-align: center;
  color: #6b7280;
  font-size: 26rpx;
}

.empty-title {
  display: block;
  margin-bottom: 12rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111a34;
}

.empty-hint {
  font-size: 24rpx;
  color: #6b7280;
}

.feed-card {
  margin-top: 24rpx;
  padding: 28rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 16rpx 32rpx rgba(15, 23, 42, 0.05);
  border: 2rpx solid transparent;
}

.feed-card:first-child {
  margin-top: 0;
}

.feed-card--critical {
  border-color: rgba(255, 99, 71, 0.6);
  box-shadow: 0 18rpx 36rpx rgba(255, 99, 71, 0.2);
}

.feed-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.feed-card-title {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.feed-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #111a34;
}

.feed-chip {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 999px;
  background: #f1f5ff;
  color: #3d5afe;
}

.feed-clock {
  font-size: 26rpx;
  color: #6b7280;
  font-variant-numeric: tabular-nums;
}

.feed-description {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #374151;
  line-height: 1.5;
  display: block;
}

.feed-location {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #6b7280;
  display: block;
}

.feed-badges {
  margin-top: 16rpx;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.feed-badge {
  padding: 6rpx 18rpx;
  border-radius: 999px;
  font-size: 22rpx;
}

.feed-badge-warning {
  background: rgba(255, 99, 71, 0.15);
  color: #d14343;
}

.feed-badge-push {
  background: rgba(79, 124, 248, 0.15);
  color: #3353d3;
}

.loading-more,
.no-more {
  background: transparent;
  box-shadow: none;
  padding: 24rpx;
  color: #7b8190;
  font-size: 24rpx;
}

.footer {
  padding: 24rpx;
  background: linear-gradient(180deg, rgba(247, 248, 251, 0), #f7f8fb);
}

.primary {
  width: 100%;
  padding: 24rpx;
  border-radius: 999px;
  border: none;
  background: linear-gradient(135deg, #4f7cf8, #5d9dff);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
}

.primary:disabled {
  opacity: 0.6;
}

.ghost {
  padding: 16rpx 28rpx;
  border-radius: 999px;
  border: 1px solid #b91c1c;
  background: transparent;
  color: #b91c1c;
  font-size: 26rpx;
}
</style>