package com.example.inventory.integration;

import com.example.inventory.enums.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    void STAFF는_상품등록이_403이고_입고는_가능하다() throws Exception {
        signUp("staff@test.com", "pass1234");
        Cookie[] staff = loginCookies("staff@test.com", "pass1234");
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);
        long productId = createProduct(admin, "원두", "SKU-STAFF", categoryId);

        mockMvc.perform(post("/api/products")
                        .cookie(staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("다른상품", "SKU-NO", categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/stock/in")
                        .cookie(staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockInJson(productId, 3)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/" + productId).cookie(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(3));
    }

    @Test
    void STAFF는_재고조정과_롤백이_403이다() throws Exception {
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);
        long productId = createProduct(admin, "원두", "SKU-ROLE", categoryId);
        long inId = stockIn(admin, productId, 5);

        signUp("staff@test.com", "pass1234");
        Cookie[] staff = loginCookies("staff@test.com", "pass1234");

        mockMvc.perform(post("/api/stock/adjustment")
                        .cookie(staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"actualQuantity":1,"reason":"실사","idempotencyKey":"%s"}
                                """.formatted(productId, UUID.randomUUID())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/stock/transactions/" + inId + "/rollback")
                        .cookie(staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"취소"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void 카테고리와_상품을_만들면_조회되고_같은_SKU는_409이다() throws Exception {
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);

        mockMvc.perform(get("/api/categories").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("음료"));

        long productId = createProduct(admin, "원두", "SKU-1", categoryId);
        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStock").value(0))
                .andExpect(jsonPath("$.costPrice").value(0));

        mockMvc.perform(post("/api/products")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("원두2", "SKU-1", categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void 입고_후_초과출고는_409이고_출고롤백하면_재고가_돌아온다() throws Exception {
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);
        long productId = createProduct(admin, "원두", "SKU-FLOW", categoryId);

        stockIn(admin, productId, 10);
        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(jsonPath("$.currentStock").value(10));

        mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":11,"reason":"과다출고","idempotencyKey":"%s"}
                                """.formatted(productId, UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        MvcResult out = mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":4,"reason":"판매","idempotencyKey":"%s"}
                                """.formatted(productId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        int outId = com.jayway.jsonpath.JsonPath.read(out.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(jsonPath("$.currentStock").value(6));

        mockMvc.perform(post("/api/stock/transactions/" + outId + "/rollback")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"오등록"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(jsonPath("$.currentStock").value(10));

        mockMvc.perform(get("/api/stock/transactions/" + outId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 같은_요청키로_출고하면_재고는_한_번만_줄어든다() throws Exception {
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);
        long productId = createProduct(admin, "원두", "SKU-IDEMPOTENT", categoryId);
        stockIn(admin, productId, 10);

        String body = """
                {"productId":%d,"quantity":4,"reason":"판매","idempotencyKey":"same-out"}
                """.formatted(productId);
        MvcResult first = mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        int firstId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        MvcResult second = mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        int secondId = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.id");

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(jsonPath("$.currentStock").value(6));
    }

    @Test
    void 같은_요청키로_다른_수량을_출고하면_409이다() throws Exception {
        Cookie[] admin = adminCookies();
        long categoryId = createCategory(admin, "음료", null);
        long productId = createProduct(admin, "원두", "SKU-KEY-MISMATCH", categoryId);
        stockIn(admin, productId, 10);

        mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":4,"reason":"판매","idempotencyKey":"mismatch-out"}
                                """.formatted(productId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/stock/out")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":5,"reason":"판매","idempotencyKey":"mismatch-out"}
                                """.formatted(productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/products/" + productId).cookie(admin))
                .andExpect(jsonPath("$.currentStock").value(6));
    }

    @Test
    void 없는_상품은_404이고_하위가_있는_카테고리와_거래가_있는_상품_삭제는_409이다() throws Exception {
        Cookie[] admin = adminCookies();

        mockMvc.perform(get("/api/products/99999").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        long parentId = createCategory(admin, "상위", null);
        createCategory(admin, "하위", parentId);
        mockMvc.perform(delete("/api/categories/" + parentId).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETE_CONFLICT"));

        long productId = createProduct(admin, "원두", "SKU-DEL", parentId);
        stockIn(admin, productId, 1);
        mockMvc.perform(delete("/api/products/" + productId).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELETE_CONFLICT"));
    }

    private Cookie[] adminCookies() throws Exception {
        createUser("admin@test.com", "pass1234", Role.ADMIN);
        return loginCookies("admin@test.com", "pass1234");
    }

    private long createCategory(Cookie[] cookies, String name, Long parentId) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .cookie(cookies)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"설명","parentId":%s}
                                """.formatted(name, parent)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long createProduct(Cookie[] cookies, String name, String sku, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .cookie(cookies)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, sku, categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long stockIn(Cookie[] cookies, long productId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/stock/in")
                        .cookie(cookies)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockInJson(productId, quantity)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String productJson(String name, String sku, long categoryId) {
        return """
                {"name":"%s","sku":"%s","categoryId":%d,"unit":"EA","sellingPrice":5000,"minStockLevel":1}
                """.formatted(name, sku, categoryId);
    }

    private static String stockInJson(long productId, int quantity) {
        return """
                {"productId":%d,"quantity":%d,"unitPrice":1000,"reason":"입고","idempotencyKey":"%s"}
                """.formatted(productId, quantity, UUID.randomUUID());
    }
}
