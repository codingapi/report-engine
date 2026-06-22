import React, { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from 'react';
import { Button, Drawer, Empty, Tabs, message } from 'antd';
import { ExportOutlined } from '@ant-design/icons';
import type {
  ExcelWorkbook, ExcelSheet, ExcelCell, ExcelStyle, ExcelBorder, ExcelBorderStyle,
} from '@coding-report/report-univer';
import type { RenderConfig, RenderService } from '../../types';
import ParamInputModal from '../param-input-modal';
import DrillModal from './drill-modal';
import { useReportPreview } from '../../hooks/use-report-preview';

export interface ReportPreviewHandle {
  /** 直接导出当前配置为 .xlsx（不经预览抽屉），返回 Promise 供调用方追踪 loading */
  exportXlsx: (config: RenderConfig) => Promise<void>;
}

export interface ReportPreviewProps {
  /** 渲染服务（桥接 report-api），由 app 注入 */
  renderService: RenderService;
  /**
   * 传入配置即触发网页预览：引用变化时重新预览。
   * 设计器点「预览」按钮传入当前编辑器配置；独立预览页加载配置后传入。
   */
  config?: RenderConfig | null;
  /** 预览 loading 变化回调（供调用方驱动按钮 loading 状态） */
  onPreviewingChange?: (loading: boolean) => void;
  /** 导出 loading 变化回调 */
  onExportingChange?: (loading: boolean) => void;
  /** 预览抽屉关闭回调：设计器可不传（仅关抽屉），独立预览页可借此返回上一页。 */
  onClose?: () => void;
}

/**
 * 报表预览能力组件：参数弹窗 → 渲染 → 预览抽屉 → 反查 → 抽屉内导出。
 * <p>由 report-engine 提供，设计器（报表配置界面）与独立预览页共用：
 * <ul>
 *   <li>声明式：传入 {@code config}（引用变化）即触发预览。</li>
 *   <li>命令式：通过 ref 调 {@link ReportPreviewHandle#exportXlsx} 直接导出。</li>
 * </ul>
 * 渲染函数由 app 注入（report-engine 不直接调 API）。
 * <p>预览抽屉内部用私有 {@link WorkbookTable} 把渲染后的工作簿画成 HTML 表格。
 */
const ReportPreview = forwardRef<ReportPreviewHandle, ReportPreviewProps>(
  ({ renderService, config, onPreviewingChange, onExportingChange, onClose }, ref) => {
    const [messageApi, messageContextHolder] = message.useMessage();
    const {
      openPreview, openExport, previewing, exporting,
      previewOpen, previewWorkbook, previewDrillable, exportingPreview,
      closePreview, exportFromPreview, drill,
      paramModalOpen, pendingParams, confirmParams, cancelParams,
      drillModalOpen, drillResult, drillLoading, closeDrill,
    } = useReportPreview({ renderService, messageApi, onClose });

    const lastPreviewedRef = useRef<RenderConfig | null>(null);

    // config 引用变化时触发预览（避免 strict mode 重复触发）
    useEffect(() => {
      if (!config || config === lastPreviewedRef.current) return;
      lastPreviewedRef.current = config;
      openPreview(config);
    }, [config, openPreview]);

    useEffect(() => { onPreviewingChange?.(previewing); }, [previewing, onPreviewingChange]);
    useEffect(() => { onExportingChange?.(exporting); }, [exporting, onExportingChange]);

    useImperativeHandle(ref, () => ({
      exportXlsx: (cfg: RenderConfig) => openExport(cfg),
    }), [openExport]);

    return (
      <>
        {messageContextHolder}

        <ParamInputModal
          params={pendingParams}
          open={paramModalOpen}
          onConfirm={confirmParams}
          onCancel={cancelParams}
        />

        <Drawer
          title="报表预览"
          placement="right"
          width="100%"
          open={previewOpen}
          onClose={closePreview}
          destroyOnHidden
          styles={{ body: { padding: 0, background: '#fff' } }}
          extra={
            <Button
              type="primary"
              icon={<ExportOutlined />}
              loading={exportingPreview}
              onClick={exportFromPreview}
            >
              导出报表
            </Button>
          }
        >
          <WorkbookTable
            workbook={previewWorkbook}
            drillable={previewDrillable}
            onDrill={drill}
          />
        </Drawer>

        <DrillModal
          open={drillModalOpen}
          loading={drillLoading}
          result={drillResult}
          onClose={closeDrill}
        />
      </>
    );
  },
);

ReportPreview.displayName = 'ReportPreview';

// ============================================================
// 私有：把渲染后的工作簿画成 HTML 表格（不单独导出）
// ============================================================

interface WorkbookTableProps {
  workbook?: ExcelWorkbook;
  /** 可反查的单元格坐标列表（"row:col" 格式） */
  drillable?: string[];
  /** 点击可反查单元格时的回调 */
  onDrill?: (row: number, col: number) => void;
}

// 边框线型 → CSS
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

const WorkbookTable: React.FC<WorkbookTableProps> = ({ workbook, drillable, onDrill }) => {
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
