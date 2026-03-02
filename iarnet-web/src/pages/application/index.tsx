import {
  AppstoreOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { PageContainer, ProTable } from '@ant-design/pro-components';
import { useRequest } from '@umijs/max';
import { Button, Card, message, Popconfirm, Space, Statistic, Tag } from 'antd';
import React, { useRef, useState } from 'react';
import { applicationApi } from '@/services/application';
import type { Application, ApplicationStatus } from '@/services/application/typings';
import ApplicationForm from './components/ApplicationForm';

/** 状态文案与颜色 */
const STATUS_MAP: Record<
  ApplicationStatus,
  { text: string; color: string }
> = {
  running: { text: '运行中', color: 'success' },
  idle: { text: '未部署', color: 'default' },
  stopped: { text: '已停止', color: 'default' },
  error: { text: '错误', color: 'error' },
  deploying: { text: '部署中', color: 'processing' },
  cloning: { text: '克隆中', color: 'warning' },
};

function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** 将 Git URL 转为可打开的 HTTPS 链接 */
function gitUrlToHttps(url?: string): string | null {
  if (!url) return null;
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url.replace(/\.git$/, '');
  }
  const m = url.match(/^git@([^:]+):([\w\-.]+\/[\w\-.]+)(?:\.git)?$/);
  if (!m) return null;
  const [, host, repo] = m;
  if (host === 'github.com') return `https://github.com/${repo}`;
  if (host === 'gitlab.com') return `https://gitlab.com/${repo}`;
  if (host === 'bitbucket.org') return `https://bitbucket.org/${repo}`;
  return `https://${host}/${repo}`;
}

