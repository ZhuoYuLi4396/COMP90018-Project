package unimelb.comp90018.equaltrip;

import java.util.List;
import java.util.Map;
import com.google.firebase.Timestamp;

public class Bill {
    public String id;
    public String title;
    public String billName;  // Firestore 字段名
    public String merchant;
    public String category;
    public String placeName;
    public String address;
    public String location;  // Firestore 字段名
    public String note;
    public Timestamp date;
    public Timestamp createdAt;
    public double amount;
    public String currency;
    public String currencyCode; // e.g. "AUD"

    // ⚠️ 关键修改：Firestore 中是 paidBy，不是 payerId
    public String paidBy;      // Firestore 字段名
    public String payerId;     // 为了兼容性保留
    public String payerUid;    // 为了兼容性保留

    public Double lat;
    public Double lng;
    public Map<String, Object> geo;  // Firestore 中是 geo: {lat, lon}

    public String receiptUrl;
    public List<String> receiptUrls;  // 多张收据

    public Long dateMs;
    public List<String> participants;       // uids
    public Map<String, Long> splits;        // uid -> cents
    public List<Map<String, Object>> debts; // debts 数组
    public Long totalCents;

    public List<String> receiptsBase64;

    public Bill() {}

    // 获取付款人 UID 的辅助方法
    public String getPayerUid() {
        if (paidBy != null) return paidBy;
        if (payerUid != null) return payerUid;
        if (payerId != null) return payerId;
        return null;
    }

    // 获取标题的辅助方法
    public String getTitle() {
        if (billName != null && !billName.isEmpty()) return billName;
        if (title != null && !title.isEmpty()) return title;
        return "Untitled Bill";
    }

    // 获取位置的辅助方法
    public String getLocation() {
        if (location != null && !location.isEmpty()) return location;
        if (address != null && !address.isEmpty()) return address;
        if (placeName != null && !placeName.isEmpty()) return placeName;
        return "";
    }

    // 从 geo 对象获取经纬度
    public Double getLat() {
        if (lat != null) return lat;
        if (geo != null && geo.containsKey("lat")) {
            Object latObj = geo.get("lat");
            if (latObj instanceof Number) {
                return ((Number) latObj).doubleValue();
            }
        }
        return null;
    }

    public Double getLon() {
        if (lng != null) return lng;
        if (geo != null && geo.containsKey("lon")) {
            Object lonObj = geo.get("lon");
            if (lonObj instanceof Number) {
                return ((Number) lonObj).doubleValue();
            }
        }
        return null;
    }
}