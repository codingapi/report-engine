import React, { useMemo, useState } from 'react';
import { Empty, Tabs, Modal, Table } from 'antd';
import type {
  ExcelWorkbook, ExcelSheet, ExcelCell, ExcelStyle, ExcelBorder, ExcelBorderStyle,
} from '@coding-report/report-univer';

/**
 * 报表网页预览：把后端 {@code /api/report/preview} 返回的 {@link ExcelWorkbook}（渲染后的工作簿，
 * 同 .xlsx 导出的同一份数据）渲染为 HTML 表格。
 *
 * <p>纯 UI 组件，不调 API。样式从 {@link ExcelStyle} 机械映射为 CSS，合并区用 rowSpan/colSpan，
 * 行高列宽来自 sheet 的 rows/columns（缺省回退 default*）。多个非空 sheet 用 Tabs 切换，单个不显示 Tabs。
 */
export interface ReportPreviewProps {
  workbook?: ExcelWorkbook;
  /** 可反查的单元格坐标列表（"row:col" 格式） */
  drillable?: string[];
  /** 点击可反查单元格时的回调 */
  onDrill?: (row: number, col: number) => void;
}

// ─── 边框线型 → CSS ─────────────────────────────────────────
const BORDER_CSS: Record<ExcelBorderStyle, string> = {
  thin: '1px solid', hair: '1px solid', dotted: '1px dotted', dashed: '1px dashed',
  dashDot: '1px dashed', dashDotDot: '1px dashed', double: '3px double', medium: '2px solid',
  mediumDashed: '2px dashed', mediumDashDot: '2px dashed', mediumDashDotDot: '2px dashed',
  slantDashDot: '2px dashed', thick: '3px solid',
};

function borderToCss(b?: ExcelBorder): string | undefined {
  if (!b) return undefined;
  const base = BORDER_CSS[b.style] || '1px solid';
  return `${base} ${b.color || '#000000'}`;
}

function styleToCss(style?: ExcelStyle): React.CSSProperties {
  const css: React.CSSProperties = {};
  if (!style) return css;

  const f = style.font;
  if (f) {
    if (f.family) css.fontFamily = f.family;
    if (f.size) css.fontSize = `${f.size}pt`;
    if (f.bold) css.fontWeight = 'bold';
    if (f.italic) css.fontStyle = 'italic';
    const deco = [f.underline ? 'underline' : '', f.strikethrough ? 'line-through' : ''].filter(Boolean);
    if (deco.length) css.textDecoration = deco.join(' ');
    if (f.color) css.color = f.color;
  }
  if (style.align) css.textAlign = style.align as React.CSSProperties['textAlign'];
  if (style.valign) css.verticalAlign = style.valign === 'middle' ? 'middle' : style.valign;
  css.whiteSpace = style.wrap ? 'pre-wrap' : 'nowrap';
  if (style.fill) css.background = style.fill;

  const bd = style.borders;
  if (bd) {
    const t = borderToCss(bd.top); if (t) css.borderTop = t;
    const r = borderToCss(bd.right); if (r) css.borderRight = r;
    const b = borderToCss(bd.bottom); if (b) css.borderBottom = b;
    const l = borderToCss(bd.left); if (l) css.borderLeft = l;
  }
  return css;
}

