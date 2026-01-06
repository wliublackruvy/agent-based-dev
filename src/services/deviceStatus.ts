// Implements 3.实时感知模块
import { httpRequest } from '@/services/httpClient';

export type DeviceNetworkType = 'wifi' | '5g' | '4g' | 'offline' | 'unknown';

export interface DeviceStatusSnapshot {
  deviceId: string;
  batteryPercent: number;
  networkType: DeviceNetworkType;
  wifiName?: string;
  heartbeatAt: string;
  reportedAt: string;
}

const DEVICE_STATUS_API = '/api/device/status';

export const fetchDeviceStatus = (deviceId: string) =>
  httpRequest<DeviceStatusSnapshot>({
    url: DEVICE_STATUS_API,
    method: 'GET',
    query: { deviceId },
    fallbackError: '设备状态同步失败'
  });