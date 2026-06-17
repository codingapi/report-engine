package com.codingapi.report.excel;

import com.codingapi.report.excel.pojo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Excel 导出器，将 {@link Workbook} JSON 模型转换为 Apache POI 的 .xlsx 字节流。
 * <p>
 * 支持完整的样式映射：字体、对齐、边框（13 种线型）、填充、旋转、换行、数字格式、
 * 富文本分段样式、合并区域、自定义行高列宽、隐藏行列等。
 * </p>
 * <p>
 * 此类为纯 Java 实现，不依赖任何 Web 框架，可在任意 Java 环境中使用。
 * </p>
 *
 * @see Workbook
 * @see ExcelImporter
 */
public class ExcelExporter {

    /** 前端边框线型字符串 → POI BorderStyle 枚举的映射表 */
    private static final Map<String, BorderStyle> BORDER_STYLE_MAP = Map.ofEntries(
            Map.entry("thin", BorderStyle.THIN),
            Map.entry("hair", BorderStyle.HAIR),
            Map.entry("dotted", BorderStyle.DOTTED),
            Map.entry("dashed", BorderStyle.DASHED),
            Map.entry("dashDot", BorderStyle.DASH_DOT),
            Map.entry("dashDotDot", BorderStyle.DASH_DOT_DOT),
            Map.entry("double", BorderStyle.DOUBLE),
            Map.entry("medium", BorderStyle.MEDIUM),
            Map.entry("mediumDashed", BorderStyle.MEDIUM_DASHED),
            Map.entry("mediumDashDot", BorderStyle.MEDIUM_DASH_DOT),
            Map.entry("mediumDashDotDot", BorderStyle.MEDIUM_DASH_DOT_DOT),
            Map.entry("slantDashDot", BorderStyle.SLANTED_DASH_DOT),
            Map.entry("thick", BorderStyle.THICK)
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 Workbook 模型导出为 .xlsx 字节数组。
     *
     * @param workbook 工作簿模型
     * @return .xlsx 文件的字节内容
     */
    public byte[] export(Workbook workbook) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        export(workbook, out);
        return out.toByteArray();
    }

