import { formatNumber, styleToCss, borderToCss } from '@/components/preview/workbook-table';

// ─── 纯函数层:数字格式化 / 样式映射 / 边框映射 ───

describe('formatNumber', () => {
  test('无格式 → 原样字符串', () => {
    expect(formatNumber(7)).toBe('7');
    expect(formatNumber(3.14)).toBe('3.14');
  });

  test('固定小数位', () => {
    expect(formatNumber(1234.5, '0.00')).toBe('1234.50');
    expect(formatNumber(1234.567, '0.0')).toBe('1234.6');
  });

  test('千分位', () => {
    expect(formatNumber(1234.5, '#,##0.00')).toBe('1,234.50');
    expect(formatNumber(1234, '#,##0')).toBe('1,234');
  });

  test('百分比(×100 + %)', () => {
    expect(formatNumber(0.5, '0%')).toBe('50%');
    expect(formatNumber(0.1234, '0.00%')).toBe('12.34%');
  });
});

describe('borderToCss', () => {
  test('undefined → undefined', () => {
    expect(borderToCss(undefined)).toBeUndefined();
  });

  test('线型 + 颜色 → CSS shorthand', () => {
    expect(borderToCss({ style: 'thin', color: '#ff0000' })).toBe('1px solid #ff0000');
    expect(borderToCss({ style: 'double', color: '#f00' })).toBe('3px double #f00');
    expect(borderToCss({ style: 'medium', color: '#000' })).toBe('2px solid #000');
  });

  test('缺省颜色回退 #000000', () => {
    expect(borderToCss({ style: 'thick' })).toBe('3px solid #000000');
  });
});

describe('styleToCss', () => {
  test('空样式 → 空对象', () => {
    expect(styleToCss(undefined)).toEqual({});
  });

  test('字体全属性映射(size 为 pt)', () => {
    const css = styleToCss({
      font: {
        family: 'Arial',
        size: 14,
        bold: true,
        italic: true,
        underline: true,
        strikethrough: true,
        color: '#ff0000',
      },
    });
    expect(css).toEqual({
      fontFamily: 'Arial',
      fontSize: '14pt',
      fontWeight: 'bold',
      fontStyle: 'italic',
      textDecoration: 'underline line-through',
      color: '#ff0000',
      whiteSpace: 'nowrap', // styleToCss 总设 whiteSpace(无 wrap 时为 nowrap)
    });
  });

  test('仅 underline 无 strikethrough → 单一装饰', () => {
    expect(styleToCss({ font: { underline: true } }).textDecoration).toBe('underline');
  });

  test('对齐 / 垂直对齐(middle 直通)/ 换行', () => {
    expect(styleToCss({ align: 'center' }).textAlign).toBe('center');
    expect(styleToCss({ valign: 'middle' }).verticalAlign).toBe('middle');
    expect(styleToCss({ valign: 'top' }).verticalAlign).toBe('top');
    expect(styleToCss({ wrap: true }).whiteSpace).toBe('pre-wrap');
    expect(styleToCss({}).whiteSpace).toBe('nowrap');
  });

  test('填充背景色', () => {
    expect(styleToCss({ fill: '#eeeeee' }).background).toBe('#eeeeee');
  });

  test('四边边框独立映射', () => {
    const css = styleToCss({
      borders: {
        top: { style: 'thin', color: '#111' },
        right: { style: 'medium', color: '#222' },
        bottom: { style: 'thick', color: '#333' },
        left: { style: 'double', color: '#444' },
      },
    });
    expect(css.borderTop).toBe('1px solid #111');
    expect(css.borderRight).toBe('2px solid #222');
    expect(css.borderBottom).toBe('3px solid #333');
    expect(css.borderLeft).toBe('3px double #444');
  });
});
