/**
 * ToolFormatter - 챗봇 tool 결과용 표시 문자열 변환
 * 프론트 lib/format.ts, lib/units.ts와 같은 규칙 (화면과 챗봇의 표기가 같아야 함, 챗봇명세서 3.1)
 */
package com.example.inventory.tool;

import com.example.inventory.enums.Unit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class ToolFormatter {

    // static 메서드만 쓰는 유틸 클래스라 객체 생성을 막음
    private ToolFormatter() {}

    /**
     * 최소 단위 수량 → 표시 문자열
     * 절댓값 1000 이상이면 kg/L, 미만이면 g/ml, EA는 항상 "개"
     * 예: (5000, G) → "5kg", (1500, G) → "1.5kg", (500, G) → "500g", (80, EA) → "80개"
     */
    public static String formatQuantity(int value, Unit unit) {
        // 단위별 작은 단위 / 큰 단위 (EA는 큰 단위 없음) — units.ts의 DISPLAY_UNIT
        String base = switch (unit) {
            case EA -> "개";
            case G -> "g";
            case ML -> "ml";
        };
        String large = switch (unit) {
            case EA -> null;
            case G -> "kg";
            case ML -> "L";
        };

        // "#,##0.###": 천 단위 쉼표, 소수는 있을 때만 최대 3자리 (5.0 → "5", 1.5 → "1.5")
        // DecimalFormat은 스레드 안전하지 않아서 필드로 공유하지 않고 호출마다 새로 만듦
        DecimalFormat format = new DecimalFormat("#,##0.###");

        if (large != null && Math.abs(value) >= 1000) {
            return format.format(value / 1000.0) + large;
        }
        return format.format(value) + base;
    }

    /**
     * 최소 단위당 가격 → 표시 문자열
     * G/ML은 ×1000 해서 kg/L당, EA는 개당
     * 예: (15.5, G) → "15,500원/kg", (45, EA) → "45원/개"
     */
    public static String formatUnitPrice(BigDecimal basePrice, Unit unit) {
        // 가격 표시 배율과 기준 단위 — units.ts의 PRICE_FACTOR, PRICE_UNIT
        BigDecimal displayPrice = switch (unit) {
            case EA -> basePrice;
            case G, ML -> basePrice.multiply(BigDecimal.valueOf(1000));
        };
        String priceUnit = switch (unit) {
            case EA -> "개";
            case G -> "kg";
            case ML -> "L";
        };

        // 원 단위 정수로 표시. DecimalFormat 기본 반올림(HALF_EVEN) 대신
        // 프론트(Intl.NumberFormat)와 같은 사사오입(HALF_UP)으로 맞춤 (45.5원 → 46원)
        DecimalFormat format = new DecimalFormat("#,##0");
        format.setRoundingMode(RoundingMode.HALF_UP);

        return format.format(displayPrice) + "원/" + priceUnit;
    }
}
