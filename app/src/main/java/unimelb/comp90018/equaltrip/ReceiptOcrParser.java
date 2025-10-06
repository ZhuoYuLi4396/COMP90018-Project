package unimelb.comp90018.equaltrip;

import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReceiptOcrParser (extended)
 * - 保留 Woolworths 识别
 * - 新增 Coles / JB Hi-Fi / Chemist Warehouse / Kmart / Bunnings / ALDI /
 *   7-Eleven / Dan Murphy's / Officeworks / BIG W / Target / IKEA / 通用餐饮
 * - 更稳健的日期 & 金额解析
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

    // === 商家关键词（检测用，按优先级从高到低） ===
    private static final String[][] MERCHANT_KEYWORDS = new String[][]{
            // {规范名, 关键词1|关键词2|...}
            {"Woolworths", "WOOLWORTHS|WOOLIES"},
            {"Coles", "COLES"},
            {"JB Hi-Fi", "JB\\s*HI[-\\s]*FI|J\\s*B\\s*HI\\s*FI"},

            // 新增商家
            {"Chemist Warehouse", "CHEMIST\\s*WAREHOUSE|CHEMIST\\s*WH|CHEMIST\\s*WHSE"},
            {"Kmart", "KMART"},
            {"Bunnings", "BUNNINGS\\s*WAREHOUSE|BUNNINGS"},
            {"ALDI", "ALDI"},
            {"7-Eleven", "7\\s*[- ]?ELEVEN|SEVEN\\s*ELEVEN"},
            {"Dan Murphy's", "DAN\\s*MURPHY'?S|DAN\\s*MURPHYS"},
            {"Officeworks", "OFFICEWORKS"},
            {"BIG W", "BIG\\s*W"},
            {"Target", "TARGET\\b"},
            {"IKEA", "IKEA"},

            // 通用餐饮（有时商户名识别不到，用票据特征识别）
            {"Restaurant", "TAX\\s*INVOICE.*(TABLE|GUESTS)|DINE\\s*IN|SERVICE\\s*CHARGE|SERVER\\b|TABLE\\b|GUEST\\b"}
    };

    // === 每个商家自定义金额正则（更精确） ===
    // 注意：这些是“行优先”，先试专属，再试通用 TOTAL，再兜底最大金额
    private static final Map<String, Pattern[]> MERCHANT_TOTAL_PATTERNS = new HashMap<>();
    static {
        // Woolworths：常见 “TOTAL $xx.xx” 或 “AMOUNT DUE $xx.xx”
        MERCHANT_TOTAL_PATTERNS.put("Woolworths", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|PURCHASE|BALANCE)\\D{0,15}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        // Coles：常见 “TOTAL AUD $xx.xx”、“TOTAL $xx.xx”、有时行尾带 “GST”
        MERCHANT_TOTAL_PATTERNS.put("Coles", new Pattern[]{
                Pattern.compile("\\bTOTAL\\s*(?:AUD)?\\s*\\$?\\s*([0-9]+[\\.,][0-9]{2})"),
                Pattern.compile("\\bAMOUNT\\s*DUE\\b\\D{0,15}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        // JB Hi-Fi：常见 “TOTAL $xx.xx”、“AMOUNT DUE $xx.xx”，票头常有 “TAX INVOICE”
        MERCHANT_TOTAL_PATTERNS.put("JB Hi-Fi", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|BALANCE\\s*DUE)\\b\\D{0,15}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        // 通用餐饮：更偏“SUBTOTAL / SERVICE CHARGE / TIP / TOTAL”
        MERCHANT_TOTAL_PATTERNS.put("Restaurant", new Pattern[]{
                // 有时行内会出现 “TOTAL (INCL GST) $xx.xx”
                Pattern.compile("\\bTOTAL(?:\\s*INCL\\s*GST)?\\b\\D{0,15}\\$?\\s*([0-9]+[\\.,][0-9]{2})"),
                // 有时 “AMOUNT DUE $xx.xx”
                Pattern.compile("\\bAMOUNT\\s*DUE\\b\\D{0,15}\\$?\\s*([0-9]+[\\.,][0-9]{2})"),
        });

        // 新增商家
        MERCHANT_TOTAL_PATTERNS.put("Chemist Warehouse", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|BALANCE)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})"),
                Pattern.compile("\\bGST\\s*INCL\\b.*?\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("Kmart", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|GRAND\\s*TOTAL)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("Bunnings", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|INVOICE\\s*TOTAL)\\b\\D{0,20}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("ALDI", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|CARD\\s*TOTAL|CASH\\s*TOTAL)\\b\\D{0,20}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("7-Eleven", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE)\\b\\D{0,16}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("Dan Murphy's", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|GRAND\\s*TOTAL)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("Officeworks", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|BALANCE\\s*DUE)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("BIG W", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE)\\b\\D{0,16}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("Target", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|PURCHASE\\s*TOTAL)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });

        MERCHANT_TOTAL_PATTERNS.put("IKEA", new Pattern[]{
                Pattern.compile("\\b(TOTAL|AMOUNT\\s*DUE|CARD\\s*TOTAL)\\b\\D{0,18}\\$?\\s*([0-9]+[\\.,][0-9]{2})")
        });
    }

    // === 通用 TOTAL 关键字（所有商家都试） ===
    private static final Pattern GENERIC_TOTAL_LINE = Pattern.compile(
            "\\b(TOTAL|AMOUNT\\s*DUE|BALANCE\\s*DUE|GRAND\\s*TOTAL|CARD\\s*TOTAL|CASH\\s*TOTAL)\\b\\D{0,20}\\$?\\s*([0-9]+[\\.,][0-9]{2})"
    );

    // === “任意金额兜底” ===
    private static final Pattern ANY_AMOUNT = Pattern.compile("\\$?\\s*([0-9]+[\\.,][0-9]{2})");

    // === 日期：扩充更多格式 ===
    // 1) 12/09/2025, 12-9-25, 2025-09-12
    private static final Pattern DATE_DMY_OR_YMD = Pattern.compile(
            "\\b([0-3]?\\d[\\-/][0-1]?\\d[\\-/](?:\\d{2}|\\d{4})|\\d{4}[\\-/][0-1]?\\d[\\-/][0-3]?\\d)\\b");
    // 2) 12 Sep 2025 / Sep 12, 2025 / 12 SEP 25（英文月份）
    private static final Pattern DATE_WITH_MONTH_NAME = Pattern.compile(
            "\\b([0-3]?\\d\\s*[A-Z]{3,9}\\s*\\d{2,4}|[A-Z]{3,9}\\s*[0-3]?\\d,?\\s*\\d{2,4})\\b");

    /**
     * 主入口
     */
    public static OcrResult parse(String text) {
        OcrResult result = new OcrResult();
        if (text == null || text.trim().isEmpty()) return result;

        // 统一预处理：大写、压缩空白、$ 与金额之间补空格等
        String upper = text.toUpperCase(Locale.ROOT)
                .replaceAll("[\\t]+", " ")
                .replaceAll("[ ]{2,}", " ")
                .replaceAll("\\$", Matcher.quoteReplacement(" $"));

        // 1) 检测商家（优先）
        String merchant = detectMerchant(upper);
        if (merchant != null) {
            result.merchant = merchant;
            Log.d("ReceiptOcrParser", "Detected merchant: " + merchant);
        } else if (upper.contains("TAX INVOICE") || upper.contains("ABN")) {
            // 澳式小票经常带 TAX INVOICE/ABN，未识别到具体商户时可留空或标 generic
            result.merchant = null; // 不乱填
        }

        // 2) 提取日期（多种格式）
        String date = extractDate(upper);
        if (date != null) result.date = date;

        // 3) 提取总金额（优先商家专属 → 通用 TOTAL → 兜底最大金额）
        String amount = extractTotalAmount(upper, merchant);
        if (amount != null) result.totalAmount = amount;

        return result;
    }

    // ========== 辅助函数 ==========

    private static String detectMerchant(String upper) {
        for (String[] row : MERCHANT_KEYWORDS) {
            String norm = row[0];
            String regexUnion = row[1];
            if (Pattern.compile(regexUnion).matcher(upper).find()) return norm;
        }
        return null;
    }

    private static String extractTotalAmount(String upper, String merchant) {
        // 3.1 商家专属正则
        if (merchant != null && MERCHANT_TOTAL_PATTERNS.containsKey(merchant)) {
            for (Pattern p : MERCHANT_TOTAL_PATTERNS.get(merchant)) {
                Matcher m = p.matcher(upper);
                String last = null;
                // 兼容 (关键词)(金额) 与仅(金额) 两种捕获形式
                while (m.find()) {
                    String candidate = (m.groupCount() >= 2) ? m.group(2) : m.group(1);
                    if (candidate != null) last = candidate;
                }
                if (last != null) return normAmount(last);
            }
        }

        // 3.2 通用 TOTAL 行
        Matcher totalM = GENERIC_TOTAL_LINE.matcher(upper);
        String last = null;
        while (totalM.find()) last = totalM.group(2);
        if (last != null) return normAmount(last);

        // 3.3 兜底：所有金额里取最大（排除异常大数）
        Matcher any = ANY_AMOUNT.matcher(upper);
        double max = -1; String best = null;
        while (any.find()) {
            String raw = any.group(1);
            try {
                double v = parseAmountToDouble(raw);
                if (v > max && v < 200000) { // 给 JB/电子类放宽上限；仍做基本防呆
                    max = v; best = raw;
                }
            } catch (Exception ignored) {}
        }
        return best != null ? normAmount(best) : null;
    }

    /** 将任意金额字符串解析为 US 格式 0.00 字符串 */
    private static String normAmount(String s) {
        try {
            double v = parseAmountToDouble(s);
            return String.format(Locale.US, "%.2f", v);
        } catch (Exception ignore) {
            return s.replace(",", "."); // 兜底
        }
    }

    /** 健壮金额解析：兼容 "1,234.56"、"1234,56"、"1234.5"、"1234" */
    private static double parseAmountToDouble(String raw) {
        if (raw == null) throw new NumberFormatException("null");
        // 只保留数字和 , .
        String s = raw.replaceAll("[^0-9,\\.]", "");

        boolean hasComma = s.indexOf(',') >= 0;
        boolean hasDot   = s.indexOf('.') >= 0;

        if (hasComma && hasDot) {
            // 同时存在：默认逗号是千分位，去掉逗号
            s = s.replace(",", "");
        } else if (hasComma) {
            // 只有逗号：按小数点处理
            s = s.replace(",", ".");
        }
        return Double.parseDouble(s);
    }

    private static String extractDate(String upper) {
        // 先试数字型
        Matcher m1 = DATE_DMY_OR_YMD.matcher(upper);
        if (m1.find()) {
            return normalizeDate(m1.group(1));
        }
        // 再试英文月份
        Matcher m2 = DATE_WITH_MONTH_NAME.matcher(upper);
        if (m2.find()) {
            return normalizeMonthNameDate(m2.group(1));
        }
        return null;
    }

    /** 规范化：12/9/25、12-09-2025、2025/09/12 → yyyy-MM-dd */
    private static String normalizeDate(String raw) {
        try {
            String cleaned = raw.replaceAll("[^0-9]", "-");
            String[] parts = cleaned.split("-");
            if (parts.length == 3) {
                String a = parts[0], b = parts[1], c = parts[2];
                if (a.length() == 4) { // yyyy-mm-dd
                    return String.format(Locale.US, "%04d-%02d-%02d",
                            i(a), i(b), i(c));
                } else {               // dd-mm-yyyy / dd-mm-yy
                    int year = i(c);
                    if (year < 100) year += 2000;
                    return String.format(Locale.US, "%04d-%02d-%02d",
                            year, i(b), i(a));
                }
            }
        } catch (Exception e) {
            Log.w("ReceiptOcrParser", "normalizeDate failed: " + e.getMessage());
        }
        return raw;
    }

    /** 规范化：12 Sep 2025 / Sep 12, 2025 / 12 SEPTEMBER 25 → yyyy-MM-dd */
    private static String normalizeMonthNameDate(String raw) {
        try {
            String s = raw.replace(",", " ").replaceAll("\\s+", " ").trim();
            String[] t = s.split(" ");
            String day, monStr, yearStr;

            if (isMonth(t[0])) { // Mon DD YYYY
                monStr = t[0]; day = t[1]; yearStr = t.length >= 3 ? t[2] : "2000";
            } else {            // DD Mon YYYY
                day = t[0]; monStr = t[1]; yearStr = t.length >= 3 ? t[2] : "2000";
            }
            int mon = monthToInt(monStr);
            int year = Integer.parseInt(yearStr);
            if (year < 100) year += 2000;

            return String.format(Locale.US, "%04d-%02d-%02d", year, mon, Integer.parseInt(day));
        } catch (Exception e) {
            Log.w("ReceiptOcrParser", "normalizeMonthNameDate failed: " + e.getMessage());
            return raw;
        }
    }

    private static boolean isMonth(String s) {
        return monthToInt(s) != 0;
    }

    private static int monthToInt(String s) {
        String x = s.toUpperCase(Locale.ROOT);
        if (x.startsWith("JAN")) return 1;
        if (x.startsWith("FEB")) return 2;
        if (x.startsWith("MAR")) return 3;
        if (x.startsWith("APR")) return 4;
        if (x.startsWith("MAY")) return 5;
        if (x.startsWith("JUN")) return 6;
        if (x.startsWith("JUL")) return 7;
        if (x.startsWith("AUG")) return 8;
        if (x.startsWith("SEP")) return 9;
        if (x.startsWith("OCT")) return 10;
        if (x.startsWith("NOV")) return 11;
        if (x.startsWith("DEC")) return 12;
        return 0;
    }

    private static int i(String s) { return Integer.parseInt(s); }
}
