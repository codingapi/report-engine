import axios from 'axios';
import type { AxiosInstance } from 'axios';

export interface ApiResponse {
  success: boolean;
  errCode?: string;
  errMessage?: string;
}

export interface ApiSingleResponse<T> extends ApiResponse {
  data: T;
}

export interface ApiMultiResponse<T> extends ApiResponse {
  data: {
    total: number;
    list: T[];
  };
}

export interface ApiMapResponse extends ApiResponse {
  data: Record<string, unknown>;
}

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data !== null && typeof data === 'object' && 'success' in data) {
      if (!data.success) {
        return Promise.reject(new Error(data.errMessage || data.errCode || '请求失败'));
      }
      response.data = data.data;
    }
    return response;
  },
  (error) => {
    if (error.response) {
      console.error(`[API Error] ${error.response.status} ${error.response.statusText}`);
    } else if (error.request) {
      console.error('[API Error] 网络异常，请检查后端服务是否启动');
    }
    return Promise.reject(error);
  },
);

export default http;
