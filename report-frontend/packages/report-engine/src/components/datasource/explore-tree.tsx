import { Empty, Spin, Tree } from 'antd';
import { useMemo } from 'react';
import type { DataNode } from 'antd/es/tree';
import { useExplore } from '@/hooks/use-explore';
import type { ColumnInfo, ExploreTreeProps, TableInfo } from '@/types';

/**
 * 表/列探查树。
 * 选中表 → 触发 onSelectTable；展开表节点 → 异步拉取列；选中列 → onSelectColumn。
 */
export default function ExploreTree({
  sourceId,
  service,
  onSelectTable,
  onSelectColumn,
  defaultExpandedTables,
}: ExploreTreeProps) {
  const { tables, columns, activeTable, loadingTables, loadingColumns, error, selectTable } =
    useExplore(service, sourceId);

  // columns 是当前展开表的列；构建树时为活动表挂上叶子
  const treeData = useMemo<DataNode[]>(() => {
    return tables.map((t) => {
      const isActive = activeTable === t.name;
      return {
        key: `table:${t.name}`,
        title: t.comment ? `${t.comment} (${t.name})` : t.name,
        isLeaf: false,
        children: isActive
          ? columns.map((c) => ({
              key: `column:${t.name}:${c.name}`,
              title: c.comment ?? c.name,
              isLeaf: true,
            }))
          : undefined,
      } satisfies DataNode;
    });
  }, [tables, columns, activeTable]);

  const handleExpand = (keys: React.Key[]) => {
    const last = keys.length ? keys[keys.length - 1] : null;
    if (typeof last === 'string' && last.startsWith('table:')) {
      const tableName = last.slice('table:'.length);
      selectTable(tableName);
      const table = tables.find((t) => t.name === tableName) ?? null;
      onSelectTable?.(table);
    } else {
      selectTable(null);
      onSelectTable?.(null);
    }
  };

  const handleSelect = (keys: React.Key[]) => {
    const key = keys[0];
    if (typeof key !== 'string') {
      onSelectTable?.(null);
      onSelectColumn?.(null);
      return;
    }
    if (key.startsWith('column:')) {
      const [, tableName, colName] = key.split(':');
      const col: ColumnInfo | null = columns.find((c) => c.name === colName) ?? null;
      onSelectTable?.(tables.find((t) => t.name === tableName) ?? null);
      onSelectColumn?.(col);
    } else if (key.startsWith('table:')) {
      const tableName = key.slice('table:'.length);
      const table: TableInfo | null = tables.find((t) => t.name === tableName) ?? null;
      onSelectTable?.(table);
      onSelectColumn?.(null);
    }
  };

  if (!sourceId) {
    return <Empty description="请先选择数据源" />;
  }
  if (loadingTables) {
    return <Spin />;
  }
  if (error) {
    return <Empty description={error.message} />;
  }
  if (!tables.length) {
    return <Empty description="无可用表" />;
  }

  const expandedKeys = defaultExpandedTables?.map((t) => `table:${t}`) ?? (
    activeTable ? [`table:${activeTable}`] : []
  );

  return (
    <Spin spinning={loadingColumns}>
      <Tree
        treeData={treeData}
        onExpand={handleExpand}
        onSelect={handleSelect}
        expandedKeys={expandedKeys}
        showLine
      />
    </Spin>
  );
}