    /**
     * 将 Workbook 模型导出到指定的输出流。
     *
     * @param workbook   工作簿模型
     * @param outputStream 目标输出流（调用方负责关闭）
     */
    public void export(Workbook workbook, OutputStream outputStream) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            if (workbook.getSheets() != null) {
                for (Sheet sheetDTO : workbook.getSheets()) {
                    buildSheet(wb, sheetDTO);
                }
            }
            wb.write(outputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export Excel", e);
        }
    }

    private void buildSheet(XSSFWorkbook wb, Sheet dto) {
        String sheetName = (dto.getName() != null && !dto.getName().isEmpty()) ? dto.getName() : "Sheet";
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(sheetName);

        sheet.setDefaultRowHeightInPoints(pixelsToPoints(dto.getDefaultRowHeight()));
        sheet.setDefaultColumnWidth((int) (dto.getDefaultColumnWidth() / 7.0));

        if (dto.getRows() != null) {
            for (Row r : dto.getRows()) {
                org.apache.poi.ss.usermodel.Row row = getOrCreateRow(sheet, r.getIndex());
                row.setHeightInPoints(pixelsToPoints(r.getHeight()));
                if (r.isHidden()) {
                    row.setZeroHeight(true);
                }
            }
        }

        if (dto.getColumns() != null) {
            for (com.codingapi.report.excel.pojo.Column c : dto.getColumns()) {
                sheet.setColumnWidth(c.getIndex(), pixelsToWidthUnits(c.getWidth()));
                if (c.isHidden()) {
                    sheet.setColumnHidden(c.getIndex(), true);
                }
            }
        }

        Map<String, CellStyle> styleCache = new HashMap<>();

        if (dto.getCells() != null) {
            for (Cell cellDTO : dto.getCells()) {
                buildCell(wb, sheet, cellDTO, styleCache);
            }
        }

        if (dto.getMerges() != null) {
            for (Merge m : dto.getMerges()) {
                sheet.addMergedRegion(new CellRangeAddress(
                        m.getStartRow(),
                        m.getStartRow() + m.getRowSpan() - 1,
                        m.getStartCol(),
                        m.getStartCol() + m.getColSpan() - 1
                ));
            }
            // 合并区边框补全：锚点格的边框需铺到整个合并区周边，否则只在左上角画出残缺边框
            for (Merge m : dto.getMerges()) {
                applyMergeBorders(wb, sheet, dto, m, styleCache);
            }
        }
    }

    /**
     * 把合并区锚点格（左上格）的边框铺满整个合并区域：
     * <p>POI 中合并单元格只有左上格的样式生效，若边框只设在锚点格上，Excel 仅在该格四周描边，
     * 视觉上合并区边框残缺。此方法按位置把锚点边框分配到周边各格——顶边铺到首行各格、
     * 底边铺到末行各格、左/右边铺到首/末列各格——并保留 RGB 颜色与锚点的字体/填充。
     */
    private void applyMergeBorders(XSSFWorkbook wb, org.apache.poi.ss.usermodel.Sheet sheet,
                                   Sheet dto, Merge m, Map<String, CellStyle> styleCache) {
        int r1 = m.getStartRow();
        int r2 = m.getStartRow() + m.getRowSpan() - 1;
        int c1 = m.getStartCol();
        int c2 = m.getStartCol() + m.getColSpan() - 1;
        if (r1 == r2 && c1 == c2) {
            return; // 非真正的合并（1x1）
        }
        Cell anchor = findCell(dto, r1, c1);
        if (anchor == null || anchor.getStyle() == null || anchor.getStyle().getBorders() == null) {
            return;
        }
        Borders bd = anchor.getStyle().getBorders();
        if (bd.getTop() == null && bd.getBottom() == null && bd.getLeft() == null && bd.getRight() == null) {
            return;
        }
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                Borders edges = new Borders();
                if (r == r1) edges.setTop(bd.getTop());
                if (r == r2) edges.setBottom(bd.getBottom());
                if (c == c1) edges.setLeft(bd.getLeft());
                if (c == c2) edges.setRight(bd.getRight());
                if (edges.getTop() == null && edges.getBottom() == null
                        && edges.getLeft() == null && edges.getRight() == null) {
                    continue; // 内部格无周边边框
                }
                Style cellStyle = styleWithBorders(anchor.getStyle(), edges);
                org.apache.poi.ss.usermodel.Row row = getOrCreateRow(sheet, r);
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(buildCellStyle(wb, cellStyle, styleCache));
            }
        }
    }

    /** 复制一个 Style，但只保留指定的周边边框（字体/对齐/填充等其余样式保留，内部边框去除） */
    private static Style styleWithBorders(Style base, Borders edges) {
        Style s = new Style();
        s.setFont(base.getFont());
        s.setAlign(base.getAlign());
        s.setValign(base.getValign());
        s.setWrap(base.getWrap());
        s.setRotation(base.getRotation());
        s.setFill(base.getFill());
        s.setNumberFormat(base.getNumberFormat());
        s.setPadding(base.getPadding());
        s.setBorders(edges);
        return s;
    }

    private static Cell findCell(Sheet dto, int row, int col) {
        if (dto.getCells() == null) {
            return null;
        }
        for (Cell c : dto.getCells()) {
            if (c.getRow() == row && c.getCol() == col) {
                return c;
            }
        }
        return null;
    }

    private void buildCell(XSSFWorkbook wb, org.apache.poi.ss.usermodel.Sheet sheet, Cell dto, Map<String, CellStyle> styleCache) {
        org.apache.poi.ss.usermodel.Row row = getOrCreateRow(sheet, dto.getRow());
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(dto.getCol());

        if (dto.getValue() != null && !dto.getValue().isNull()) {
            if (dto.getValue().isNumber()) {
                cell.setCellValue(dto.getValue().doubleValue());
            } else if (dto.getValue().isBoolean()) {
                cell.setCellValue(dto.getValue().booleanValue());
            } else if (dto.getValue().isTextual()) {
                cell.setCellValue(dto.getValue().textValue());
            }
        }

        if (dto.getFormula() != null && !dto.getFormula().isEmpty()) {
            cell.setCellFormula(dto.getFormula());
        }

        if (dto.getRichText() != null) {
            XSSFRichTextString rts = buildRichText(wb, dto.getRichText());
            cell.setCellValue(rts);
        }

        if (dto.getStyle() != null) {
            CellStyle style = buildCellStyle(wb, dto.getStyle(), styleCache);
            cell.setCellStyle(style);
        }
    }

    private CellStyle buildCellStyle(XSSFWorkbook wb, Style dto, Map<String, CellStyle> cache) {
        String key = computeStyleKey(dto);
        return cache.computeIfAbsent(key, k -> createCellStyle(wb, dto));
    }

    private CellStyle createCellStyle(XSSFWorkbook wb, Style dto) {
        CellStyle style = wb.createCellStyle();

        if (dto.getFont() != null) {
            org.apache.poi.ss.usermodel.Font font = createFont(wb, dto.getFont());
            style.setFont(font);
        }

        if (dto.getAlign() != null) {
            style.setAlignment(mapHorizontalAlignment(dto.getAlign()));
        }

        if (dto.getValign() != null) {
            style.setVerticalAlignment(mapVerticalAlignment(dto.getValign()));
        }

        if (Boolean.TRUE.equals(dto.getWrap())) {
            style.setWrapText(true);
        }

        if (dto.getRotation() != null) {
            style.setRotation(dto.getRotation().shortValue());
        }

        if (dto.getFill() != null && !dto.getFill().isEmpty()) {
            XSSFColor color = parseColor(dto.getFill());
            if (color != null) {
                style.setFillForegroundColor(color);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        }

        if (dto.getBorders() != null) {
            applyBorders(style, dto.getBorders());
        }

        if (dto.getNumberFormat() != null && !dto.getNumberFormat().isEmpty()) {
            DataFormat dataFormat = wb.createDataFormat();
            style.setDataFormat(dataFormat.getFormat(dto.getNumberFormat()));
        }

        if (dto.getPadding() != null && dto.getPadding().getLeft() != null) {
            int indent = (int) (dto.getPadding().getLeft() / 7.0);
            if (indent > 0) {
                style.setIndention((short) indent);
            }
        }

        return style;
    }

    private org.apache.poi.ss.usermodel.Font createFont(XSSFWorkbook wb, Font dto) {
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        if (dto.getFamily() != null) {
            font.setFontName(dto.getFamily());
        }
        if (dto.getSize() != null) {
            font.setFontHeightInPoints(dto.getSize().shortValue());
        }
        if (Boolean.TRUE.equals(dto.getBold())) {
            font.setBold(true);
        }
        if (Boolean.TRUE.equals(dto.getItalic())) {
            font.setItalic(true);
        }
        if (Boolean.TRUE.equals(dto.getUnderline())) {
            font.setUnderline(org.apache.poi.ss.usermodel.Font.U_SINGLE);
        }
        if (Boolean.TRUE.equals(dto.getStrikethrough())) {
            font.setStrikeout(true);
        }
        if (dto.getColor() != null && !dto.getColor().isEmpty()) {
            XSSFColor color = parseColor(dto.getColor());
            if (color != null) {
                ((XSSFFont) font).setColor(color);
            }
        }
        return font;
    }

    private XSSFRichTextString buildRichText(XSSFWorkbook wb, RichText rt) {
        XSSFRichTextString rts = new XSSFRichTextString(rt.getText());
        if (rt.getSegments() != null) {
            int offset = 0;
            for (RichTextSegment seg : rt.getSegments()) {
                int start = offset;
                int end = offset + seg.getText().length();
                if (seg.getStyle() != null && end > start) {
                    org.apache.poi.ss.usermodel.Font font = createFont(wb, seg.getStyle());
                    rts.applyFont(start, end, font);
                }
                offset = end;
            }
        }
        return rts;
    }

    private void applyBorders(CellStyle style, Borders borders) {
        if (borders.getTop() != null) applyBorder(style, borders.getTop(), "top");
        if (borders.getRight() != null) applyBorder(style, borders.getRight(), "right");
        if (borders.getBottom() != null) applyBorder(style, borders.getBottom(), "bottom");
        if (borders.getLeft() != null) applyBorder(style, borders.getLeft(), "left");
    }

    private void applyBorder(CellStyle style, Border border, String side) {
        BorderStyle bs = BORDER_STYLE_MAP.getOrDefault(border.getStyle(), BorderStyle.THIN);
        XSSFColor color = parseColor(border.getColor());
        XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
        switch (side) {
            case "top" -> { xssfStyle.setBorderTop(bs); if (color != null) xssfStyle.setTopBorderColor(color); }
            case "right" -> { xssfStyle.setBorderRight(bs); if (color != null) xssfStyle.setRightBorderColor(color); }
            case "bottom" -> { xssfStyle.setBorderBottom(bs); if (color != null) xssfStyle.setBottomBorderColor(color); }
            case "left" -> { xssfStyle.setBorderLeft(bs); if (color != null) xssfStyle.setLeftBorderColor(color); }
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    static XSSFColor parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) return null;
        byte[] rgb = new byte[3];
        rgb[0] = (byte) Integer.parseInt(clean.substring(0, 2), 16);
        rgb[1] = (byte) Integer.parseInt(clean.substring(2, 4), 16);
        rgb[2] = (byte) Integer.parseInt(clean.substring(4, 6), 16);
        return new XSSFColor(rgb, null);
    }

    static float pixelsToPoints(double pixels) {
        return (float) (pixels * 72.0 / 96.0);
    }

    static int pixelsToWidthUnits(double pixels) {
        return (int) (pixels * 256.0 / 7.0);
    }

    /** 将 POI points 反向转换为像素值 */
    static double pointsToPixels(float points) {
        return points * 96.0 / 72.0;
    }

    /** 将 POI 列宽单位反向转换为像素值 */
    static double widthUnitsToPixels(int units) {
        return units * 7.0 / 256.0;
    }

    private static HorizontalAlignment mapHorizontalAlignment(String align) {
        return switch (align) {
            case "left" -> HorizontalAlignment.LEFT;
            case "center" -> HorizontalAlignment.CENTER;
            case "right" -> HorizontalAlignment.RIGHT;
            case "justify" -> HorizontalAlignment.JUSTIFY;
            case "distributed" -> HorizontalAlignment.DISTRIBUTED;
            default -> HorizontalAlignment.GENERAL;
        };
    }

    private static VerticalAlignment mapVerticalAlignment(String valign) {
        return switch (valign) {
            case "top" -> VerticalAlignment.TOP;
            case "middle" -> VerticalAlignment.CENTER;
            case "bottom" -> VerticalAlignment.BOTTOM;
            default -> VerticalAlignment.BOTTOM;
        };
    }

    private static org.apache.poi.ss.usermodel.Row getOrCreateRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex) {
        org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private String computeStyleKey(Style dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return String.valueOf(dto.hashCode());
        }
    }
}
