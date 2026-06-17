import React, { useRef, useState, useMemo } from 'react';
import { Input, Menu, List, Empty } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { InputRef } from 'antd';
import type { ReportValue, Dataset, LoopBlock, ReportParam, ExpressionCatalog, FunctionMeta } from '../../types';
import { findDataset, dataTypeLabel } from '../../types';
import { templateToString, parseTemplate } from '../../value-text';
import { matchWithPinyin } from '../../pinyin';

interface ExpressionBuilderProps {
  value: ReportValue;
  datasets: Dataset[];
  loopBlocks: LoopBlock[];
  params?: ReportParam[];
  functions?: ExpressionCatalog;
  onChange: (value: ReportValue) => void;
}

type Category = 'field' | 'loop' | 'param' | 'agg' | 'func';

/** 内置聚合（后端未提供时的兜底） */
const FALLBACK_AGGS: FunctionMeta[] = [
  { name: 'COUNT', label: '计数', params: ['字段'], description: '统计行数，如 COUNT(employees.id)' },
  { name: 'COUNT_DISTINCT', label: '去重计数', params: ['字段'], description: '统计不重复的行数' },
  { name: 'SUM', label: '求和', params: ['字段'], description: '计算数值字段的总和' },
  { name: 'AVG', label: '平均值', params: ['字段'], description: '计算数值字段的平均值' },
  { name: 'MAX', label: '最大值', params: ['字段'], description: '获取字段的最大值' },
  { name: 'MIN', label: '最小值', params: ['字段'], description: '获取字段的最小值' },
];

/** 判断光标位置是否落在某个 ${…} 占位内部 */
function isInsideHole(text: string, pos: number): boolean {
  const re = /\$\{[^}]*\}/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const innerStart = m.index + 2;
    const innerEnd = m.index + m[0].length - 1;
    if (pos >= innerStart && pos <= innerEnd) return true;
  }
  return false;
}

/**
 * 表达式构建器：textarea + 一级分类菜单 + 二级选择列表
 */
