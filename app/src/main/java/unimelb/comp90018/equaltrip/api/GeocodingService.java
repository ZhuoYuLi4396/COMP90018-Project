package unimelb.comp90018.equaltrip.api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeocodingService {
    // 地址 -> 经纬度
    @GET("geocode/json")
    Call<GeocodingResponse> geocode(
            @Query("address") String address,
            @Query("key") String apiKey
    );

    // 经纬度 -> 地址
    @GET("geocode/json")
    Call<GeocodingResponse> reverse(
            @Query("latlng") String latlng, // "lat,lng"
            @Query("key") String apiKey
    );
}
