import http from "./http.js";
async function exportExcel(workbook) {
    const response = await http.post('/excel/generate', workbook, {
        responseType: 'blob'
    });
    return response.data;
}
async function importExcel(file) {
    const formData = new FormData();
    formData.append('file', file);
    const response = await http.post('/excel/import', formData, {
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
    return response.data;
}
export { exportExcel, importExcel };
