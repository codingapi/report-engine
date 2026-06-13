import axios from "axios";
const http = axios.create({
    baseURL: '/api',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
});
http.interceptors.response.use((response)=>{
    const data = response.data;
    if (null !== data && 'object' == typeof data && 'success' in data) {
        if (!data.success) return Promise.reject(new Error(data.errMessage || data.errCode || '请求失败'));
        response.data = data.data;
    }
    return response;
}, (error)=>{
    if (error.response) console.error(`[API Error] ${error.response.status} ${error.response.statusText}`);
    else if (error.request) console.error('[API Error] 网络异常，请检查后端服务是否启动');
    return Promise.reject(error);
});
const src_http = http;
export default src_http;
