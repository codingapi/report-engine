package com.codingapi.report.excel;

import com.codingapi.report.excel.pojo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 导入器，将 .xlsx 文件解析为 {@link Workbook} JSON 模型。
 *
 * <p>与 {@link ExcelExporter} 互为逆操作：Exporter 将 JSON 模型构建为 .xlsx 字节流， Importer 将 .xlsx 字节流解析回 JSON
 * 模型。两者配合实现 Excel 数据的完整 round-trip。
 *
 * <p>解析能力覆盖：单元格值（字符串/数字/布尔/空值）、公式、富文本、样式（字体/对齐/边框/ 填充/旋转/换行/数字格式）、合并区域、自定义行高列宽、隐藏行列。
 *
 * @see Workbook
 * @see ExcelExporter
 */
public class ExcelImporter {

    /** POI BorderStyle 枚举 → 前端边框线型字符串的反向映射 */
    private static final String[] BORDER_STYLE_NAMES = new String[14];

    static {
        BORDER_STYLE_NAMES[BorderStyle.THIN.getCode()] = "thin";
        BORDER_STYLE_NAMES[BorderStyle.HAIR.getCode()] = "hair";
        BORDER_STYLE_NAMES[BorderStyle.DOTTED.getCode()] = "dotted";
        BORDER_STYLE_NAMES[BorderStyle.DASHED.getCode()] = "dashed";
        BORDER_STYLE_NAMES[BorderStyle.DASH_DOT.getCode()] = "dashDot";
        BORDER_STYLE_NAMES[BorderStyle.DASH_DOT_DOT.getCode()] = "dashDotDot";
        BORDER_STYLE_NAMES[BorderStyle.DOUBLE.getCode()] = "double";
        BORDER_STYLE_NAMES[BorderStyle.MEDIUM.getCode()] = "medium";
        BORDER_STYLE_NAMES[BorderStyle.MEDIUM_DASHED.getCode()] = "mediumDashed";
        BORDER_STYLE_NAMES[BorderStyle.MEDIUM_DASH_DOT.getCode()] = "mediumDashDot";
        BORDER_STYLE_NAMES[BorderStyle.MEDIUM_DASH_DOT_DOT.getCode()] = "mediumDashDotDot";
        BORDER_STYLE_NAMES[BorderStyle.SLANTED_DASH_DOT.getCode()] = "slantDashDot";
        BORDER_STYLE_NAMES[BorderStyle.THICK.getCode()] = "thick";
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从输入流解析 .xlsx 文件为 Workbook 模型。
     *
     * @param input .xlsx 文件输入流
     * @return 解析后的 Workbook 模型
     */
    public Workbook importFrom(InputStream input) {
        try (XSSFWorkbook wb = new XSSFWorkbook(input)) {
            return parseWorkbook(wb);
        } catch (IOException e) {
            throw new RuntimeException("Failed to import Excel", e);
        }
    }

    /**
     * 从字节数组解析 .xlsx 文件为 Workbook 模型。
     *
     * @param data .xlsx 文件字节内容
     * @return 解析后的 Workbook 模型
     */
    public Workbook importFrom(byte[] data) {
        return importFrom(new ByteArrayInputStream(data));
    }

    private Workbook parseWorkbook(XSSFWorkbook wb) {
        Workbook workbook = new Workbook();
        List<Sheet> sheets = new ArrayList<>();

        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sheets.add(parseSheet(wb.getSheetAt(i)));
        }

        workbook.setSheets(sheets);
        return workbook;
    }

