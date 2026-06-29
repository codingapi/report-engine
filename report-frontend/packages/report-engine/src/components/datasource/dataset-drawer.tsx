import { useEffect, useState } from 'react';
import { App as AntdApp, Button, Drawer, Input, Modal, Space, Spin, Typography } from 'antd';
import type { DataSourceDTO } from '@coding-report/report-api';
import DatasetEditor from './dataset-editor';
import SqlDatasetModal from './sql-dataset-modal';
import {
  fromDatasetDto,
  fromIntrospected,
  mergeTables,
  splitTableNames,
  tablesToDatasetDtos,
} from './datasource-service';
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
  const [sqlModalOpen, setSqlModalOpen] = useState(false);
  // SQL 编辑模式：非空时弹窗为编辑该数据集；null 为新建
  const [editingSql, setEditingSql] = useState<WizardTable | null>(null);
  // 重新解析弹窗 + 指定解析的表名（空=全部）
  const [reparseOpen, setReparseOpen] = useState(false);
  const [parseTableNames, setParseTableNames] = useState('');

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
      // 指定表名时只解析这些表（空=全部）；mergeTables 会保留 SQL 数据集与已编辑别名/顺序
      const names = splitTableNames(parseTableNames);
      const fresh = await service.introspect(datasourceId, names);
      setTables((prev) => mergeTables(prev, fresh.map(fromIntrospected)));
      message.success(`解析完成，共 ${fresh.length} 张表`);
      setReparseOpen(false);
    } catch (e) {
      message.error(`重新解析失败: ${(e as Error).message}`);
    } finally {
      setParsing(false);
    }
  };

  /** 单表重新解析：只同步该表最新结构，合并已编辑别名/字段顺序，其它表不动。 */
  const handleReparseTable = async (table: WizardTable) => {
    if (!datasourceId || !table.sourceTable) return;
    setParsing(true);
    try {
      const fresh = await service.introspect(datasourceId, [table.sourceTable]);
      const freshOne = fresh.map(fromIntrospected).find((t) => t.name === table.sourceTable);
      if (!freshOne) {
        message.warning(`未找到表 ${table.sourceTable}，可能已被删除`);
        return;
      }
      // 用 mergeTables 对单表合并：传当前该表 + 新解析结果
      const [merged] = mergeTables([table], [freshOne]);
      setTables((prev) => prev.map((t) => (t.name === table.name ? merged : t)));
      message.success(`已同步表 ${table.sourceTable} 的最新结构`);
    } catch (e) {
      message.error(`同步失败: ${(e as Error).message}`);
    } finally {
      setParsing(false);
    }
  };

  /** 应用 SQL 弹窗结果：新建追加 / 编辑替换（按原 name 定位）。 */
  const handleSqlConfirm = (table: WizardTable) => {
    setTables((prev) => {
      if (editingSql) {
        return prev.map((t) => (t.name === editingSql.name ? table : t));
      }
      return [...prev, table];
    });
    setSqlModalOpen(false);
    setEditingSql(null);
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
          <Button type="primary" loading={saving} onClick={handleSave}>
            保存
          </Button>
          <Button onClick={onClose}>取消</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <DatasetEditor
          tables={tables}
          onChange={setTables}
          onReparseTable={dto?.type === 'DB' ? handleReparseTable : undefined}
          onEditSql={
            dto?.type === 'DB' && service.introspectSql
              ? (t) => {
                  setEditingSql(t);
                  setSqlModalOpen(true);
                }
              : undefined
          }
          toolbar={
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 12,
              }}
            >
              <Text type="secondary">维护表别名、字段别名或删除不需要的表</Text>
              <Space>
                {dto?.type === 'DB' && (
                  <Button onClick={() => setReparseOpen(true)}>重新解析</Button>
                )}
                {dto?.type === 'DB' && service.introspectSql && (
                  <Button
                    type="primary"
                    onClick={() => {
                      setEditingSql(null);
                      setSqlModalOpen(true);
                    }}
                  >
                    新建 SQL 数据集
                  </Button>
                )}
              </Space>
            </div>
          }
          emptyHint="该数据源暂无数据集，可点击「重新解析」获取"
        />
      </Spin>
      <Modal
        title="重新解析表结构"
        open={reparseOpen}
        onOk={handleReparse}
        onCancel={() => setReparseOpen(false)}
        okText="解析"
        cancelText="取消"
        confirmLoading={parsing}
        destroyOnHidden
      >
        <div style={{ marginBottom: 8 }}>
          <Text type="secondary">指定表名（多个用逗号或换行分隔，留空解析全部）</Text>
        </div>
        <Input.TextArea
          autoSize={{ minRows: 3, maxRows: 8 }}
          placeholder={'如：\nt_student\nt_student_class'}
          value={parseTableNames}
          onChange={(e) => setParseTableNames(e.target.value)}
          allowClear
        />
      </Modal>
      {datasourceId && (
        <SqlDatasetModal
          open={sqlModalOpen}
          datasourceId={datasourceId}
          service={service}
          existingNames={tables.map((t) => t.name)}
          editing={editingSql}
          onClose={() => {
            setSqlModalOpen(false);
            setEditingSql(null);
          }}
          onConfirm={handleSqlConfirm}
        />
      )}
    </Drawer>
  );
}
