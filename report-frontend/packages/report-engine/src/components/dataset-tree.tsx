import React, { useMemo } from 'react';
import { Tree, Tag } from 'antd';
import { KeyOutlined } from '@ant-design/icons';
import type { Dataset, DatasetField, Relationship, DataSourceType } from '@/types';
import { dataTypeLabel } from '@/types';

interface DatasetTreeProps {
  datasets: Dataset[];
  relationships?: Relationship[];
}

interface FieldDragData {
  datasetId: string;
  field: string;
  alias: string;
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

// ─── 字段关联标注构建 ────────────────────────────────────

interface FieldRelation {
  direction: '→' | '←';
  targetDatasetAlias: string;
  targetFieldAlias: string;
}

/**
 * 构建字段关联映射：双侧标注，用箭头区分方向。
 * → 表示引用方（FK 侧），← 表示被引用方（PK 侧）。
 */
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

    // left 侧 → 指向 right
    if (!map.has(leftKey)) map.set(leftKey, []);
    map.get(leftKey)!.push({
      direction: '→',
      targetDatasetAlias: findDsAlias(rel.right.datasetId),
      targetFieldAlias: findFieldAlias(rel.right.datasetId, rel.right.field),
    });

    // right 侧 ← 来自 left
    if (!map.has(rightKey)) map.set(rightKey, []);
    map.get(rightKey)!.push({
      direction: '←',
      targetDatasetAlias: findDsAlias(rel.left.datasetId),
      targetFieldAlias: findFieldAlias(rel.left.datasetId, rel.left.field),
    });
  }

  return map;
}

// ─── 节点构建 ────────────────────────────────────────────

/** 字段节点（可拖拽，含关联标注） */
function buildFieldNode(
  ds: Dataset,
  f: DatasetField,
  fieldRelationMap: Map<string, FieldRelation[]>,
) {
  const fieldKey = `${ds.id}.${f.name}`;
  const relations = fieldRelationMap.get(fieldKey);

  return {
    key: fieldKey,
    title: (
      <span
        className="re-field-drag"
        draggable
        onDragStart={(e) => {
          const data: FieldDragData = {
            datasetId: ds.id,
            field: f.name,
            alias: f.alias || f.name,
          };
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
}

/** 数据集节点（含字段子节点） */
function buildDatasetNode(ds: Dataset, fieldRelationMap: Map<string, FieldRelation[]>) {
  return {
    key: ds.id,
    title: (
      <span>
        {getSourceTag(ds.sourceType)}
        {ds.alias || ds.id}
      </span>
    ),
    selectable: false,
    children: ds.fields.map((f) => buildFieldNode(ds, f, fieldRelationMap)),
  };
}

// ─── 组件 ────────────────────────────────────────────────

/**
 * 数据集树：展示数据集和字段的层级结构，字段节点支持拖拽。
 * - 数据集图标按 sourceType 区分（CSV/JSON/DB/API/EXCEL）
 * - 参与关系的字段显示关联标注（🔗 → 目标表.字段 JOIN类型）
 */
const DatasetTree: React.FC<DatasetTreeProps> = ({ datasets, relationships = [] }) => {
  const treeData = useMemo(() => {
    const fieldRelationMap = buildFieldRelationMap(relationships, datasets);
    return datasets.map((ds) => buildDatasetNode(ds, fieldRelationMap));
  }, [datasets, relationships]);

  return (
    <div className="re-dataset-tree">
      <Tree treeData={treeData} defaultExpandAll blockNode showLine={{ showLeafIcon: false }} />
    </div>
  );
};

export default DatasetTree;