    private Sheet parseSheet(XSSFSheet xssfSheet) {
        Sheet sheet = new Sheet();
        sheet.setId(xssfSheet.getSheetName());
        sheet.setName(xssfSheet.getSheetName());
        sheet.setRowCount(xssfSheet.getLastRowNum() + 1);
        int dataMaxCol = getMaxColumnCount(xssfSheet);
        sheet.setColumnCount(dataMaxCol);
        sheet.setDefaultRowHeight(
                ExcelExporter.pointsToPixels(xssfSheet.getDefaultRowHeightInPoints()));
        sheet.setDefaultColumnWidth(
                ExcelExporter.widthUnitsToPixels(xssfSheet.getDefaultColumnWidth()));

        // 解析合并区域
        List<Merge> merges = new ArrayList<>();
        for (int i = 0; i < xssfSheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = xssfSheet.getMergedRegion(i);
            Merge merge = new Merge();
            merge.setStartRow(region.getFirstRow());
            merge.setStartCol(region.getFirstColumn());
            merge.setRowSpan(region.getLastRow() - region.getFirstRow() + 1);
            merge.setColSpan(region.getLastColumn() - region.getFirstColumn() + 1);
            merges.add(merge);
        }
        sheet.setMerges(merges);

        // 解析自定义行高
        List<Row> rows = new ArrayList<>();
        double defaultHeight = sheet.getDefaultRowHeight();
        for (int i = 0; i <= xssfSheet.getLastRowNum(); i++) {
            org.apache.poi.ss.usermodel.Row poiRow = xssfSheet.getRow(i);
            if (poiRow != null) {
                double heightPx = ExcelExporter.pointsToPixels(poiRow.getHeightInPoints());
                boolean hidden = poiRow.getZeroHeight();
                if (Math.abs(heightPx - defaultHeight) > 0.5 || hidden) {
                    Row row = new Row();
                    row.setIndex(i);
                    row.setHeight(heightPx);
                    row.setHidden(hidden);
                    rows.add(row);
                }
            }
        }
        sheet.setRows(rows);

        // 解析自定义列宽
        List<Column> columns = new ArrayList<>();
        double defaultWidth = sheet.getDefaultColumnWidth();
        for (int i = 0; i < dataMaxCol; i++) {
            double widthPx = ExcelExporter.widthUnitsToPixels(xssfSheet.getColumnWidth(i));
            boolean hidden = xssfSheet.isColumnHidden(i);
            if (Math.abs(widthPx - defaultWidth) > 1 || hidden) {
                Column col = new Column();
                col.setIndex(i);
                col.setWidth(widthPx);
                col.setHidden(hidden);
                columns.add(col);
            }
        }
        sheet.setColumns(columns);

        // 解析单元格
        List<Cell> cells = new ArrayList<>();
        for (int r = 0; r <= xssfSheet.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row poiRow = xssfSheet.getRow(r);
            if (poiRow == null) continue;
            for (int c = 0; c < poiRow.getLastCellNum(); c++) {
                org.apache.poi.ss.usermodel.Cell poiCell = poiRow.getCell(c);
                if (poiCell == null) continue;
                Cell cell = parseCell(poiCell);
                if (cell != null) {
                    cells.add(cell);
                }
            }
        }
        sheet.setCells(cells);

        return sheet;
    }

    private Cell parseCell(org.apache.poi.ss.usermodel.Cell poiCell) {
        Cell cell = new Cell();
        cell.setRow(poiCell.getRowIndex());
        cell.setCol(poiCell.getColumnIndex());
        cell.setRef(getCellRef(poiCell));

        // 解析值
        CellType cellType = poiCell.getCellType();
        if (cellType == CellType.FORMULA) {
            cell.setFormula(poiCell.getCellFormula());
            // 对于公式单元格，也尝试设置缓存值
            CellType cachedType = poiCell.getCachedFormulaResultType();
            cell.setValue(parseCachedValue(poiCell, cachedType));
        } else {
            cell.setValue(parseValue(poiCell, cellType));
        }

        // 解析富文本
        if (cellType == CellType.STRING
                || (cellType == CellType.FORMULA
                        && poiCell.getCachedFormulaResultType() == CellType.STRING)) {
            XSSFRichTextString rts = (XSSFRichTextString) poiCell.getRichStringCellValue();
            if (rts != null && rts.numFormattingRuns() > 1) {
                cell.setRichText(parseRichText(rts));
            }
        }

        // 解析样式
        XSSFCellStyle xssfStyle = (XSSFCellStyle) poiCell.getCellStyle();
        Style style = parseStyle(xssfStyle);
        if (style != null) {
            cell.setStyle(style);
        }

        return cell;
    }

