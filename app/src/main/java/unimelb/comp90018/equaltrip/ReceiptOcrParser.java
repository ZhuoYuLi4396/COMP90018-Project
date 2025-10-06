package unimelb.comp90018.equaltrip;

import java.util.regex.Matcher;   // 确保文件顶部有这个 import
import android.util.Log;
import java.util.Locale;
import java.util.regex.Pattern;


/**
 * Zhuoyu Li
 * ReceiptOcrParser
 * 用于解析从 ML Kit OCR 得到的小票文本。
 * 当前版本针对 Woolworths 收据优化。
 * 简单来说就是识图用的
 */
public class ReceiptOcrParser {

    public static class OcrResult {
        public String merchant;
        public String totalAmount;
        public String date;

        @Override
        public String toString() {
            return String.format(Locale.getDefault(),
                    "%s · %s · $%s",
                    merchant != null ? merchant : "?",
                    date != null ? date : "?",
                    totalAmount != null ? totalAmount : "?");
        }

        public boolean isEmpty() {
            return (merchant == null && totalAmount == null && date == null);
        }
    }

    /**
     * 主入口：解析整段 OCR 文本
     */
    public static OcrResult parse(String text) {
        if (text == null || text.trim().isEmpty()) return new OcrResult();

        OcrResult result = new OcrResult();

        // 统一成大写，去掉多余空格
//        String upper = text.toUpperCase(Locale.ROOT)
//                .replaceAll("[\\t]+", " ")
//                .replaceAll("[ ]{2,}", " ")
//                .replaceAll("\\$", " $"); // 确保 $ 与金额分隔

        String upper = text.toUpperCase(Locale.ROOT)
                .replaceAll("[\\t]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\$", Matcher.quoteReplacement(" $")); // ✅ 防止 group reference 错误


        // === 检测商家 ===
        if (upper.contains("WOOLWORTHS")) {
            result.merchant = "Woolworths";
            Log.d("ReceiptOcrParser", "Detected Woolworths receipt.");
        }

        // === 提取日期 ===
        // 支持 DD/MM/YYYY, DD/MM/YY, YYYY-MM-DD, YYYY/MM/DD
        Pattern datePattern = Pattern.compile(
                "\\b([0-3]?\\d[\\-/][0-1]?\\d[\\-/](?:\\d{2}|\\d{4}))\\b");
        Matcher dateMatcher = datePattern.matcher(upper);
        if (dateMatcher.find()) {
            result.date = normalizeDate(dateMatcher.group(1));
        }

        // === 提取金额 ===
        // 先尝试匹配 TOTAL 或 AMOUNT DUE
        Pattern totalPattern = Pattern.compile(
                "(TOTAL|AMOUNT\\s*DUE|PURCHASE)\\D{0,10}\\$?\\s*([0-9]+[\\.,][0-9]{2})");
        Matcher totalMatcher = totalPattern.matcher(upper);
        String amount = null;
        while (totalMatcher.find()) {
            amount = totalMatcher.group(2);
        }

        // 如果没匹配到关键词金额，尝试所有金额候选，取最大值
        if (amount == null) {
            Pattern anyAmount = Pattern.compile("\\$?\\s*([0-9]+[\\.,][0-9]{2})");
            Matcher anyM = anyAmount.matcher(upper);
            double max = -1;
            while (anyM.find()) {
                try {
                    double val = Double.parseDouble(anyM.group(1).replace(",", "."));
                    if (val > max && val < 10000) { // 简单过滤异常大数
                        max = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (max > 0) amount = String.format(Locale.US, "%.2f", max);
        }

        if (amount != null) {
            result.totalAmount = amount.replace(",", ".");
        }

        return result;
    }

    /**
     * 规范化日期格式成 yyyy-MM-dd
     */
    private static String normalizeDate(String raw) {
        try {
            String cleaned = raw.replaceAll("[^0-9]", "-");
            String[] parts = cleaned.split("-");
            if (parts.length == 3) {
                String p1 = parts[0], p2 = parts[1], p3 = parts[2];
                if (p1.length() == 4) { // yyyy-mm-dd
                    return String.format(Locale.US, "%04d-%02d-%02d",
                            Integer.parseInt(p1),
                            Integer.parseInt(p2),
                            Integer.parseInt(p3));
                } else { // dd-mm-yyyy or dd-mm-yy
                    int year = Integer.parseInt(p3);
                    if (year < 100) year += 2000;
                    return String.format(Locale.US, "%04d-%02d-%02d",
                            year,
                            Integer.parseInt(p2),
                            Integer.parseInt(p1));
                }
            }
        } catch (Exception e) {
            Log.w("ReceiptOcrParser", "normalizeDate failed: " + e.getMessage());
        }
        return raw;
    }
}

