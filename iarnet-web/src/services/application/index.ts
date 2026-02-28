import { request } from '@umijs/max';
import type {
  Application,
  ApplicationStats,
  CreateApplicationParams,
  GetApplicationsResponse,
  GetRunnerEnvironmentsResponse,
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
  const status = app.status as Application['status'];
  return {
    id: app.id,
    name: app.name,
    description: app.description ?? '',
    gitUrl: app.git_url,
    branch: app.branch,
    status:
      status === 'idle' ||
      status === 'running' ||
      status === 'stopped' ||
      status === 'error' ||
      status === 'deploying' ||
      status === 'cloning' ||
      status === 'importing'
        ? status
        : 'idle',
    lastDeployed: parseTime(app.last_deployed),
    runnerEnv: app.runner_env,
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

  /** 获取运行环境列表 */
  getRunnerEnvironments: () =>
    request<GetRunnerEnvironmentsResponse>(`${API_PREFIX}/runner-environments`, {
      method: 'GET',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => {
      const raw = res?.data !== undefined ? res.data : res;
      return { environments: Array.isArray(raw?.environments) ? raw.environments : [] };
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
    request<Application>(`${API_PREFIX}/apps/${id}/run`, {
      method: 'POST',
      skipErrorHandler: false,
      getResponse: false,
      baseURL: '',
    }).then((res: any) => {
      const raw = res?.data !== undefined ? res.data : res;
      return raw ? toApplication(raw) : res;
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