    private JsonNode parseValue(org.apache.poi.ss.usermodel.Cell poiCell, CellType type) {
        return switch (type) {
            case STRING -> new TextNode(poiCell.getStringCellValue());
            case NUMERIC -> new DoubleNode(poiCell.getNumericCellValue());
            case BOOLEAN -> BooleanNode.valueOf(poiCell.getBooleanCellValue());
            default -> NullNode.getInstance();
        };
    }

    private JsonNode parseCachedValue(org.apache.poi.ss.usermodel.Cell poiCell, CellType type) {
        return switch (type) {
            case STRING -> new TextNode(poiCell.getStringCellValue());
            case NUMERIC -> new DoubleNode(poiCell.getNumericCellValue());
            case BOOLEAN -> BooleanNode.valueOf(poiCell.getBooleanCellValue());
            default -> NullNode.getInstance();
        };
    }

    private RichText parseRichText(XSSFRichTextString rts) {
        RichText richText = new RichText();
        richText.setText(rts.getString());

        List<RichTextSegment> segments = new ArrayList<>();
        String fullText = rts.getString();
        int runs = rts.numFormattingRuns();
        int offset = 0;

        for (int i = 0; i < runs; i++) {
            int start = rts.getIndexOfFormattingRun(i);
            int end = (i + 1 < runs) ? rts.getIndexOfFormattingRun(i + 1) : fullText.length();

            // 如果分段起始前有无样式文本，先输出一个无样式段
            if (start > offset) {
                RichTextSegment seg = new RichTextSegment();
                seg.setText(fullText.substring(offset, start));
                segments.add(seg);
            }

            RichTextSegment seg = new RichTextSegment();
            seg.setText(fullText.substring(start, end));

            XSSFFont font = (XSSFFont) rts.getFontOfFormattingRun(i);
            if (font != null) {
                seg.setStyle(parseFont(font));
            }

            segments.add(seg);
            offset = end;
        }

        // 尾部无样式文本
        if (offset < fullText.length()) {
            RichTextSegment seg = new RichTextSegment();
            seg.setText(fullText.substring(offset));
            segments.add(seg);
        }

        richText.setSegments(segments);
        return richText;
    }

    private Style parseStyle(XSSFCellStyle xssfStyle) {
        Style style = new Style();
        boolean hasContent = false;

        // 字体
        XSSFFont xssfFont = xssfStyle.getFont();
        Font font = parseFont(xssfFont);
        if (font != null) {
            style.setFont(font);
            hasContent = true;
        }

        // 对齐
        HorizontalAlignment hAlign = xssfStyle.getAlignment();
        if (hAlign != HorizontalAlignment.GENERAL) {
            style.setAlign(mapHorizontalAlignment(hAlign));
            hasContent = true;
        }

        VerticalAlignment vAlign = xssfStyle.getVerticalAlignment();
        if (vAlign != VerticalAlignment.BOTTOM) {
            style.setValign(mapVerticalAlignment(vAlign));
            hasContent = true;
        }

        // 换行
        if (xssfStyle.getWrapText()) {
            style.setWrap(true);
            hasContent = true;
        }

        // 旋转
        short rotation = xssfStyle.getRotation();
        if (rotation != 0) {
            style.setRotation((int) rotation);
            hasContent = true;
        }

        // 背景填充
        if (xssfStyle.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            XSSFColor fillColor = xssfStyle.getFillForegroundXSSFColor();
            String fillHex = colorToHex(fillColor);
            if (fillHex != null) {
                style.setFill(fillHex);
                hasContent = true;
            }
        }

        // 边框
        Borders borders = parseBorders(xssfStyle);
        if (borders != null) {
            style.setBorders(borders);
            hasContent = true;
        }

        // 数字格式
        String dataFmt = xssfStyle.getDataFormatString();
        if (dataFmt != null && !"General".equals(dataFmt)) {
            style.setNumberFormat(dataFmt);
            hasContent = true;
        }

        // 缩进
        short indent = xssfStyle.getIndention();
        if (indent > 0) {
            Padding padding = new Padding();
            padding.setLeft((double) indent * 7.0);
            style.setPadding(padding);
            hasContent = true;
        }

        return hasContent ? style : null;
    }

