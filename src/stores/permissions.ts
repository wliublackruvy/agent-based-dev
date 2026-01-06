// Implements 2.权限引导与存活看板
import { defineStore } from 'pinia';
import {
  fetchPermissionGuideState,
  savePermissionGuideState,
  type PermissionGuideStateResponse,
  type PermissionGuideStepId,
  type PermissionGuideStepPayload
} from '@/services/permissions';

export interface PermissionScene extends PermissionGuideStepPayload {
  title: string;
  tagline: string;
  description: string;
  cue: string;
  actionLabel: string;
  deepLink?: string;
  cueEmoji: string;
}

const nowIso = () => new Date().toISOString();

const parseErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  return fallback;
};

const createScenes = (): PermissionScene[] => [
  {
    id: 'location',
    title: '定位权限',
    tagline: 'Always On 轨迹同步',
    description:
      '开启“始终允许”定位后，双方距离、行程和地理围栏变化才能每分钟上报；关闭超过 60 秒监视方立刻收到异常推送。',
    cue: '聚光灯实时追踪脚步，定位偏差等于剧情穿帮，需要你亲自开场。',
    actionLabel: '我已开启定位',
    deepLink: 'app-settings:root=LOCATION_SERVICES',
    cueEmoji: '📍',
    completed: false,
    completedAt: null
  },
  {
    id: 'notification',
    title: '通知权限',
    tagline: '异常无遗漏',
    description:
      '通知权限负责递送异常操作、报备与心跳丢失提醒，建议保持锁屏、横幅与声音都开启，避免后台静默。',
    cue: '把推送当作后台导演的耳返，关掉它所有突发状况都会被静音。',
    actionLabel: '我已开启通知',
    deepLink: 'app-settings:root=NOTIFICATIONS_ID',
    cueEmoji: '📣',
    completed: false,
    completedAt: null
  },
  {
    id: 'autostart',
    title: '后台自启动',
    tagline: '驻留后台不落幕',
    description:
      '将 App 加入自启动与电池优化白名单，系统才不会在锁屏或长时间静止时杀掉进程，1v1 保活才算真正在线。',
    cue: '这一步像维持舞台电源，省电策略一旦介入实时感知就会失焦。',
    actionLabel: '加入自启/白名单',
    deepLink:
      'package:com.android.settings/.Settings$HighPowerApplicationsActivity',
    cueEmoji: '🔋',
    completed: false,
    completedAt: null
  },
  {
    id: 'usage',
    title: '使用情况访问',
    tagline: '解锁 & 使用审计',
    description:
      'Android 需授权“使用情况访问”，获取解锁节奏与应用使用时长，结合定位判定逃逸或卸载风险。',
    cue: '这像后台时间轴，帮我们在审计面板重放你的解锁镜头。',
    actionLabel: '开启使用情况访问',
    deepLink: 'package:com.android.settings/.UsageAccessSettings',
    cueEmoji: '📊',
    completed: false,
    completedAt: null
  }
];

export const usePermissionStore = defineStore('permissionGuide', {
  state: () => ({
    steps: createScenes(),
    activeIndex: 0,
    deviceId: '',
    loading: false,
    saving: false,
    error: '',
    lastSyncedAt: ''
  }),
  getters: {
    completedCount: (state) =>
      state.steps.filter((step) => step.completed).length,
    allComplete: (state) => state.steps.every((step) => step.completed)
  },
  actions: {
    setActiveIndex(index: number) {
      if (!this.steps.length) {
        this.activeIndex = 0;
        return;
      }
      const clamped = Math.max(0, Math.min(index, this.steps.length - 1));
      this.activeIndex = clamped;
    },
    syncActiveIndex() {
      const nextIndex = this.steps.findIndex((step) => !step.completed);
      this.activeIndex = nextIndex === -1 ? this.steps.length - 1 : nextIndex;
    },
    applyRemoteState(payload: PermissionGuideStateResponse) {
      const remoteSteps = new Map(
        (payload.steps ?? []).map((step) => [step.id, step])
      );
      this.steps = this.steps.map((step) => {
        const incoming = remoteSteps.get(step.id);
        if (!incoming) {
          return { ...step };
        }
        return {
          ...step,
          completed: incoming.completed,
          completedAt: incoming.completed
            ? incoming.completedAt ?? step.completedAt ?? nowIso()
            : null
        };
      }) as PermissionScene[];
      this.error = '';
      this.lastSyncedAt = payload.updatedAt ?? nowIso();
      this.syncActiveIndex();
    },
    async bootstrap(deviceId: string) {
      this.deviceId = deviceId;
      this.loading = true;
      this.error = '';
      if (!deviceId) {
        this.loading = false;
        return;
      }
      try {
        const response = await fetchPermissionGuideState(deviceId);
        this.applyRemoteState(response);
      } catch (error) {
        this.error = parseErrorMessage(error, '权限状态同步失败');
      } finally {
        this.loading = false;
      }
    },
    async persistState() {
      if (!this.deviceId) {
        return;
      }
      this.saving = true;
      try {
        const response = await savePermissionGuideState({
          deviceId: this.deviceId,
          steps: this.steps.map<PermissionGuideStepPayload>((step) => ({
            id: step.id,
            completed: step.completed,
            completedAt: step.completedAt
          }))
        });
        this.applyRemoteState(response);
      } catch (error) {
        this.error = parseErrorMessage(error, '保存失败，请稍后重试');
        throw error;
      } finally {
        this.saving = false;
      }
    },
    async markStepComplete(stepId: PermissionGuideStepId) {
      const scene = this.steps.find((step) => step.id === stepId);
      if (!scene || scene.completed) {
        return;
      }
      const previous = {
        completed: scene.completed,
        completedAt: scene.completedAt
      };
      scene.completed = true;
      scene.completedAt = nowIso();
      this.syncActiveIndex();
      try {
        await this.persistState();
      } catch (error) {
        scene.completed = previous.completed;
        scene.completedAt = previous.completedAt;
        this.syncActiveIndex();
        throw error;
      }
    }
  }
});