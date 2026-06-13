import http from "./http.js";
async function fetchFonts() {
    const response = await http.get('/fonts/list');
    return response.data.list;
}
export { fetchFonts };
