package unimelb.comp90018.equaltrip;

import java.util.List;
import java.util.Map;

public class Bill {
    public String id;
    public String title;
    public String category;
    public Long dateMs;
    public String payerUid;
    public List<String> participants;       // uids
    public Map<String, Long> splits;        // uid -> cents
    public Long totalCents;
    public com.google.firebase.Timestamp createdAt;

    public Bill() {}
}
