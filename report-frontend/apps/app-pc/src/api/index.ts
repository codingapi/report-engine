import axios from 'axios';
import type { AxiosInstance } from 'axios';

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  (response) => response,
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