/** 轻量数字格式化：覆盖常见模式（小数位 / 千分位 / 百分比），不追求 Excel 全量保真。 */
function formatNumber(value: number, fmt?: string): string {
  if (!fmt) return String(value);
  const isPercent = fmt.includes('%');
  const v = isPercent ? value * 100 : value;

  const dot = fmt.indexOf('.');
  let decimals = 0;
  if (dot >= 0) {
    decimals = (fmt.slice(dot + 1).match(/[0#]/g) || []).length;
  }
  const useGroup = fmt.includes(',');
  let s = useGroup
    ? v.toLocaleString('zh-CN', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
    : v.toFixed(decimals);
  if (isPercent) s += '%';
  return s;
}

function renderCellContent(cell?: ExcelCell): React.ReactNode {
  if (!cell) return null;
  const rt = cell.richText;
  if (rt && rt.segments && rt.segments.length) {
    return rt.segments.map((seg, i) => (
      <span key={i} style={styleToCss({ font: seg.style })}>{seg.text}</span>
    ));
  }
  const v = cell.value;
  if (v === null || v === undefined) return null;
  if (typeof v === 'number') return formatNumber(v, cell.style?.numberFormat);
  return String(v);
}

// 默认浅色网格线用 dotted：在 border-collapse 下，模板定义的实线/虚线边框线型优先级更高
// （solid/dashed > dotted），与单元格位置无关地压过基线，避免相邻格的浅色基线「吃掉」已定义边框
// （如薪资条第二条表头的 top 黑线被上一格下边框抢占而消失）。单元格自带边框仍逐边覆盖。
const BASE_TD_STYLE: React.CSSProperties = {
  padding: '3px 8px',
  overflow: 'hidden',
  verticalAlign: 'middle',
  boxSizing: 'border-box',
  border: '1px dotted #eeeeee',
};

// 反查格样式：蓝色文字 + 手型光标
const DRILLABLE_STYLE: React.CSSProperties = {
  color: '#1677ff',
  cursor: 'pointer',
};

/** 单个 sheet → 居中白纸卡片内的 HTML 表格 */
const SheetTable: React.FC<{ sheet: ExcelSheet; drillable?: Set<string>; onDrill?: (row: number, col: number) => void }> = ({ sheet, drillable, onDrill }) => {
  const model = useMemo(() => {
    const cellMap = new Map<string, ExcelCell>();
    let maxRow = 0;
    let maxCol = 0;
    for (const c of sheet.cells) {
      cellMap.set(`${c.row}:${c.col}`, c);
      if (c.row > maxRow) maxRow = c.row;
      if (c.col > maxCol) maxCol = c.col;
    }

    const merges = sheet.merges || [];
    const anchorSpan = new Map<string, { rowSpan: number; colSpan: number }>();
    const covered = new Set<string>();
    for (const m of merges) {
      anchorSpan.set(`${m.startRow}:${m.startCol}`, { rowSpan: m.rowSpan, colSpan: m.colSpan });
      const lastRow = m.startRow + m.rowSpan - 1;
      const lastCol = m.startCol + m.colSpan - 1;
      if (lastRow > maxRow) maxRow = lastRow;
      if (lastCol > maxCol) maxCol = lastCol;
      for (let r = m.startRow; r <= lastRow; r++) {
        for (let c = m.startCol; c <= lastCol; c++) {
          if (r === m.startRow && c === m.startCol) continue;
          covered.add(`${r}:${c}`);
        }
      }
    }

    const rowH = new Map<number, number>();
    const rowHidden = new Set<number>();
    for (const r of sheet.rows || []) {
      rowH.set(r.index, r.height);
      if (r.hidden) rowHidden.add(r.index);
    }
    const colW = new Map<number, number>();
    const colHidden = new Set<number>();
    for (const c of sheet.columns || []) {
      colW.set(c.index, c.width);
      if (c.hidden) colHidden.add(c.index);
    }

    return { cellMap, anchorSpan, covered, maxRow, maxCol, rowH, rowHidden, colW, colHidden };
  }, [sheet]);

  const {
    cellMap, anchorSpan, covered, maxRow, maxCol, rowH, rowHidden, colW, colHidden,
  } = model;

  return (
    <table style={{ borderCollapse: 'collapse', tableLayout: 'fixed', color: 'rgba(0, 0, 0, 0.88)' }}>
        <colgroup>
          {Array.from({ length: maxCol + 1 }, (_, c) => (
            <col
              key={c}
              style={{ width: `${colHidden.has(c) ? 0 : (colW.get(c) ?? sheet.defaultColumnWidth)}px` }}
            />
          ))}
        </colgroup>
        <tbody>
          {Array.from({ length: maxRow + 1 }, (_, r) => {
            if (rowHidden.has(r)) return null;
            const h = rowH.get(r) ?? sheet.defaultRowHeight;
            return (
              <tr key={r} style={{ height: `${h}px` }}>
                {Array.from({ length: maxCol + 1 }, (_, c) => {
                  const key = `${r}:${c}`;
                  if (covered.has(key)) return null;
                  const span = anchorSpan.get(key);
                  const cell = cellMap.get(key);
                  const isDrillable = drillable?.has(key) ?? false;
                  return (
                    <td
                      key={c}
                      rowSpan={span?.rowSpan}
                      colSpan={span?.colSpan}
                      style={{
                        ...BASE_TD_STYLE,
                        ...styleToCss(cell?.style),
                        ...(isDrillable ? DRILLABLE_STYLE : {}),
                      }}
                      onClick={isDrillable && onDrill ? () => onDrill(r, c) : undefined}
                    >
                      {renderCellContent(cell)}
                    </td>
                  );
                })}
              </tr>
            );
          })}
        </tbody>
    </table>
  );
};

// 白底铺满整个区域（宽高），表格从左上角自然排布；抽屉 body 负责滚动
const ROOT_STYLE: React.CSSProperties = { minHeight: '100%', background: '#fff' };
const PANE_STYLE: React.CSSProperties = { padding: 24 };

const ReportPreview: React.FC<ReportPreviewProps> = ({ workbook, drillable, onDrill }) => {
  const sheets = (workbook?.sheets || []).filter((s) => s.cells && s.cells.length > 0);
  const drillableSet = useMemo(() => new Set(drillable || []), [drillable]);

  if (sheets.length === 0) {
    return (
      <div style={{ ...ROOT_STYLE, display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 240 }}>
        <Empty description="无预览数据" />
      </div>
    );
  }

  if (sheets.length === 1) {
    return (
      <div style={ROOT_STYLE}>
        <div style={PANE_STYLE}>
          <SheetTable sheet={sheets[0]} drillable={drillableSet} onDrill={onDrill} />
        </div>
      </div>
    );
  }

  return (
    <div style={ROOT_STYLE}>
      <Tabs
        tabBarStyle={{
          position: 'sticky',
          top: 0,
          zIndex: 1,
          margin: 0,
          padding: '0 24px',
          background: '#fff',
        }}
        items={sheets.map((s) => ({
          key: s.id,
          label: s.name || '工作表',
          children: (
            <div style={PANE_STYLE}>
              <SheetTable sheet={s} drillable={drillableSet} onDrill={onDrill} />
            </div>
          ),
        }))}
      />
    </div>
  );
};

export default ReportPreview;
