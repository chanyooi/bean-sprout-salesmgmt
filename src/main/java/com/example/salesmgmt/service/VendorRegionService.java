package com.example.salesmgmt.service;

import com.example.salesmgmt.repository.VendorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class VendorRegionService {
    private static final Pattern AREA = Pattern.compile("^[가-힣0-9]+[읍면동]$");
    private static final String BASE = "https://maps.apigw.ntruss.com";
    private final VendorProfileRepository profiles;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public VendorRegionService(VendorProfileRepository profiles, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.profiles=profiles; this.mapper=mapper; this.transaction=new TransactionTemplate(manager);
    }

    public record Result(boolean ok, String region, BigDecimal lat, BigDecimal lng, String message) {}
    private Result fail(String message){return new Result(false,null,null,null,message);}
    public static String addressRegion(String address) {
        if(address==null)return null;
        // A numbered legal dong is valid; ignore building names and road names.
        String clean=address.replace('(', ' ').replace(')', ' ').replace(',', ' ');
        for(String token:clean.trim().split("\\s+")){
            if(AREA.matcher(token).matches())return token;
        }
        return null;
    }
    public Result resolve(Long id,String expectedAddress,String manualRegion,BigDecimal suppliedLat,BigDecimal suppliedLng,boolean manual) {
        var p=profiles.findByVendor_Id(id).orElse(null);
        if(p==null)return fail("거래처 정보에서 주소를 먼저 저장하세요.");
        String address=p.getAddress();
        if(!Objects.equals(address,expectedAddress==null||expectedAddress.isBlank()?null:expectedAddress.trim()))
            return fail("주소가 변경되었습니다. 새로고침하세요.");
        if(!manual&&p.getSalesRegion()!=null)
            return new Result(true,p.getSalesRegion(),p.getLatitude(),p.getLongitude(),"저장된 지역");
        String region=manualRegion==null?null:manualRegion.trim();
        BigDecimal lat=p.getLatitude(),lng=p.getLongitude();
        if(region!=null&&!AREA.matcher(region).matches())return fail("읍·면·동 이름만 입력하세요.");
        if(manual){
            if(region==null||region.isEmpty())return fail("지역명을 입력하세요.");
            return persist(id,address,region,null,null,true,lat,lng);
        }
        if(valid(suppliedLat,suppliedLng)){lat=suppliedLat;lng=suppliedLng;}
        if(region!=null&&valid(lat,lng))return persist(id,address,region,lat,lng,false,lat,lng);
        region=addressRegion(address);
        if(region!=null&&valid(lat,lng))return persist(id,address,region,lat,lng,false,lat,lng);
        if((address==null||address.isBlank())&&!valid(lat,lng))return fail("주소 미등록");
        String secret=System.getenv("NAVER_MAP_CLIENT_SECRET");
        String key=System.getenv("NAVER_MAP_CLIENT_ID");
        if(key==null||key.isBlank())key="iwii3ygty2";
        if(secret==null||secret.isBlank()){
            if(region!=null)return persist(id,address,region,null,null,false,lat,lng);
            return fail("서버 지도 인증정보 미설정");
        }
        try{
            if(!valid(lat,lng)&&address!=null){
                JsonNode root=get("/map-geocode/v2/geocode?query="+URLEncoder.encode(address,StandardCharsets.UTF_8)+"&count=2",key,secret);
                JsonNode list=root.path("addresses");
                if(!list.isArray()||list.size()==0)return fail("주소 검색 결과 없음");
                if(list.size()>1)return fail("주소 검색 결과가 여러 개입니다. 상세 주소를 확인하세요.");
                JsonNode point=list.get(0);
                lat=new BigDecimal(point.path("y").asText()); lng=new BigDecimal(point.path("x").asText());
                if(!valid(lat,lng))return fail("주소 좌표 확인 실패");
                region=addressRegion(point.path("jibunAddress").asText());
                if(region==null)region=addressRegion(point.path("roadAddress").asText());
            }
            if(region==null&&valid(lat,lng)){
                JsonNode root=get("/map-reversegeocode/v2/gc?coords="+lng.toPlainString()+","+lat.toPlainString()+"&orders=legalcode,admcode&output=json",key,secret);
                for(JsonNode item:root.path("results")){
                    String name=item.path("region").path("area3").path("name").asText();
                    if(AREA.matcher(name).matches()){region=name;break;}
                }
            }
            if(region==null)return fail("읍·면·동 검색 결과 없음");
            return persist(id,address,region,valid(lat,lng)?lat:null,valid(lat,lng)?lng:null,false,lat,lng);
        }catch(ApiFailure e){return fail(e.getMessage());}
        catch(InterruptedException e){Thread.currentThread().interrupt();return fail("주소 검색 중단");}
        catch(Exception e){return fail("주소 검색 연결 또는 응답 오류");}
    }
    private Result persist(Long id,String address,String region,BigDecimal saveLat,BigDecimal saveLng,boolean manual,BigDecimal lat,BigDecimal lng){
        Integer count=transaction.execute(status->profiles.saveSalesLocation(id,address,region,saveLat,saveLng,manual));
        if(count==null||count==0){
            var latest=profiles.findByVendor_Id(id).orElse(null);
            if(latest!=null&&Objects.equals(address,latest.getAddress())&&latest.getSalesRegion()!=null)
                return new Result(true,latest.getSalesRegion(),latest.getLatitude(),latest.getLongitude(),"저장된 지역");
            return fail("주소가 변경되었습니다. 새로고침하세요.");
        }
        return new Result(true,region,lat,lng,"지역 저장 완료");
    }
    private static boolean valid(BigDecimal lat,BigDecimal lng){
        return lat!=null&&lng!=null&&lat.doubleValue()>=33&&lat.doubleValue()<=39&&lng.doubleValue()>=124&&lng.doubleValue()<=132;
    }
    private JsonNode get(String path,String key,String secret)throws Exception{
        var req=HttpRequest.newBuilder(URI.create(BASE+path)).timeout(Duration.ofSeconds(7))
            .header("x-ncp-apigw-api-key-id",key).header("x-ncp-apigw-api-key",secret)
            .header("Accept","application/json").GET().build();
        var res=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200)throw new ApiFailure("지도 API 오류 (HTTP "+res.statusCode()+")");
        return mapper.readTree(res.body());
    }
    private static class ApiFailure extends Exception{ApiFailure(String message){super(message);}}
}
