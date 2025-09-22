package unimelb.comp90018.equaltrip.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GeocodingResponse {
    public String status;
    public List<Result> results;

    public static class Result {
        @SerializedName("formatted_address")
        public String formattedAddress;
        public Geometry geometry;
    }

    public static class Geometry {
        public Location location;
    }

    public static class Location {
        public double lat;
        public double lng;
    }
}
