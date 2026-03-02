import { request } from '@umijs/max';
import type {
  Application,
  ApplicationStats,
  CreateApplicationParams,
  GetApplicationsResponse,
  GetSupportedLangsResponse,
  UpdateApplicationParams,
} from './typings';

/** 应用 API 前缀，需在 proxy 中配置 /api 转发到后端 */
const API_PREFIX = '/api/application';

/** 将后端 snake_case 转为前端 camelCase */
function toApplication(app: Record<string, any>): Application {
  const parseTime = (v: string | undefined) => {
    if (!v || v === '' || v === '0001-01-01T00:00:00Z') return undefined;
    const date = new Date(v);
    return isNaN(date.getTime()) ? undefined : date.toISOString();
  };

  // 统一映射后端状态到前端可展示的状态：
  // - importing 视为 deploying（导入过程本质上也是部署准备过程）
  // - 其他未知状态回退为 idle
  const rawStatus = (app.status as string | undefined) ?? 'idle';
  const normalizedStatusMap: Record<string, Application['status']> = {
    // 纯字符串状态（旧格式）
    idle: 'idle',
    running: 'running',
    stopped: 'stopped',
    error: 'error',
    deploying: 'deploying',
    cloning: 'cloning',
    // 历史 importing 状态视为 deploying
    importing: 'deploying',
    // 枚举名状态（后端 AppStatus），统一在前端做转换
    APP_STATUS_IDLE: 'idle',
    APP_STATUS_RUNNING: 'running',
    APP_STATUS_STOPPED: 'stopped',
    APP_STATUS_DEPLOYING: 'deploying',
    APP_STATUS_FAILED: 'error',
  };
  const status = normalizedStatusMap[rawStatus] ?? 'idle';

  return {
    id: app.id,
    name: app.name,
    description: app.description ?? '',
    gitUrl: app.git_url,
    branch: app.branch,
    status,
    lastDeployed: parseTime(app.last_deployed),
    // 后端新字段为 lang，兼容旧字段 runner_env
    lang: app.lang ?? app.runner_env,
     // 部署错误信息
    lastError: app.last_error ?? app.lastError,
    containerId: app.container_id,
    executeCmd: app.execute_cmd,
    envInstallCmd: app.env_install_cmd,
    createdAt: parseTime(app.created_at),
  };
}

export const applicationApi = {
  /** 获取应用统计 */
  getStats: () =>
    request<ApplicationStats>(`${API_PREFIX}/stats`, {
      method: 'GET',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => (res?.data !== undefined ? res.data : res)),

  /** 获取应用列表 */
  getList: () =>
    request<GetApplicationsResponse>(`${API_PREFIX}/apps`, {
      method: 'GET',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => {
      const raw = res?.data !== undefined ? res.data : res;
      const list = Array.isArray(raw?.applications) ? raw.applications : [];
      const applications = list.map((app: Record<string, any>) => toApplication(app));
      applications.sort((a, b) => {
        const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        if (ta === 0 && tb === 0) return b.id.localeCompare(a.id);
        return tb - ta;
      });
      return { applications };
    }),

  /** 获取支持的编程语言列表 */
  getSupportedLangs: () =>
    request<GetSupportedLangsResponse>(`${API_PREFIX}/supported-langs`, {
      method: 'GET',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => {
      const raw = res?.data !== undefined ? res.data : res;
      return { supportedLangs: Array.isArray(raw?.supportedLangs) ? raw.supportedLangs : [] };
    }),

  /** 创建应用 */
  create: (params: CreateApplicationParams) =>
    request(`${API_PREFIX}/apps`, {
      method: 'POST',
      data: params,
      skipErrorHandler: false,
      baseURL: '',
    }),

  /** 更新应用 */
  update: (id: string, params: UpdateApplicationParams) =>
    request(`${API_PREFIX}/apps/${id}`, {
      method: 'PUT',
      data: params,
      skipErrorHandler: false,
      baseURL: '',
    }),

  /** 删除应用 */
  remove: (id: string) =>
    request(`${API_PREFIX}/apps/${id}`, {
      method: 'DELETE',
      skipErrorHandler: false,
      baseURL: '',
    }),

  /** 运行/部署应用 */
  run: (id: string) =>
    request<void>(`${API_PREFIX}/apps/${id}/launch`, {
      method: 'POST',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }),

  /** 停止应用 */
  stop: (id: string) =>
    request<Application>(`${API_PREFIX}/apps/${id}/stop`, {
      method: 'POST',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => {
      const raw = res?.data !== undefined ? res.data : res;
      return raw ? toApplication(raw) : res;
    }),
};
