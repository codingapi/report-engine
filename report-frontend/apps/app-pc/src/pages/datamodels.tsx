import { useEffect, useState } from 'react';
import { Layout, List, Tabs, Typography, Empty, Descriptions, Spin, message } from 'antd';
import { DatabaseOutlined } from '@ant-design/icons';
import {
  listDataModelBriefs,
  getDataModel,
} from '@coding-report/report-api';
import type {
  DataModelBrief,
  DataModelInfo,
  DataModelDataset,
  DataModelSource,
} from '@coding-report/report-api';
import {
  DatasetManager,
  RelationEditor,
} from '@coding-report/report-datasource';
import type {
  DatasetDef,
  DataSourceConfig,
  Relationship,
} from '@coding-report/report-datasource';

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

/**
 * 后端 DatasetDTO（kind=TABLE/UNION）→ report-datasource 的 DatasetDef（kind=PHYSICAL/UNION）。
 * 两个端点的 dataset 字段集不同（configs/{id} 精简视图无 kind，datamodels/{id} 完整 DTO 有），
 * 此处统一适配：kind 缺失或 TABLE 都按物理表处理。
 */
const toDatasetDef = (d: DataModelDataset): DatasetDef => {
  const fields = (d.fields ?? []).map((f) => ({
    name: f.name,
    alias: f.alias,
    dataType: f.dataType,
    primaryKey: f.primaryKey,
  }));
  if (d.kind === 'UNION') {
    return {
      kind: 'UNION',
      id: d.id,
      alias: d.alias,
      baseDatasetIds: (d.members ?? []).map((m) => m.datasetId),
      fields,
    };
  }
  return {
    kind: 'PHYSICAL',
    id: d.id,
    alias: d.alias,
    sourceId: d.datasourceId ?? '',
    table: d.sourceTable ?? '',
    fields,
  };
};

/** 后端 DataSourceDTO → report-datasource 的 DataSourceConfig（来源列只需 id/name/type） */
const toDataSourceConfig = (s: DataModelSource): DataSourceConfig => ({
  id: s.id,
  name: s.name,
  type: s.type,
  options: s.config,
});

const DataModelsPage = () => {
  const [briefs, setBriefs] = useState<DataModelBrief[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [model, setModel] = useState<DataModelInfo | null>(null);
  const [modelLoading, setModelLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listDataModelBriefs()
      .then((list) => {
        if (!active) return;
        setBriefs(list);
        if (list.length > 0) setSelectedId(list[0].id);
      })
      .catch((e) => {
        message.error(`加载数据模型列表失败: ${(e as Error).message}`);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedId) {
      setModel(null);
      return;
    }
    let active = true;
    setModelLoading(true);
    getDataModel(selectedId)
      .then((data) => {
        if (!active) return;
        setModel(data);
      })
      .catch((e) => {
        message.error(`加载数据模型详情失败: ${(e as Error).message}`);
      })
      .finally(() => {
        if (active) setModelLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedId]);

  const datasets: DatasetDef[] = (model?.datasets ?? []).map(toDatasetDef);
  const relationships = (model?.relationships ?? []) as unknown as Relationship[];
  const dataSources: DataSourceConfig[] = (model?.datasources ?? []).map(
    toDataSourceConfig,
  );

  return (
    <Layout style={{ height: '100%', minHeight: 600 }}>
      <Sider width={240} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: 12, fontWeight: 600 }}>数据模型</div>
        <Spin spinning={loading}>
          {briefs.length === 0 && !loading ? (
            <Empty description="暂无数据模型" style={{ padding: 24 }} />
          ) : (
            <List
              size="small"
              dataSource={briefs}
              renderItem={(item) => (
                <List.Item
                  style={{
                    cursor: 'pointer',
                    padding: '8px 12px',
                    background:
                      item.id === selectedId ? '#e6f4ff' : undefined,
                  }}
                  onClick={() => setSelectedId(item.id)}
                >
                  <List.Item.Meta
                    avatar={<DatabaseOutlined />}
                    title={item.name}
                    description={<Text type="secondary">{item.id}</Text>}
                  />
                </List.Item>
              )}
            />
          )}
        </Spin>
      </Sider>
      <Content style={{ padding: 16, overflow: 'auto' }}>
        {!selectedId ? (
          <Empty description="请选择左侧数据模型" style={{ marginTop: 80 }} />
        ) : modelLoading ? (
          <div style={{ textAlign: 'center', paddingTop: 80 }}>
            <Spin />
          </div>
        ) : (
          <Tabs
            defaultActiveKey="datasets"
            items={[
              {
                key: 'datasets',
                label: '数据集',
                children: (
                  <DatasetManager
                    datasets={datasets}
                    dataSources={dataSources}
                  />
                ),
              },
              {
                key: 'relations',
                label: '关系',
                children: (
                  <RelationEditor datasets={datasets} relationships={relationships} />
                ),
              },
              {
                key: 'params',
                label: '参数',
                children: <Empty description="参数管理（占位）" style={{ padding: 24 }} />,
              },
            ]}
          />
        )}
      </Content>
      <Sider width={280} theme="light" style={{ borderLeft: '1px solid #f0f0f0' }}>
        <div style={{ padding: 12, fontWeight: 600 }}>属性</div>
        {selectedId && model ? (
          <Descriptions column={1} size="small" bordered style={{ padding: 12 }}>
            <Descriptions.Item label="ID">{selectedId}</Descriptions.Item>
            <Descriptions.Item label="名称">
              {briefs.find((b) => b.id === selectedId)?.name ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="数据集数">
              {model.datasets.length}
            </Descriptions.Item>
            <Descriptions.Item label="关系数">
              {model.relationships.length}
            </Descriptions.Item>
          </Descriptions>
        ) : (
          <Text type="secondary" style={{ padding: 12, display: 'block' }}>
            未选中数据模型
          </Text>
        )}
      </Sider>
    </Layout>
  );
};

export default DataModelsPage;