const ApplicationManagement: React.FC = () => {
  const actionRef = useRef<ActionType | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const [formOpen, setFormOpen] = useState(false);
  const [editingApp, setEditingApp] = useState<Application | null>(null);
  const [formInitialValues, setFormInitialValues] = useState<
    Record<string, any> | undefined
  >(undefined);

  const { data: stats, loading: statsLoading, run: refreshStats } = useRequest(
    () => applicationApi.getStats(),
    {
      onError: () => {},
    },
  );

  const reload = () => {
    refreshStats();
    actionRef.current?.reload?.();
  };

  const handleCreate = () => {
    setEditingApp(null);
    setFormInitialValues({ branch: 'main' });
    setFormOpen(true);
  };

  const handleEdit = (app: Application) => {
    setEditingApp(app);
    setFormInitialValues({
      name: app.name,
      gitUrl: app.gitUrl,
      branch: app.branch || 'main',
      description: app.description,
      runnerEnv: app.runnerEnv,
    });
    setFormOpen(true);
  };

  const handleRun = async (app: Application) => {
    try {
      await applicationApi.run(app.id);
      messageApi.success(`应用「${app.name}」已开始部署`);
      reload();
    } catch {
      messageApi.error('启动失败，请重试');
    }
  };

  const handleStop = async (app: Application) => {
    try {
      await applicationApi.stop(app.id);
      messageApi.success(`应用「${app.name}」已停止`);
      reload();
    } catch {
      messageApi.error('停止失败，请重试');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await applicationApi.remove(id);
      messageApi.success('已删除');
      reload();
    } catch {
      messageApi.error('删除失败，请重试');
    }
  };

  const columns: ProColumns<Application>[] = [
    {
      title: '应用名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 160,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <span className="font-medium">{record.name}</span>
          {record.description ? (
            <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 12 }}>
              {record.description.length > 60
                ? `${record.description.slice(0, 60)}...`
                : record.description}
            </span>
          ) : null}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 60,
      render: (_, record) => {
        const s = STATUS_MAP[record.status] ?? {
          text: record.status,
          color: 'default',
        };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    {
      title: '分支',
      dataIndex: 'branch',
      width: 60,
      ellipsis: true,
      render: (_, r) => r.branch ?? '—',
    },
    {
      title: '编程语言',
      dataIndex: 'runnerEnv',
      width: 80,
      ellipsis: true,
      render: (_, r) => r.runnerEnv ?? '—',
    },
    {
      title: '最后部署',
      dataIndex: 'lastDeployed',
      width: 110,
      render: (_, r) => formatDateTime(r.lastDeployed),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 110,
      render: (_, r) => formatDateTime(r.createdAt),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 320,
      fixed: 'right',
      onCell: () => ({ style: { width: 320, minWidth: 320, maxWidth: 320 } }),
      render: (_, record) => {
        const canRun =
          record.status !== 'deploying' && record.status !== 'cloning';
        const repoUrl = gitUrlToHttps(record.gitUrl);
        const runButtonText =
          record.status === 'deploying'
            ? '部署中'
            : record.status === 'cloning'
              ? '克隆中'
              : '运行';
        return (
          <Space size="small" wrap={false}>
            {record.status === 'running' ? (
              <Button
                type="link"
                size="small"
                icon={<StopOutlined />}
                onClick={() => handleStop(record)}
              >
                停止
              </Button>
            ) : (
              <Button
                type="link"
                size="small"
                icon={<ThunderboltOutlined />}
                disabled={!canRun}
                onClick={() => handleRun(record)}
              >
                {runButtonText}
              </Button>
            )}
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
            {repoUrl ? (
              <Button
                type="link"
                size="small"
                icon={<ExportOutlined />}
                href={repoUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                仓库
              </Button>
            ) : null}
            <Popconfirm
              title="确定删除该应用？"
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  const statCards = [
    {
      key: 'total',
      title: '总应用数',
      value: stats?.total ?? 0,
      icon: <AppstoreOutlined />,
      suffix: '个',
    },
    {
      key: 'running',
      title: '运行中',
      value: stats?.running ?? 0,
      icon: <ThunderboltOutlined />,
      valueStyle: { color: '#52c41a' },
    },
    {
      key: 'undeployed',
      title: '未部署',
      value: stats?.undeployed ?? 0,
      icon: <ClockCircleOutlined />,
      valueStyle: { color: '#fa8c16' },
    },
    {
      key: 'stopped',
      title: '已停止',
      value: stats?.stopped ?? 0,
      icon: <StopOutlined />,
    },
    {
      key: 'failed',
      title: '失败',
      value: stats?.failed ?? 0,
      icon: <CloseCircleOutlined />,
      valueStyle: { color: '#ff4d4f' },
    },
  ];

  return (
    <PageContainer
      header={{
        title: '应用管理',
        subTitle: '从 Git 仓库导入应用，在算力资源上部署运行',
        extra: (
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={reload}
              loading={statsLoading}
            >
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              导入应用
            </Button>
          </Space>
        ),
      }}
    >
      {contextHolder}

      {/* 总览统计 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
          {statCards.map((item) => (
            <Statistic
              key={item.key}
              title={
                <span style={{ color: 'rgba(0,0,0,0.45)', fontSize: 13 }}>
                  {item.icon}
                  <span style={{ marginLeft: 8 }}>{item.title}</span>
                </span>
              }
              value={statsLoading ? '—' : item.value}
              suffix={item.suffix}
              valueStyle={item.valueStyle}
            />
          ))}
        </div>
      </Card>

      {/* 应用列表 */}
      <ProTable<Application>
          rowKey="id"
          actionRef={actionRef}
          columns={columns}
        request={async () => {
          try {
            const res = await applicationApi.getList();
            return {
              data: res.applications ?? [],
              success: true,
              total: (res.applications ?? []).length,
            };
          } catch {
            return { data: [], success: false, total: 0 };
          }
        }}
        search={false}
        pagination={{
          defaultPageSize: 10,
          showSizeChanger: true,
        }}
        options={{ reload: true, density: true }}
        scroll={{ x: 900 }}
        locale={{ emptyText: '暂无应用，点击「导入应用」添加' }}
      />

      <ApplicationForm
        open={formOpen}
        onOpenChange={setFormOpen}
        initialValues={formInitialValues}
        application={editingApp}
        onSuccess={reload}
      />
    </PageContainer>
  );
};

export default ApplicationManagement;
