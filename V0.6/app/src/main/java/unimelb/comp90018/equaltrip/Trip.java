// Trip.java
package unimelb.comp90018.equaltrip;
// Author: Jinglin Lei
// SignUp Function
//Date: 2025-09-06


import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Trip {
    public String id;
    public String name;
    public String location;
    public Long startDate;   // 你存的是毫秒 → Long OK
    public Long endDate;
    public String description;
    public java.util.List<String> tripmates;
    public String ownerId;
    public com.google.firebase.Timestamp createdAt; // 如果你想保留
    public java.util.Map<String, String> date;      // 展示用，可空
    public Long createdAtClient;                    // 用来排序

    public Trip() {}
}

