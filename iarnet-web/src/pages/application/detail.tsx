import { PageContainer } from '@ant-design/pro-components';
import { history, useParams } from '@umijs/max';
import { Alert, Button, Card, Descriptions, Space, Spin, Tag } from 'antd';
import React, { useEffect, useState } from 'react';
import { applicationApi } from '@/services/application';
import type { Application, ApplicationStatus } from '@/services/application/typings';

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

const ApplicationDetail: React.FC = () => {
  const params = useParams();
  const id = params.id as string | undefined;

  const [app, setApp] = useState<Application | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    if (!id) {
      setError('缺少应用 ID');
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const res = await applicationApi.getList();
      const found = (res.applications ?? []).find((x: Application) => x.id === id);
      if (!found) {
        setError('应用不存在或已被删除');
        setApp(null);
      } else {
        setApp(found);
      }
    } catch (e) {
      setError('加载应用详情失败，请稍后重试');
      setApp(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [id]);

  const handleRun = async () => {
    if (!app) return;
    try {
      await applicationApi.run(app.id);
    } catch {
      // 错误由全局错误处理显示
    } finally {
      // 启动是异步的，这里只需刷新状态
      load();
    }
  };

  const title = app ? `${app.name}` : '';

  const statusView = app ? (() => {
    const s = STATUS_MAP[app.status] ?? { text: app.status, color: 'default' };
    return <Tag color={s.color}>{s.text}</Tag>;
  })() : null;

  return (
    <PageContainer
      header={{
        title,
        tags: statusView ?? undefined,
        onBack: () => history.push('/application'),
      }}
    >
      <Spin spinning={loading}>
        {!loading && error && (
          <Alert
            type="error"
            message={error}
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        {!loading && app && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {app.lastError && (
              <Alert
                type="error"
                message="最近一次构建/部署错误"
                description={app.lastError}
                showIcon
              />
            )}

            <Card
              title="基本信息"
              extra={
                <Space>
                  <Button onClick={load}>刷新</Button>
                  <Button
                    type="primary"
                    onClick={handleRun}
                    disabled={app.status === 'deploying' || app.status === 'cloning'}
                  >
                    {app.status === 'deploying' ? '部署中...' : '重新部署'}
                  </Button>
                </Space>
              }
            >
              <Descriptions column={2} bordered>
                <Descriptions.Item label="应用 ID">{app.id}</Descriptions.Item>
                <Descriptions.Item label="名称">{app.name}</Descriptions.Item>
                <Descriptions.Item label="Git 仓库">
                  {app.gitUrl ? (
                    <a href={app.gitUrl} target="_blank" rel="noreferrer">
                      {app.gitUrl}
                    </a>
                  ) : (
                    '—'
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="分支">
                  {app.branch ?? 'main'}
                </Descriptions.Item>
                <Descriptions.Item label="语言">
                  {app.lang ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="最后部署时间">
                  {formatDateTime(app.lastDeployed)}
                </Descriptions.Item>
                <Descriptions.Item label="创建时间">
                  {formatDateTime(app.createdAt)}
                </Descriptions.Item>
                <Descriptions.Item label="当前状态">
                  {statusView}
                </Descriptions.Item>
                <Descriptions.Item label="描述" span={2}>
                  {app.description || '—'}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Space>
        )}
      </Spin>
    </PageContainer>
  );
};

export default ApplicationDetail;

