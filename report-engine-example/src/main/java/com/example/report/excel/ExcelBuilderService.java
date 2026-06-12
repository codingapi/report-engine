package com.example.report.excel;

import com.example.report.excel.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelBuilderService {

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

    public byte[] buildExcel(ExcelWorkbookDTO workbook) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            if (workbook.getSheets() != null) {
                for (ExcelSheetDTO sheetDTO : workbook.getSheets()) {
                    buildSheet(wb, sheetDTO);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build Excel", e);
        }
    }

    private void buildSheet(XSSFWorkbook wb, ExcelSheetDTO dto) {
        String sheetName = (dto.getName() != null && !dto.getName().isEmpty()) ? dto.getName() : "Sheet";
        Sheet sheet = wb.createSheet(sheetName);

        // 默认行高列宽
        sheet.setDefaultRowHeightInPoints(pixelsToPoints(dto.getDefaultRowHeight()));
        sheet.setDefaultColumnWidth((int) (dto.getDefaultColumnWidth() / 7.0));

        // 自定义行高
        if (dto.getRows() != null) {
            for (ExcelRowDTO r : dto.getRows()) {
                Row row = getOrCreateRow(sheet, r.getIndex());
                row.setHeightInPoints(pixelsToPoints(r.getHeight()));
                if (r.isHidden()) {
                    row.setZeroHeight(true);
                }
            }
        }

        // 自定义列宽
        if (dto.getColumns() != null) {
            for (ExcelColumnDTO c : dto.getColumns()) {
                sheet.setColumnWidth(c.getIndex(), pixelsToWidthUnits(c.getWidth()));
                if (c.isHidden()) {
                    sheet.setColumnHidden(c.getIndex(), true);
                }
            }
        }

        // 样式缓存（每个 sheet 独立缓存）
        Map<String, CellStyle> styleCache = new HashMap<>();

        // 写入单元格
        if (dto.getCells() != null) {
            for (ExcelCellDTO cellDTO : dto.getCells()) {
                buildCell(wb, sheet, cellDTO, styleCache);
            }
        }

        // 合并区域
        if (dto.getMerges() != null) {
            for (ExcelMergeDTO m : dto.getMerges()) {
                sheet.addMergedRegion(new CellRangeAddress(
                        m.getStartRow(),
                        m.getStartRow() + m.getRowSpan() - 1,
                        m.getStartCol(),
                        m.getStartCol() + m.getColSpan() - 1
                ));
            }
        }
    }

    private void buildCell(XSSFWorkbook wb, Sheet sheet, ExcelCellDTO dto, Map<String, CellStyle> styleCache) {
        Row row = getOrCreateRow(sheet, dto.getRow());
        Cell cell = row.createCell(dto.getCol());

        // 设置值
        if (dto.getValue() != null && !dto.getValue().isNull()) {
            if (dto.getValue().isNumber()) {
                cell.setCellValue(dto.getValue().doubleValue());
            } else if (dto.getValue().isBoolean()) {
                cell.setCellValue(dto.getValue().booleanValue());
            } else if (dto.getValue().isTextual()) {
                cell.setCellValue(dto.getValue().textValue());
            }
        }

        // 设置公式
        if (dto.getFormula() != null && !dto.getFormula().isEmpty()) {
            cell.setCellFormula(dto.getFormula());
        }

        // 设置富文本（覆盖普通值）
        if (dto.getRichText() != null) {
            XSSFRichTextString rts = buildRichText(wb, dto.getRichText());
            cell.setCellValue(rts);
        }

        // 设置样式
        if (dto.getStyle() != null) {
            CellStyle style = buildCellStyle(wb, dto.getStyle(), styleCache);
            cell.setCellStyle(style);
        }
    }

    private CellStyle buildCellStyle(XSSFWorkbook wb, ExcelStyleDTO dto, Map<String, CellStyle> cache) {
        String key = computeStyleKey(dto);
        return cache.computeIfAbsent(key, k -> createCellStyle(wb, dto));
    }

    private CellStyle createCellStyle(XSSFWorkbook wb, ExcelStyleDTO dto) {
        CellStyle style = wb.createCellStyle();

        // 字体
        if (dto.getFont() != null) {
            Font font = createFont(wb, dto.getFont());
            style.setFont(font);
        }

        // 水平对齐
        if (dto.getAlign() != null) {
            style.setAlignment(mapHorizontalAlignment(dto.getAlign()));
        }

        // 垂直对齐
        if (dto.getValign() != null) {
            style.setVerticalAlignment(mapVerticalAlignment(dto.getValign()));
        }

        // 自动换行
        if (Boolean.TRUE.equals(dto.getWrap())) {
            style.setWrapText(true);
        }

        // 旋转
        if (dto.getRotation() != null) {
            style.setRotation(dto.getRotation().shortValue());
        }

        // 背景填充
        if (dto.getFill() != null && !dto.getFill().isEmpty()) {
            XSSFColor color = parseColor(dto.getFill());
            if (color != null) {
                style.setFillForegroundColor(color);
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        }

        // 边框
        if (dto.getBorders() != null) {
            applyBorders(style, dto.getBorders());
        }

        // 数字格式
        if (dto.getNumberFormat() != null && !dto.getNumberFormat().isEmpty()) {
            DataFormat dataFormat = wb.createDataFormat();
            style.setDataFormat(dataFormat.getFormat(dto.getNumberFormat()));
        }

        // 内边距（仅 left 可近似映射为 indent）
        if (dto.getPadding() != null && dto.getPadding().getLeft() != null) {
            int indent = (int) (dto.getPadding().getLeft() / 7.0);
            if (indent > 0) {
                style.setIndention((short) indent);
            }
        }

        return style;
    }

    private Font createFont(XSSFWorkbook wb, ExcelFontDTO dto) {
        Font font = wb.createFont();
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
            font.setUnderline(Font.U_SINGLE);
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

    private XSSFRichTextString buildRichText(XSSFWorkbook wb, ExcelRichTextDTO rt) {
        XSSFRichTextString rts = new XSSFRichTextString(rt.getText());
        if (rt.getSegments() != null) {
            int offset = 0;
            for (ExcelRichTextSegmentDTO seg : rt.getSegments()) {
                int start = offset;
                int end = offset + seg.getText().length();
                if (seg.getStyle() != null && end > start) {
                    Font font = createFont(wb, seg.getStyle());
                    rts.applyFont(start, end, font);
                }
                offset = end;
            }
        }
        return rts;
    }

    private void applyBorders(CellStyle style, ExcelBordersDTO borders) {
        if (borders.getTop() != null) {
            applyBorder(style, borders.getTop(), "top");
        }
        if (borders.getRight() != null) {
            applyBorder(style, borders.getRight(), "right");
        }
        if (borders.getBottom() != null) {
            applyBorder(style, borders.getBottom(), "bottom");
        }
        if (borders.getLeft() != null) {
            applyBorder(style, borders.getLeft(), "left");
        }
    }

    private void applyBorder(CellStyle style, ExcelBorderDTO border, String side) {
        BorderStyle bs = BORDER_STYLE_MAP.getOrDefault(border.getStyle(), BorderStyle.THIN);
        XSSFColor color = parseColor(border.getColor());
        XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
        switch (side) {
            case "top" -> {
                xssfStyle.setBorderTop(bs);
                if (color != null) xssfStyle.setTopBorderColor(color);
            }
            case "right" -> {
                xssfStyle.setBorderRight(bs);
                if (color != null) xssfStyle.setRightBorderColor(color);
            }
            case "bottom" -> {
                xssfStyle.setBorderBottom(bs);
                if (color != null) xssfStyle.setBottomBorderColor(color);
            }
            case "left" -> {
                xssfStyle.setBorderLeft(bs);
                if (color != null) xssfStyle.setLeftBorderColor(color);
            }
        }
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    private static XSSFColor parseColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        if (clean.length() != 6) return null;
        byte[] rgb = new byte[3];
        rgb[0] = (byte) Integer.parseInt(clean.substring(0, 2), 16);
        rgb[1] = (byte) Integer.parseInt(clean.substring(2, 4), 16);
        rgb[2] = (byte) Integer.parseInt(clean.substring(4, 6), 16);
        return new XSSFColor(rgb, null);
    }

    private static float pixelsToPoints(double pixels) {
        return (float) (pixels * 72.0 / 96.0);
    }

    private static int pixelsToWidthUnits(double pixels) {
        return (int) (pixels * 256.0 / 7.0);
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

    private static Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private String computeStyleKey(ExcelStyleDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return String.valueOf(dto.hashCode());
        }
    }
}