const ExpressionBuilder: React.FC<ExpressionBuilderProps> = ({
  value,
  datasets,
  loopBlocks,
  params = [],
  functions,
  onChange,
}) => {
  const [text, setText] = useState(() => templateToString(value));
  const [category, setCategory] = useState<Category | null>(null);
  const [search, setSearch] = useState('');
  const taRef = useRef<InputRef>(null);

  const getTA = (): HTMLTextAreaElement | null => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (taRef.current as any)?.resizableTextArea?.textArea ?? null;
  };

  const emit = (next: string) => {
    setText(next);
    onChange(parseTemplate(next));
  };

  /** 在光标处插入表达式 */
  const doInsert = (rawExpr: string, caretFromEnd = 0) => {
    const ta = getTA();
    const pos = ta ? ta.selectionStart : text.length;
    const ins = isInsideHole(text, pos) ? rawExpr : `\${${rawExpr}}`;
    const next = text.slice(0, pos) + ins + text.slice(pos);
    emit(next);
    const caret = pos + ins.length - caretFromEnd;
    requestAnimationFrame(() => {
      const t = getTA();
      if (t) {
        t.focus();
        t.setSelectionRange(caret, caret);
      }
    });
  };

  // 可用的分类列表（根据上下文动态生成）
  const availableCategories = useMemo(() => {
    const cats: { key: Category; label: string }[] = [];
    if (datasets.length > 0) {
      cats.push({ key: 'field', label: '字段' });
    }
    if (loopBlocks.length > 0) {
      cats.push({ key: 'loop', label: '循环变量' });
    }
    if (params.length > 0) {
      cats.push({ key: 'param', label: '参数' });
    }
    cats.push({ key: 'agg', label: '聚合函数' });
    if (functions?.functions && functions.functions.length > 0) {
      cats.push({ key: 'func', label: '通用函数' });
    }
    return cats;
  }, [datasets, loopBlocks, params, functions]);

  const aggs = functions?.aggregations ?? FALLBACK_AGGS;
  const funcs = functions?.functions ?? [];

  // 二级列表内容
  const secondLevelItems = useMemo(() => {
    const keyword = search.trim();

    if (category === 'field') {
      const items: { expr: string; label: string; desc: string }[] = [];
      for (const ds of datasets) {
        const dsLabel = ds.alias || ds.id;
        for (const f of ds.fields) {
          const fLabel = f.alias || f.name;
          const displayLabel = `${dsLabel}.${fLabel}`;
          const desc = `${ds.id}.${f.name} · ${dataTypeLabel(f.dataType)}`;
          if (keyword && !matchWithPinyin(displayLabel, keyword) && !matchWithPinyin(desc, keyword)) {
            continue;
          }
          items.push({ expr: `${ds.id}.${f.name}`, label: displayLabel, desc });
        }
      }
      return items;
    }

    if (category === 'loop') {
      const items: { expr: string; label: string; desc: string }[] = [];
      for (const lb of loopBlocks) {
        const ds = findDataset(datasets, lb.source.datasetId);
        if (!ds) continue;
        const lbLabel = lb.label || lb.id;
        for (const f of ds.fields) {
          const fLabel = f.alias || f.name;
          const displayLabel = `${lbLabel}.${fLabel}`;
          const desc = `${lb.id}.${f.name} · ${dataTypeLabel(f.dataType)}`;
          if (keyword && !matchWithPinyin(displayLabel, keyword) && !matchWithPinyin(desc, keyword)) {
            continue;
          }
          items.push({ expr: `${lb.id}.${f.name}`, label: displayLabel, desc });
        }
      }
      return items;
    }

    if (category === 'param') {
      return params
        .filter((p) => {
          if (!keyword) return true;
          const label = p.alias || p.name;
          return matchWithPinyin(label, keyword) || matchWithPinyin(p.name, keyword);
        })
        .map((p) => ({
          expr: p.name,
          label: p.alias || p.name,
          desc: `\${${p.name}} · ${dataTypeLabel(p.dataType)}`,
        }));
    }

    if (category === 'agg') {
      return aggs
        .filter((a) => {
          if (!keyword) return true;
          return matchWithPinyin(a.name, keyword) ||
            matchWithPinyin(a.label, keyword) ||
            matchWithPinyin(a.description, keyword);
        })
        .map((a) => ({
          expr: `${a.name}()`,
          label: `${a.name} - ${a.label}`,
          desc: a.description,
          caretFromEnd: 2,
        }));
    }

    if (category === 'func') {
      return funcs
        .filter((f) => {
          if (!keyword) return true;
          return matchWithPinyin(f.name, keyword) ||
            matchWithPinyin(f.label, keyword) ||
            matchWithPinyin(f.description, keyword);
        })
        .map((f) => ({
          expr: `${f.name}()`,
          label: `${f.name} - ${f.label}`,
          desc: f.description,
          caretFromEnd: 2,
        }));
    }

    return [];
  }, [category, search, datasets, loopBlocks, params, aggs, funcs]);

  return (
    <div className="re-expr-builder">
      <Input.TextArea
        ref={taRef}
        value={text}
        autoSize={{ minRows: 2, maxRows: 6 }}
        onChange={(e) => emit(e.target.value)}
        placeholder={'例：部门${depart.name} 共 ${SUM(depart.salary)} 元；纯文本直接输入'}
      />

      <div className="re-expr-panel">
        {/* 一级菜单 */}
        <Menu
          mode="inline"
          selectedKeys={category ? [category] : []}
          onClick={({ key }) => {
            setCategory(key === category ? null : (key as Category));
            setSearch('');
          }}
          items={availableCategories.map((cat) => ({
            key: cat.key,
            label: cat.label,
          }))}
        />

        {/* 二级选择区 */}
        <div className="re-expr-selector">
          {category ? (
            <>
              <Input
                size="small"
                prefix={<SearchOutlined />}
                placeholder="搜索..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
              <List
                size="small"
                dataSource={secondLevelItems}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无匹配项" /> }}
                style={{ flex: 1, overflowY: 'auto' }}
                split={false}
                renderItem={(item, idx) => (
                  <List.Item
                    key={`${item.expr}-${idx}`}
                    onClick={() => doInsert(item.expr, (item as any).caretFromEnd || 0)}
                    style={{ cursor: 'pointer', borderRadius: 'var(--re-radius-base)', padding: '6px 12px', marginBottom: 2 }}
                  >
                    <List.Item.Meta
                      title={<span style={{ fontWeight: 500 }}>{item.label}</span>}
                      description={item.desc}
                    />
                  </List.Item>
                )}
              />
            </>
          ) : (
            <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <span style={{ color: 'var(--re-color-text-secondary)' }}>
                    请先选择左侧分类
                  </span>
                }
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ExpressionBuilder;
