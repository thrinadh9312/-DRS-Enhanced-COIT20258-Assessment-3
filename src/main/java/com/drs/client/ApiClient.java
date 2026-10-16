package com.drs.client;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
/** Only the server accesses JDBC. Certificate and hostname verification stay enabled. */
public final class ApiClient {
    private final HttpClient http;
    public ApiClient(){
        // Also supports NetBeans javafx:run without JVM trust-store arguments.
        if(System.getProperty("javax.net.ssl.trustStore")==null){
            try(var in=java.nio.file.Files.newInputStream(java.nio.file.Path.of("config/client.properties"))){
                java.util.Properties p=new java.util.Properties();p.load(in);
                System.setProperty("javax.net.ssl.trustStore","config/client-trust.p12");
                System.setProperty("javax.net.ssl.trustStorePassword",p.getProperty("DRS_TLS_PASSWORD"));
            }catch(java.io.IOException ignored){ /* OS/JVM trust settings remain active. */ }
        }
        http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
    private final String base=System.getenv().getOrDefault("DRS_SERVER_URL","https://localhost:8443");
    private String token="";
    public JsonElement call(String action,JsonObject body)throws Exception{
        HttpRequest request=HttpRequest.newBuilder(URI.create(base+"/api/"+action)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());JsonElement data=JsonParser.parseString(response.body());
        if(response.statusCode()!=200)throw new IllegalArgumentException(data.getAsJsonObject().get("error").getAsString());
        if(action.equals("login"))token=data.getAsJsonObject().get("token").getAsString();if(action.equals("logout"))token="";return data;
    }
}
