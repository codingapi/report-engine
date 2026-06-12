import axios from 'axios';
import type { AxiosInstance } from 'axios';

// ─── 标准响应结构（对应后端 com.codingapi.springboot.framework.dto.response）───

/** 基础响应（无数据） */
export interface ApiResponse {
  success: boolean;
  errCode?: string;
  errMessage?: string;
}

/** 单对象响应 */
export interface ApiSingleResponse<T> extends ApiResponse {
  data: T;
}

/** 列表响应 */
export interface ApiMultiResponse<T> extends ApiResponse {
  data: {
    total: number;
    list: T[];
  };
}

/** Map 响应 */
export interface ApiMapResponse extends ApiResponse {
  data: Record<string, unknown>;
}

// ─── axios 实例 ──────────────────────────────────────────────

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 响应拦截器：解包标准响应结构 + 错误处理
http.interceptors.response.use(
  (response) => {
    const data = response.data;

    // JSON 响应：解包 { success, data, errCode, errMessage }
    if (data !== null && typeof data === 'object' && 'success' in data) {
      if (!data.success) {
        return Promise.reject(new Error(data.errMessage || data.errCode || '请求失败'));
      }
      // 将 response.data 替换为解包后的业务数据
      response.data = data.data;
    }

    // 非 JSON 响应（Blob 文件下载等）：直接透传
    return response;
  },
  (error) => {
    if (error.response) {
      const { status, statusText } = error.response;
      console.error(`[API Error] ${status} ${statusText}`, error.response.data);
    } else if (error.request) {
      console.error('[API Error] 网络异常，请检查后端服务是否启动');
    }
    return Promise.reject(error);
  },
);

export default http;
