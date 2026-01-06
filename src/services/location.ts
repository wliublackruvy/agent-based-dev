// Implements 3.实时感知模块
import { httpRequest } from '@/services/httpClient';

export type MapTileMode = 'standard' | 'satellite';

export interface MapParticipantLocation {
  id: string;
  nickname: string;
  latitude: number;
  longitude: number;
  avatarUrl?: string;
  battery?: number;
  network?: string;
}

export interface LocationDistanceResponse {
  selfUser: MapParticipantLocation;
  partnerUser: MapParticipantLocation;
  distanceMeters: number;
  updatedAt: string;
}

const LOCATION_API_PREFIX = '/api/location';

export const fetchRealtimeDistance = (mode: MapTileMode) =>
  httpRequest<LocationDistanceResponse>({
    url: `${LOCATION_API_PREFIX}/distance`,
    method: 'GET',
    query: { mapMode: mode },
    fallbackError: '实时定位数据加载失败，请稍后再试'
  });