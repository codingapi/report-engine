import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Drawer, Space, Spin, Typography } from 'antd';
import type { DataSourceDTO } from '@coding-report/report-api';
import DatasetEditor from './dataset-editor';
import { fromDatasetDto, fromIntrospected, mergeTables, tablesToDatasetDtos } from './datasource-service';
import type { DataSourceService, WizardTable } from './datasource-service';

const { Text } = Typography;

export interface DatasetDrawerProps {
  open: boolean;
  datasourceId?: string | null;
  datasourceName?: string;
  service: DataSourceService;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * 数据集维护抽屉：从数据源列表「数据集数量」点击进入，
 * 只查看/维护该连接下的数据集（表别名、字段别名、删除表），不改连接配置。
 */
export default function DatasetDrawer({
  open,
  datasourceId,
  datasourceName,
  service,
  onClose,
  onSaved,
}: DatasetDrawerProps) {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [dto, setDto] = useState<DataSourceDTO | null>(null);
  const [tables, setTables] = useState<WizardTable[]>([]);

  useEffect(() => {
    if (!open || !datasourceId) return;
    let active = true;
    setLoading(true);
    (async () => {
      try {
        const loaded = await service.get(datasourceId);
        if (!active) return;
        setDto(loaded);
        setTables((loaded.datasets ?? []).map(fromDatasetDto));
      } catch (e) {
        if (active) message.error(`加载数据集失败: ${(e as Error).message}`);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, datasourceId]);

  const handleReparse = async () => {
    if (!datasourceId) return;
    setParsing(true);
    try {
      const fresh = await service.introspect(datasourceId);
      setTables((prev) => mergeTables(prev, fresh.map(fromIntrospected)));
      message.success(`解析完成，共 ${fresh.length} 张表`);
    } catch (e) {
      message.error(`重新解析失败: ${(e as Error).message}`);
    } finally {
      setParsing(false);
    }
  };

  const handleSave = async () => {
    if (!dto) return;
    setSaving(true);
    try {
      await service.save({ ...dto, datasets: tablesToDatasetDtos(tables, dto.id) });
      message.success('数据集已保存');
      onSaved();
      onClose();
    } catch (e) {
      message.error(`保存失败: ${(e as Error).message}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={`数据集维护${datasourceName ? ` · ${datasourceName}` : ''}`}
      open={open}
      onClose={onClose}
      width={1040}
      destroyOnHidden
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={handleSave}>
            保存
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <DatasetEditor
          tables={tables}
          onChange={setTables}
          toolbar={
            <Space>
              <Button loading={parsing} onClick={handleReparse}>
                重新解析
              </Button>
              <Text type="secondary">维护表别名、字段别名或删除不需要的表</Text>
            </Space>
          }
          emptyHint="该数据源暂无数据集，可点击「重新解析」获取"
        />
      </Spin>
    </Drawer>
  );
}
