import {
  ModalForm,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { message } from 'antd';
import type { FC } from 'react';
import { applicationApi } from '@/services/application';
import type { Application } from '@/services/application/typings';

export interface ApplicationFormValues {
  name: string;
  gitUrl?: string;
  branch?: string;
  description?: string;
  runnerEnv?: string;
}

interface ApplicationFormProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  initialValues?: Partial<ApplicationFormValues> | null;
  /** 编辑时传入应用，创建时为 undefined */
  application?: Application | null;
  onSuccess?: () => void;
}

const ApplicationForm: FC<ApplicationFormProps> = ({
  open,
  onOpenChange,
  initialValues,
  application,
  onSuccess,
}) => {
  const [messageApi, contextHolder] = message.useMessage();
  const isEdit = Boolean(application?.id);

  const handleFinish = async (values: ApplicationFormValues) => {
    try {
      if (isEdit && application?.id) {
        await applicationApi.update(application.id, {
          name: values.name,
          description: values.description ?? '',
          runner_env: values.runnerEnv,
        });
        messageApi.success('应用已更新');
      } else {
        await applicationApi.create({
          name: values.name,
          git_url: values.gitUrl,
          branch: values.branch || 'main',
          description: values.description ?? '',
          runner_env: values.runnerEnv,
        });
        messageApi.success('应用已创建');
      }
      onOpenChange(false);
      onSuccess?.();
      return true;
    } catch (e) {
      messageApi.error(isEdit ? '更新失败，请重试' : '创建失败，请重试');
      return false;
    }
  };

  return (
    <>
      {contextHolder}
      <ModalForm<ApplicationFormValues>
        title={isEdit ? '编辑应用' : '导入应用'}
        open={open}
        onOpenChange={onOpenChange}
        width={560}
        initialValues={
          initialValues ?? {
            branch: 'main',
          }
        }
        modalProps={{ destroyOnClose: true }}
        onFinish={handleFinish}
      >
        <ProFormText
          name="name"
          label="应用名称"
          placeholder="例如：用户管理系统"
          rules={[{ required: true, message: '请输入应用名称' }]}
          fieldProps={{ maxLength: 128 }}
        />
        <ProFormText
          name="gitUrl"
          label="Git 仓库地址"
          placeholder="https://github.com/username/repo"
          disabled={isEdit}
          fieldProps={{ maxLength: 512 }}
        />
        <ProFormText
          name="branch"
          label="分支"
          placeholder="main"
          disabled={isEdit}
          fieldProps={{ maxLength: 64 }}
        />
        <ProFormSelect
          name="runnerEnv"
          label="运行环境"
          placeholder="选择运行环境"
          request={async () => {
            const { environments } = await applicationApi.getRunnerEnvironments();
            return (environments || []).map((env) => ({ label: env, value: env }));
          }}
        />
        <ProFormTextArea
          name="description"
          label="描述（可选）"
          placeholder="应用描述信息"
          fieldProps={{ rows: 3, maxLength: 500 }}
        />
      </ModalForm>
    </>
  );
};

export default ApplicationForm;
