/** 应用状态 */
export type ApplicationStatus =
  | 'idle'
  | 'running'
  | 'stopped'
  | 'error'
  | 'deploying'
  | 'cloning'
  | 'importing';

/** 应用 */
export interface Application {
  id: string;
  name: string;
  description?: string;
  gitUrl?: string;
  branch?: string;
  status: ApplicationStatus;
  lastDeployed?: string;
  runnerEnv?: string;
  containerId?: string;
  executeCmd?: string;
  envInstallCmd?: string;
  createdAt?: string;
}

/** 应用统计 */
export interface ApplicationStats {
  total: number;
  running: number;
  stopped: number;
  undeployed: number;
  failed: number;
  /** 导入中的应用数 */
  importing?: number;
}

/** 获取应用列表响应 */
export interface GetApplicationsResponse {
  applications: Application[];
}

/** 支持的编程语言列表响应 */
export interface GetSupportedLangsResponse {
  supportedLangs: string[];
}

/** 创建应用参数（后端 snake_case） */
export interface CreateApplicationParams {
  name: string;
  git_url?: string;
  branch?: string;
  description?: string;
  execute_cmd?: string;
  env_install_cmd?: string;
  runner_env?: string;
}

/** 更新应用参数 */
export interface UpdateApplicationParams {
  name?: string;
  description?: string;
  execute_cmd?: string;
  env_install_cmd?: string;
  runner_env?: string;
}
