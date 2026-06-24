import React, { useMemo } from 'react';
import { Empty, Tag, Tree, Divider, List } from 'antd';
import { SwapOutlined, KeyOutlined } from '@ant-design/icons';
import type { Relationship, Dataset, DataSourceType } from '@/types';
import { findDataset, dataTypeLabel } from '@/types';

interface RelationshipListProps {
  relationships: Relationship[];
  datasets: Dataset[];
}

// ─── 数据源类型标签 ──────────────────────────────────────

const SOURCE_COLORS: Record<DataSourceType, string> = {
  CSV: 'green',
  JSON: 'orange',
  DB: 'blue',
  API: 'purple',
  EXCEL: 'cyan',
};

function getSourceTag(sourceType?: DataSourceType): React.ReactNode {
  if (!sourceType) return null;
  return (
    <Tag
      color={SOURCE_COLORS[sourceType] || 'default'}
      style={{ marginRight: 4, fontSize: 10, lineHeight: '16px', padding: '0 4px' }}
    >
      {sourceType}
    </Tag>
  );
}

// ─── Union-Find 分组 ──────────────────────────────────────

interface DatasetGroup {
  datasets: Dataset[];
}

/**
 * 按 relationships 将 datasets 分为连通分量。
 * 仅返回有关系的数据集（standalone 被过滤掉）。
 */
function groupDatasets(datasets: Dataset[], relationships: Relationship[]): DatasetGroup[] {
  if (relationships.length === 0) return [];

  const parent = new Map<string, string>();

  function find(x: string): string {
    if (!parent.has(x)) parent.set(x, x);
    if (parent.get(x) !== x) parent.set(x, find(parent.get(x)!));
    return parent.get(x)!;
  }

  function union(a: string, b: string) {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent.set(ra, rb);
  }

  for (const rel of relationships) {
    union(rel.left.datasetId, rel.right.datasetId);
  }

  // 收集有关系的数据集 ID
  const involvedIds = new Set<string>();
  for (const rel of relationships) {
    involvedIds.add(rel.left.datasetId);
    involvedIds.add(rel.right.datasetId);
  }

  // 按连通分量分组
  const groupMap = new Map<string, DatasetGroup>();
  for (const ds of datasets) {
    if (!involvedIds.has(ds.id)) continue;
    const root = find(ds.id);
    if (!groupMap.has(root)) {
      groupMap.set(root, { datasets: [] });
    }
    groupMap.get(root)!.datasets.push(ds);
  }

  return Array.from(groupMap.values());
}

// ─── 字段关联标注（仅 FK 侧） ────────────────────────────

interface FieldRelation {
  direction: '→' | '←';
  targetDatasetAlias: string;
  targetFieldAlias: string;
}

function buildFieldRelationMap(
  relationships: Relationship[],
  datasets: Dataset[],
): Map<string, FieldRelation[]> {
  const map = new Map<string, FieldRelation[]>();
  const findDsAlias = (datasetId: string) =>
    datasets.find((d) => d.id === datasetId)?.alias || datasetId;
  const findFieldAlias = (datasetId: string, fieldName: string) => {
    const ds = datasets.find((d) => d.id === datasetId);
    return ds?.fields.find((f) => f.name === fieldName)?.alias || fieldName;
  };

  for (const rel of relationships) {
    const leftKey = `${rel.left.datasetId}.${rel.left.field}`;
    const rightKey = `${rel.right.datasetId}.${rel.right.field}`;

    if (!map.has(leftKey)) map.set(leftKey, []);
    map.get(leftKey)!.push({
      direction: '→',
      targetDatasetAlias: findDsAlias(rel.right.datasetId),
      targetFieldAlias: findFieldAlias(rel.right.datasetId, rel.right.field),
    });

    if (!map.has(rightKey)) map.set(rightKey, []);
    map.get(rightKey)!.push({
      direction: '←',
      targetDatasetAlias: findDsAlias(rel.left.datasetId),
      targetFieldAlias: findFieldAlias(rel.left.datasetId, rel.left.field),
    });
  }
  return map;
}

// ─── 分组树节点构建 ──────────────────────────────────────

