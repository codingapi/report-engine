import { getPinyinInitials, getFullPinyin, matchWithPinyin } from '@/pinyin';

describe('getPinyinInitials', () => {
  test('中文取首字母', () => {
    expect(getPinyinInitials('中国')).toBe('zg');
    expect(getPinyinInitials('员工表')).toBe('ygb');
  });
  test('纯英文按 token 首字母（tiny-pinyin 对 ASCII 逐 token 处理）', () => {
    expect(getPinyinInitials('hello')).toBe('hello');
  });
});

describe('getFullPinyin', () => {
  test('中文全拼无空格', () => {
    expect(getFullPinyin('中国')).toBe('zhongguo');
    expect(getFullPinyin('员工表')).toBe('yuangongbiao');
  });
});

describe('matchWithPinyin', () => {
  test('空关键字 → 恒匹配', () => {
    expect(matchWithPinyin('任意', '')).toBe(true);
  });
  test('原文直接匹配（大小写不敏感）', () => {
    expect(matchWithPinyin('Hello World', 'world')).toBe(true);
    expect(matchWithPinyin('中国', '中')).toBe(true);
  });
  test('拼音首字母匹配', () => {
    expect(matchWithPinyin('中国', 'zg')).toBe(true);
    expect(matchWithPinyin('员工表', 'ygb')).toBe(true);
  });
  test('完整拼音匹配', () => {
    expect(matchWithPinyin('中国', 'zhongguo')).toBe(true);
    expect(matchWithPinyin('中国', 'zhong')).toBe(true);
  });
  test('都不匹配 → false', () => {
    expect(matchWithPinyin('中国', 'abc')).toBe(false);
    expect(matchWithPinyin('中国', 'xyz')).toBe(false);
  });
});
