import React, { useRef, useState } from 'react';
import { Input, Button, Select, Space, Tooltip } from 'antd';
import type { InputRef } from 'antd';
import type { ReportValue, Dataset, LoopBlock, ReportParam, ExpressionCatalog } from '../../types';
import { findDataset } from '../../types';
import { templateToString, parseTemplate } from '../../value-text';

interface ExpressionBuilderProps {
  value: ReportValue;
  datasets: Dataset[];
  loopBlocks: LoopBlock[];
  params?: ReportParam[];
  functions?: ExpressionCatalog;
  onChange: (value: ReportValue) => void;
}

type InsertMode = 'field' | 'loop' | 'param' | 'formula' | null;

/** 内置聚合（后端未提供时的兜底） */
const FALLBACK_AGGS = ['COUNT', 'COUNT_DISTINCT', 'SUM', 'AVG', 'MAX', 'MIN'];

/** 判断光标位置是否落在某个 ${…} 占位内部 */
function isInsideHole(text: string, pos: number): boolean {
  const re = /\$\{[^}]*\}/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    const innerStart = m.index + 2; // 跳过 "${"
    const innerEnd = m.index + m[0].length - 1; // "}" 的位置
    if (pos >= innerStart && pos <= innerEnd) return true;
  }
  return false;
}

/**
 * 表达式构建器：textarea 自由编辑 + 插入按钮（字段/循环变量/公式）+ 二级选择。
 * 文本用引用形式（${depart.name} / ${SUM(depart.salary)}），onChange 解析为 Value 节点。
 * 嵌套时（光标在 ${…} 内）插入裸表达式，不再重复套 ${}。
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
  const [mode, setMode] = useState<InsertMode>(null);
  const [fieldDs, setFieldDs] = useState<string>();
  const [loopId, setLoopId] = useState<string>();
  const taRef = useRef<InputRef>(null);

  const getTA = (): HTMLTextAreaElement | null => {
    // antd Input.TextArea 的底层 textarea
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (taRef.current as any)?.resizableTextArea?.textArea ?? null;
  };

  const emit = (next: string) => {
    setText(next);
    onChange(parseTemplate(next));
  };

  /** 在光标处插入表达式：caretFromEnd 让光标停在插入文本末尾前若干位（如括号内） */
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

  const aggs = functions?.aggregations ?? FALLBACK_AGGS;
  const funcs = functions?.functions ?? [];

  const loopDs = (() => {
    const lb = loopBlocks.find((l) => l.id === loopId);
    return lb ? findDataset(datasets, lb.source.datasetId) : null;
  })();

  return (
    <div className="re-expr-builder">
      <Input.TextArea
        ref={taRef}
        value={text}
        autoSize={{ minRows: 2, maxRows: 6 }}
        onChange={(e) => emit(e.target.value)}
        placeholder={'例：部门${depart.name} 共 ${SUM(depart.salary)} 元；纯文本直接输入'}
      />

      <div className="re-expr-toolbar">
        <Button size="small" type={mode === 'field' ? 'primary' : 'default'}
          onClick={() => setMode(mode === 'field' ? null : 'field')}>插入字段</Button>
        <Button size="small" type={mode === 'loop' ? 'primary' : 'default'}
          disabled={loopBlocks.length === 0}
          onClick={() => setMode(mode === 'loop' ? null : 'loop')}>插入循环变量</Button>
        <Button size="small" type={mode === 'param' ? 'primary' : 'default'}
          disabled={params.length === 0}
          onClick={() => setMode(mode === 'param' ? null : 'param')}>插入参数</Button>
        <Button size="small" type={mode === 'formula' ? 'primary' : 'default'}
          onClick={() => setMode(mode === 'formula' ? null : 'formula')}>插入公式</Button>
      </div>

      {mode === 'field' && (
        <div className="re-expr-picker">
          <Select size="small" placeholder="数据集" value={fieldDs} onChange={setFieldDs}
            style={{ width: '40%' }} showSearch
            options={datasets.map((d) => ({ value: d.id, label: d.alias || d.id }))} />
          <Select size="small" placeholder="选字段插入" value={undefined} disabled={!fieldDs}
            style={{ flex: 1, minWidth: 0 }} showSearch
            onChange={(f) => doInsert(`${fieldDs}.${f}`)}
            options={(findDataset(datasets, fieldDs || '')?.fields || []).map((f) => ({
              value: f.name, label: f.alias || f.name,
            }))} />
        </div>
      )}

      {mode === 'loop' && (
        <div className="re-expr-picker">
          <Select size="small" placeholder="循环块" value={loopId} onChange={setLoopId}
            style={{ width: '40%' }}
            options={loopBlocks.map((l) => ({ value: l.id, label: l.label || l.id }))} />
          <Select size="small" placeholder="选字段插入" value={undefined} disabled={!loopId}
            style={{ flex: 1, minWidth: 0 }} showSearch
            onChange={(f) => doInsert(`${loopId}.${f}`)}
            options={(loopDs?.fields || []).map((f) => ({
              value: f.name, label: f.alias || f.name,
            }))} />
        </div>
      )}

      {mode === 'param' && (
        <div className="re-expr-picker">
          <Select<string> size="small" placeholder="选参数插入" value={undefined}
            style={{ flex: 1, minWidth: 0 }} showSearch
            onChange={(name) => doInsert(name)}
            options={params.map((p) => ({ value: p.name, label: p.label || p.name }))} />
        </div>
      )}

      {mode === 'formula' && (
        <div className="re-expr-picker re-expr-picker--wrap">
          <div className="re-expr-group-label">聚合</div>
          <Space size={4} wrap>
            {aggs.map((a) => (
              <Button key={a} size="small" onClick={() => doInsert(`${a}()`, 2)}>{a}</Button>
            ))}
          </Space>
          {funcs.length > 0 && (
            <>
              <div className="re-expr-group-label">函数</div>
              <Space size={4} wrap>
                {funcs.map((f) => (
                  <Tooltip key={f.name} title={`${f.description}（参数：${f.params.join('、')}）`}>
                    <Button size="small" onClick={() => doInsert(`${f.name}()`, 2)}>{f.label}</Button>
                  </Tooltip>
                ))}
              </Space>
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default ExpressionBuilder;