function buildGroupedTreeData(datasets: Dataset[], relationships: Relationship[]): any[] {
  const groups = groupDatasets(datasets, relationships);
  if (groups.length === 0) return [];

  const fieldRelationMap = buildFieldRelationMap(relationships, datasets);

  return groups.map((g) => {
    const groupLabel = g.datasets.map((ds) => ds.alias || ds.id).join(' - ');

    const children = g.datasets.map((ds) => ({
      key: `grouped-${ds.id}`,
      title: (
        <span>
          {getSourceTag(ds.sourceType)}
          {ds.alias || ds.id}
        </span>
      ),
      selectable: false,
      children: ds.fields.map((f) => {
        const fieldKey = `${ds.id}.${f.name}`;
        const relations = fieldRelationMap.get(fieldKey);
        return {
          key: `grouped-${fieldKey}`,
          title: (
            <span
              className="re-field-drag"
              draggable
              onDragStart={(e) => {
                const data = { datasetId: ds.id, field: f.name, alias: f.alias || f.name };
                e.dataTransfer.setData('text/plain', JSON.stringify(data));
                e.dataTransfer.effectAllowed = 'copy';
              }}
            >
              <span style={{ marginRight: 4 }}>{f.alias || f.name}</span>
              <span className="re-field-type">{dataTypeLabel(f.dataType)}</span>
              {f.primaryKey && <KeyOutlined className="re-field-pk" style={{ marginLeft: 4 }} />}
              {relations &&
                relations.map((rel, i) => (
                  <span key={i} className="re-field-relation">
                    🔗 {rel.direction} {rel.targetDatasetAlias}.{rel.targetFieldAlias}
                  </span>
                ))}
            </span>
          ),
          isLeaf: true,
          selectable: false,
        };
      }),
    }));

    return {
      key: `group-${g.datasets
        .map((d) => d.id)
        .sort()
        .join('-')}`,
      title: <span className="re-ds-group-title">📦 {groupLabel}</span>,
      selectable: false,
      children,
    };
  });
}

// ─── 关系列表渲染 ────────────────────────────────────────

function RelationshipItems({
  relationships,
  datasets,
}: {
  relationships: Relationship[];
  datasets: Dataset[];
}) {
  const endpoint = (ref: { datasetId: string; field: string }) => {
    const ds = findDataset(datasets, ref.datasetId);
    const dsName = ds?.alias || ref.datasetId;
    const fieldName = ds?.fields.find((f) => f.name === ref.field)?.alias || ref.field;
    return `${dsName}.${fieldName}`;
  };

  return (
    <List
      size="small"
      dataSource={relationships}
      split={false}
      renderItem={(r, i) => (
        <List.Item
          key={i}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            padding: '6px 10px',
            marginBottom: 6,
            background: 'var(--re-color-bg-subtle)',
            border: '1px solid var(--re-color-border-light)',
            borderRadius: 'var(--re-radius-base)',
            fontSize: 12,
            borderBottom: '1px solid var(--re-color-border-light)',
          }}
        >
          <span style={{ wordBreak: 'break-all' }}>{endpoint(r.left)}</span>
          <SwapOutlined style={{ color: 'var(--re-color-primary)', flexShrink: 0 }} />
          <span style={{ wordBreak: 'break-all' }}>{endpoint(r.right)}</span>
          <Tag color="blue" style={{ marginLeft: 'auto' }}>
            {r.joinType}
          </Tag>
        </List.Item>
      )}
    />
  );
}

// ─── 主组件 ──────────────────────────────────────────────

/**
 * 数据关系面板：上半区关系列表 + 下半区数据分组树。
 * 分组树仅展示有关系的数据集（按连通分量分组），不展示 JOIN 细节。
 */
const RelationshipList: React.FC<RelationshipListProps> = ({ relationships, datasets }) => {
  const groupedTreeData = useMemo(
    () => buildGroupedTreeData(datasets, relationships),
    [datasets, relationships],
  );

  if (relationships.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无数据关系"
        style={{ margin: '24px 0' }}
      />
    );
  }

  return (
    <div className="re-rel-panel">
      {/* 上半区：关系列表 */}
      <RelationshipItems relationships={relationships} datasets={datasets} />

      {/* 下半区：数据分组树 */}
      <Divider style={{ margin: '12px 0' }} />
      <div className="re-rel-group-header">数据分组</div>
      <div className="re-dataset-tree">
        <Tree
          treeData={groupedTreeData}
          defaultExpandAll
          blockNode
          showLine={{ showLeafIcon: false }}
        />
      </div>
    </div>
  );
};

export default RelationshipList;
