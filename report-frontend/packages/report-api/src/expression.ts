import http from './http';

// ============================================================
// Types（匹配后端 ExpressionController.ExpressionCatalog）
// ============================================================

/** 函数元信息 */
export interface FunctionMeta {
  name: string;
  label: string;
  params: string[];
  description: string;
}

/** 可用公式目录：聚合 + 函数（均使用 FunctionMeta 格式，由后端聚合/函数注册表自描述） */
export interface ExpressionCatalog {
  /** 可用聚合函数（含元信息，已排除内部占位 NONE） */
  aggregations: FunctionMeta[];
  /** 可用函数（含元信息） */
  functions: FunctionMeta[];
}

// ============================================================
// API
// ============================================================

/** 获取可用公式清单（聚合 + 函数），供表达式构建器渲染选择项 */
export async function fetchFunctions(): Promise<ExpressionCatalog> {
  const res = await http.get('/expression/functions');
  return res.data;
}
