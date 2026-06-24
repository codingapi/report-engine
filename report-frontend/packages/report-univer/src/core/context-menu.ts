/**
 * 右键菜单管理
 * 从声明式 MenuGroupDef[] 构建 Univer 右键菜单
 */

import type { MenuGroupDef, CellRange } from '@/types';
import type { UniverAPI } from './setup';

// 模块级 ref：存储最新的菜单定义（action 回调通过此间接调用，避免闭包过期）
let currentMenuGroups: MenuGroupDef[] = [];

/** 更新模块级菜单定义引用 */
export function updateMenuGroups(groups: MenuGroupDef[]): void {
  currentMenuGroups = groups;
}

/** 查找指定 id 的菜单项的最新 onClick */
function findOnClick(itemId: string): ((range: CellRange) => void) | undefined {
  for (const group of currentMenuGroups) {
    for (const item of group.items) {
      if (item.id === itemId) return item.onClick;
    }
  }
  return undefined;
}

/** 获取当前活动区域的 CellRange */
function getActiveCellRange(univerAPI: UniverAPI): CellRange | null {
  const workbook = univerAPI.getActiveWorkbook();
  const sheet = workbook?.getActiveSheet();
  if (!sheet) return null;

  const activeRange = sheet.getActiveRange();
  if (!activeRange) return null;

  const sheetId = sheet.getSheetId?.() as string;
  const startRow = activeRange.getRow() as number;
  const startColumn = activeRange.getColumn() as number;
  const height = activeRange.getHeight() as number;
  const width = activeRange.getWidth() as number;

  return {
    sheetId,
    startRow,
    startColumn,
    endRow: startRow + height - 1,
    endColumn: startColumn + width - 1,
  };
}

/**
 * 从声明式定义构建并注册右键菜单
 * 只需调用一次，action 通过模块级 ref 间接调用最新回调
 */
export function buildContextMenus(univerAPI: UniverAPI, groups: MenuGroupDef[]): void {
  // 更新模块级引用
  currentMenuGroups = groups;

  for (const group of groups) {
    // 创建子菜单项
    const menus = group.items.map((item) =>
      univerAPI.createMenu({
        id: item.id,
        title: item.title,
        tooltip: item.tooltip,
        action: () => {
          // 通过间接引用调用最新的 onClick
          const onClick = findOnClick(item.id);
          if (!onClick) return;
          const range = getActiveCellRange(univerAPI);
          if (range) onClick(range);
        },
      }),
    );

    // 创建一级菜单并挂载子项
    const submenu = univerAPI.createSubmenu({
      id: group.id,
      title: group.title,
    });
    for (const menu of menus) {
      submenu.addSubmenu(menu);
    }
    submenu.appendTo(['contextMenu.mainArea', 'contextMenu.others']);
  }
}
