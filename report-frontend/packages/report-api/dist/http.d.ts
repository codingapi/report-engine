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
declare const http: AxiosInstance;
export default http;