    private Font parseFont(XSSFFont xssfFont) {
        Font font = new Font();
        boolean hasContent = false;

        String fontName = xssfFont.getFontName();
        if (fontName != null && !"Calibri".equals(fontName)) {
            font.setFamily(fontName);
            hasContent = true;
        }

        short fontSize = xssfFont.getFontHeightInPoints();
        if (fontSize != 11) {
            font.setSize((double) fontSize);
            hasContent = true;
        }

        if (xssfFont.getBold()) {
            font.setBold(true);
            hasContent = true;
        }

        if (xssfFont.getItalic()) {
            font.setItalic(true);
            hasContent = true;
        }

        if (xssfFont.getUnderline() != 0) {
            font.setUnderline(true);
            hasContent = true;
        }

        if (xssfFont.getStrikeout()) {
            font.setStrikethrough(true);
            hasContent = true;
        }

        XSSFColor fontColor = xssfFont.getXSSFColor();
        String colorHex = colorToHex(fontColor);
        if (colorHex != null && !"#000000".equals(colorHex)) {
            font.setColor(colorHex);
            hasContent = true;
        }

        return hasContent ? font : null;
    }

    private Borders parseBorders(XSSFCellStyle style) {
        BorderStyle top = style.getBorderTop();
        BorderStyle right = style.getBorderRight();
        BorderStyle bottom = style.getBorderBottom();
        BorderStyle left = style.getBorderLeft();

        if (top == BorderStyle.NONE
                && right == BorderStyle.NONE
                && bottom == BorderStyle.NONE
                && left == BorderStyle.NONE) {
            return null;
        }

        Borders borders = new Borders();
        borders.setTop(parseBorder(top, style.getTopBorderXSSFColor()));
        borders.setRight(parseBorder(right, style.getRightBorderXSSFColor()));
        borders.setBottom(parseBorder(bottom, style.getBottomBorderXSSFColor()));
        borders.setLeft(parseBorder(left, style.getLeftBorderXSSFColor()));
        return borders;
    }

    private Border parseBorder(BorderStyle bs, XSSFColor color) {
        if (bs == BorderStyle.NONE) return null;
        Border border = new Border();
        border.setStyle(mapBorderStyle(bs));
        border.setColor(colorToHex(color));
        return border;
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    private static String mapBorderStyle(BorderStyle bs) {
        int code = bs.getCode();
        if (code >= 0 && code < BORDER_STYLE_NAMES.length && BORDER_STYLE_NAMES[code] != null) {
            return BORDER_STYLE_NAMES[code];
        }
        return "thin";
    }

    private static String mapHorizontalAlignment(HorizontalAlignment hAlign) {
        return switch (hAlign) {
            case LEFT -> "left";
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "justify";
            case DISTRIBUTED -> "distributed";
            default -> null;
        };
    }

    private static String mapVerticalAlignment(VerticalAlignment vAlign) {
        return switch (vAlign) {
            case TOP -> "top";
            case CENTER -> "middle";
            case BOTTOM -> "bottom";
            default -> null;
        };
    }

    static String colorToHex(XSSFColor color) {
        if (color == null) return null;
        byte[] rgb = color.getRGB();
        if (rgb == null) return null;
        return String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }

    private static String getCellRef(org.apache.poi.ss.usermodel.Cell cell) {
        return new org.apache.poi.ss.util.CellReference(cell.getRowIndex(), cell.getColumnIndex())
                .formatAsString();
    }

    private static int getMaxColumnCount(XSSFSheet sheet) {
        int max = 0;
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
            if (row != null && row.getLastCellNum() > max) {
                max = row.getLastCellNum();
            }
        }
        return max;
    }
}
