// Implements 3.实时感知模块
import { httpRequest } from '@/services/httpClient';

export type BehaviorFeedCategory = 'arrivals' | 'rides' | 'reminders';

export type BehaviorEventType =
  | 'arrival'
  | 'departure'
  | 'ride_start'
  | 'ride_end'
  | 'peace_reminder';

export interface BehaviorPushChannel {
  required: boolean;
  delivered: boolean;
}

export interface BehaviorEventPayload {
  id: string;
  eventType: BehaviorEventType;
  occurredAt: string;
  locationName?: string;
  placeType?: string;
  vehicle?: string;
  note?: string;
  severity?: 'normal' | 'critical';
  pushChannel?: BehaviorPushChannel;
}

export interface BehaviorFeedPageResponse {
  cursor: string | null;
  hasMore: boolean;
  syncedAt?: string;
  pendingPushCounts?: Partial<Record<BehaviorFeedCategory, number>>;
  items: BehaviorEventPayload[];
}

export interface BehaviorFeedQuery {
  cursor?: string | null;
  limit?: number;
  filters?: BehaviorFeedCategory[];
}

const FEED_API_URL = '/api/behavior/feed';

export const fetchBehaviorFeedPage = (query: BehaviorFeedQuery = {}) => {
  const { cursor, limit, filters } = query;
  return httpRequest<BehaviorFeedPageResponse>({
    url: FEED_API_URL,
    method: 'GET',
    query: {
      cursor: cursor ?? undefined,
      limit: limit ?? 20,
      filters: filters?.length ? filters.join(',') : undefined
    },
    fallbackError: '行为动态加载失败，请稍后再试'
  });
};