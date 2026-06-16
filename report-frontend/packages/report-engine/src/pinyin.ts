/**
 * 拼音匹配工具（基于 tiny-pinyin）
 * 支持中文字符串的拼音首字母和全拼匹配
 */

import Pinyin from 'tiny-pinyin';

/** 获取字符串的拼音首字母序列 */
export function getPinyinInitials(str: string): string {
  const tokens = Pinyin.parse(str);
  return tokens
    .map((t) => {
      // type 2 = 中文转拼音, type 1 = 拉丁字母, type 3 = 未知
      const text = t.target || t.source;
      return text.charAt(0).toLowerCase();
    })
    .join('');
}

/** 获取字符串的完整拼音（无空格） */
export function getFullPinyin(str: string): string {
  return Pinyin.convertToPinyin(str, '', true);
}

/**
 * 检查文本是否匹配关键字（支持拼音首字母和全拼）
 * @param text 要匹配的文本
 * @param keyword 搜索关键字
 */
export function matchWithPinyin(text: string, keyword: string): boolean {
  if (!keyword) return true;

  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();

  // 1. 直接匹配原文
  if (lowerText.includes(lowerKeyword)) {
    return true;
  }

  // 2. 拼音首字母匹配
  const initials = getPinyinInitials(text);
  if (initials.includes(lowerKeyword)) {
    return true;
  }

  // 3. 完整拼音匹配
  const fullPinyin = getFullPinyin(text);
  if (fullPinyin.includes(lowerKeyword)) {
    return true;
  }

  return false;
}
