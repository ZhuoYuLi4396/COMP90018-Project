package unimelb.comp90018.equaltrip;

public class TripMember {
    public String userId;
    public String displayName;
    public String photoUrl;

    public TripMember() {}
    public TripMember(String userId, String displayName, String photoUrl) {
        this.userId = userId;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
    }
}
