import { createCellHandle } from '@/core/cell-handle';

/** 构造 mock univerAPI：支持任意 sheetId 返回带 getRange spy 的 sheet */
function makeUniver(rangeSpy: Record<string, ReturnType<typeof rs.fn>>) {
    const sheet = { getRange: rs.fn(() => rangeSpy) };
    const workbook = { getSheetBySheetId: rs.fn(() => sheet) };
    return { getActiveWorkbook: rs.fn(() => workbook), Enum: undefined };
}

describe('createCellHandle', () => {
    test('暴露单元格元信息', () => {
        const api = makeUniver({});
        const handle = createCellHandle(api as never, 's1', 2, 3, 'D3');
        expect(handle.sheetId).toBe('s1');
        expect(handle.row).toBe(2);
        expect(handle.col).toBe(3);
        expect(handle.a1Notation).toBe('D3');
    });

    test('setValue 转发到 range', () => {
        const range = { setValue: rs.fn() };
        const handle = createCellHandle(makeUniver(range) as never, 's1', 0, 0, 'A1');
        handle.setValue('hello');
        expect(range.setValue).toHaveBeenCalledWith('hello');
    });

    test('样式写入方法转发到 range', () => {
        const range = {
            setFontColor: rs.fn(),
            setBackground: rs.fn(),
            setFontSize: rs.fn(),
            setFontWeight: rs.fn(),
            clearFormat: rs.fn(),
        };
        const handle = createCellHandle(makeUniver(range) as never, 's1', 0, 0, 'A1');
        handle.setFontColor('#FF0000');
        handle.setBackground('#00FF00');
        handle.setFontSize(14);
        handle.setFontWeight('bold');
        handle.clearFormat();
        expect(range.setFontColor).toHaveBeenCalledWith('#FF0000');
        expect(range.setBackground).toHaveBeenCalledWith('#00FF00');
        expect(range.setFontSize).toHaveBeenCalledWith(14);
        expect(range.setFontWeight).toHaveBeenCalledWith('bold');
        expect(range.clearFormat).toHaveBeenCalled();
    });

    test('setBorder：存在 Enum 时按方向/线型/颜色映射后调用 setBorder', () => {
        const range = { setBorder: rs.fn() };
        const api = {
            getActiveWorkbook: rs.fn(() => ({
                getSheetBySheetId: rs.fn(() => ({ getRange: rs.fn(() => range) })),
            })),
            Enum: { BorderType: { TOP: 1, RIGHT: 2, BOTTOM: 3, LEFT: 4 } },
        };
        const handle = createCellHandle(api as never, 's1', 0, 0, 'A1');
        handle.setBorder('bottom', 'thin', '#000000');
        expect(range.setBorder).toHaveBeenCalledWith(3, 1, '#000000');
    });

    test('setBorder：缺 Enum.BorderType 时不调用 setBorder', () => {
        const range = { setBorder: rs.fn() };
        const handle = createCellHandle(makeUniver(range) as never, 's1', 0, 0, 'A1');
        handle.setBorder('top', 'thin', '#000000');
        expect(range.setBorder).not.toHaveBeenCalled();
    });

    test('getStyle：从 cell 样式快照解析字体色/背景/字号/加粗', () => {
        const range = {
            getCellStyleData: rs.fn(() => ({
                cl: { rgb: '#111111' },
                bg: { rgb: '#EEEEEE' },
                fs: 16,
                bl: 1,
            })),
        };
        const handle = createCellHandle(makeUniver(range as never) as never, 's1', 0, 0, 'A1');
        expect(handle.getStyle()).toEqual({
            fontColor: '#111111',
            background: '#EEEEEE',
            fontSize: 16,
            bold: true,
        });
    });

    test('getStyle：bl 非 1 时不标记加粗；raw 为空返回空对象', () => {
        const range = { getCellStyleData: rs.fn(() => ({ bl: 0 })) };
        const handle = createCellHandle(makeUniver(range as never) as never, 's1', 0, 0, 'A1');
        expect(handle.getStyle()).toEqual({});
    });

    test('无 workbook 时 setValue 为空操作、getStyle 返回空对象', () => {
        const api = { getActiveWorkbook: rs.fn(() => null) };
        const range = { setValue: rs.fn(), getCellStyleData: rs.fn() };
        const handle = createCellHandle(api as never, 's1', 0, 0, 'A1');
        expect(() => handle.setValue('x')).not.toThrow();
        expect(range.setValue).not.toHaveBeenCalled();
        expect(handle.getStyle()).toEqual({});
    });

    test('无 sheet（sheetId 不匹配）时为空操作', () => {
        const api = {
            getActiveWorkbook: rs.fn(() => ({ getSheetBySheetId: rs.fn(() => null) })),
        };
        const range = { setValue: rs.fn() };
        const handle = createCellHandle(api as never, 'missing', 0, 0, 'A1');
        handle.setValue('x');
        expect(range.setValue).not.toHaveBeenCalled();
    });
});
