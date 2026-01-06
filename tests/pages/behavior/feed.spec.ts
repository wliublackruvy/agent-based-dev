// Implements 3.实时感知模块
import { describe, expect, it } from 'vitest';
import {
  filterFeedItems,
  formatTimelineTime,
  normalizeFeedItems,
  type FeedViewItem
} from '@/pages/behavior/feed.vue';
import type {
  BehaviorEventPayload,
  BehaviorFeedCategory
} from '@/services/behaviorFeed';

describe('normalizeFeedItems', () => {
  it('sorts events and maps metadata plus push statuses', () => {
    const payload: BehaviorEventPayload[] = [
      {
        id: 'ride',
        eventType: 'ride_start',
        occurredAt: '2024-06-02T08:30:00',
        vehicle: '滴滴快车',
        note: '疑似急加速',
        pushChannel: { required: true, delivered: false }
      },
      {
        id: 'arrival',
        eventType: 'arrival',
        occurredAt: '2024-06-02T09:30:00',
        locationName: '公司',
        placeType: '办公',
        note: '打卡成功',
        severity: 'critical',
        pushChannel: { required: true, delivered: true }
      },
      {
        id: 'peace',
        eventType: 'peace_reminder',
        occurredAt: '2024-06-01T23:00:00',
        note: '报平安'
      }
    ];

    const normalized = normalizeFeedItems(payload);
    expect(normalized.map((item) => item.id)).toEqual([
      'arrival',
      'ride',
      'peace'
    ]);

    const arrival = normalized[0];
    expect(arrival.category).toBe('arrivals');
    expect(arrival.critical).toBe(true);
    expect(arrival.description).toContain('办公');
    expect(arrival.location).toBe('公司');

    const ride = normalized[1];
    expect(ride.category).toBe('rides');
    expect(ride.pushPending).toBe(true);
    expect(ride.description).toContain('滴滴快车');

    const reminder = normalized[2];
    expect(reminder.category).toBe('reminders');
    expect(reminder.description).toContain('报平安');
  });
});

describe('filterFeedItems', () => {
  const feedItems: FeedViewItem[] = [
    {
      id: 'arrival',
      title: '到达提醒',
      description: '办公',
      location: '公司',
      clockText: '08:30',
      category: 'arrivals',
      eventType: 'arrival',
      critical: false,
      pushPending: false
    },
    {
      id: 'ride',
      title: '上车提醒',
      description: '滴滴快车',
      location: '',
      clockText: '09:10',
      category: 'rides',
      eventType: 'ride_start',
      critical: false,
      pushPending: true
    },
    {
      id: 'peace',
      title: '平安提醒',
      description: '报平安',
      location: '',
      clockText: '21:30',
      category: 'reminders',
      eventType: 'peace_reminder',
      critical: false,
      pushPending: false
    }
  ];

  it('reduces the feed based on selected categories', () => {
    const filtered = filterFeedItems(
      feedItems,
      new Set<BehaviorFeedCategory>(['rides'])
    );
    expect(filtered).toHaveLength(1);
    expect(filtered[0].id).toBe('ride');
  });

  it('returns all records when no filter state is provided', () => {
    expect(filterFeedItems(feedItems, new Set())).toHaveLength(3);
  });
});

describe('formatTimelineTime', () => {
  it('formats timestamps and guards invalid payloads', () => {
    expect(formatTimelineTime('2024-06-02T08:05:00')).toBe('08:05');
    expect(formatTimelineTime('invalid')).toBe('');
  });
});

**Behavior Feed**
- Built the timeline UI in `src/pages/behavior/feed.vue:1` with filter chips, badge indicators for pending push counts, infinite scroll pagination, and highlighted critical/push states fed by the backend cursor API.
- Added a typed data service at `src/services/behaviorFeed.ts:1` that encapsulates the GET contract, pending push counts, and category metadata used across the page.
- Covered the normalization/filtering helpers via Vitest in `tests/pages/behavior/feed.spec.ts:1`, ensuring payload sorting, badge derivation, category filtering, and timestamp formatting behave deterministically.

Tests not run (filesystem is read-only so dependencies like `vitest` cannot be installed); once you have a writable workspace run `npm install` then `npm run test -- tests/pages/behavior/feed.spec.ts`.