package unimelb.comp90018.equaltrip;

public class ParticipantBalance {
    public String userId;
    public String displayName;
    public String photoUrl;
    public boolean isPayer;
    public String rightText; // 右侧文案（示例：Paid A$100 / owes A $25）

    public ParticipantBalance(String userId, String displayName, String photoUrl,
                              boolean isPayer, String rightText) {
        this.userId = userId;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.isPayer = isPayer;
        this.rightText = rightText;
    }
}
