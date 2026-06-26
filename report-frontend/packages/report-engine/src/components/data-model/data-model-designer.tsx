import { useEffect, useState } from 'react';
import { App as AntdApp, Spin, Tabs, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type {
  DataModelDataset,
  DataModelSource,
  RelationshipInfo,
} from '@coding-report/report-api';

const { Title } = Typography;

/**
 * 数据模型设计页使用的完整 DTO。
 *
 * 后端两个端点拼合而成：
 * - {@code GET /api/datamodels} 列表项（DataModelBrief）提供 id/name
 * - {@code GET /api/datamodels/{id}} 详情（DataModelInfo）提供 datasets/relationships/datasources
 *
 * 设计页基于"已加载完整模型"的前提工作，故 id/name/datasets/relationships 必填。
 */
export interface DataModelDTO {
  id: string;
  name: string;
  status?: string;
  createTime?: number;
  updateTime?: number;
  datasets: DataModelDataset[];
  relationships: RelationshipInfo[];
  datasources?: DataModelSource[];
}

/**
 * 数据模型设计页对外依赖的服务注入（app-pc 用 report-api 实现）。
 * 组件本身不直接调 API，保持纯 UI 可复用。
 */
export interface DataModelDesignerService {
  /** 加载数据模型完整 DTO（id + name + datasets + relationships + datasources） */
  getDataModel: (id: string) => Promise<DataModelDTO>;
  /** 保存（新建/更新） */
  saveDataModel: (dto: DataModelDTO) => Promise<void>;
}

export interface DataModelDesignerProps {
  dataModelId: string;
  service: DataModelDesignerService;
}

const datasetColumns: ColumnsType<DataModelDataset> = [
  { title: '别名', dataIndex: 'alias', key: 'alias' },
  { title: '形态', dataIndex: 'kind', key: 'kind', width: 100 },
  { title: '物理表', dataIndex: 'sourceTable', key: 'sourceTable' },
  {
    title: '字段数',
    key: 'fieldCount',
    width: 80,
    render: (_v, record) => record.fields?.length ?? 0,
  },
];

const unionColumns: ColumnsType<DataModelDataset> = [
  { title: '别名', dataIndex: 'alias', key: 'alias' },
  { title: '成员数', key: 'memberCount', width: 100 },
];

const relationshipColumns: ColumnsType<RelationshipInfo> = [
  {
    title: '左字段',
    key: 'left',
    render: (_v, r) => `${r.left.datasetId}.${r.left.field}`,
  },
  { title: 'JOIN', dataIndex: 'joinType', key: 'joinType', width: 80 },
  {
    title: '右字段',
    key: 'right',
    render: (_v, r) => `${r.right.datasetId}.${r.right.field}`,
  },
];

const formatTime = (t?: number) => (t ? new Date(t).toLocaleString() : '-');

/**
 * 数据模型设计页：顶部标题栏 + 三 tab（数据集 / 数据合集 / 关系）。
 * 当前为框架骨架，每个 tab 内放 antd Table 空数据，columns 已定义。
 */
const DataModelDesigner: React.FC<DataModelDesignerProps> = ({
  dataModelId,
  service,
}) => {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [model, setModel] = useState<DataModelDTO | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    service
      .getDataModel(dataModelId)
      .then((dto) => {
        if (active) setModel(dto);
      })
      .catch((err) => {
        if (active) message.error(`加载数据模型失败：${err?.message ?? err}`);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dataModelId, service, message]);

  if (loading || !model) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin tip="加载中…" />
      </div>
    );
  }

  return (
    <div style={{ padding: 16 }}>
      <Title level={4} style={{ marginBottom: 16 }}>
        {model.name}
        <span
          style={{
            marginLeft: 12,
            fontSize: 12,
            color: 'rgba(0,0,0,0.45)',
            fontWeight: 'normal',
          }}
        >
          {model.status ? `（${model.status}）` : ''} 最后更新{' '}
          {formatTime(model.updateTime)}
        </span>
      </Title>
      <Tabs
        items={[
          {
            key: 'datasets',
            label: '数据集',
            children: (
              <Table<DataModelDataset>
                rowKey="id"
                size="small"
                columns={datasetColumns}
                dataSource={[]}
                pagination={false}
              />
            ),
          },
          {
            key: 'unions',
            label: '数据合集',
            children: (
              <Table<DataModelDataset>
                rowKey="id"
                size="small"
                columns={unionColumns}
                dataSource={[]}
                pagination={false}
              />
            ),
          },
          {
            key: 'relations',
            label: '关系',
            children: (
              <Table<RelationshipInfo>
                rowKey={(r) =>
                  `${r.left.datasetId}.${r.left.field}-${r.right.datasetId}.${r.right.field}`
                }
                size="small"
                columns={relationshipColumns}
                dataSource={[]}
                pagination={false}
              />
            ),
          },
        ]}
      />
    </div>
  );
};

export default DataModelDesigner;
