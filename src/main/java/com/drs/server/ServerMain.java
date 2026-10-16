package com.drs.server;
import com.drs.model.Actor;
import com.drs.security.Crypto;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import javax.net.ssl.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded multithreaded HTTPS JSON server. No Java object deserialization. */
public final class ServerMain {
    private record Session(Actor actor,Instant expires){}
    private record Attempts(int count,Instant until){}
    private final ConcurrentMap<String,Session> sessions=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Attempts> attempts=new ConcurrentHashMap<>();
    private final DisasterService service;
    private final Gson gson=new Gson();
    public ServerMain(DisasterService service){this.service=service;}
    public static String env(String key,String fallback){return System.getenv().getOrDefault(key,fallback);}
    public static void main(String[] args)throws Exception {
        String password=env("DRS_DB_PASSWORD","");String database=env("DRS_DB_NAME","drs_enhanced");if(!database.matches("[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid database name");
        String base=env("DRS_DB_BASE","jdbc:mysql://localhost:3306/");
        try(Connection c=DriverManager.getConnection(base,env("DRS_DB_USER","root"),password);Statement s=c.createStatement()){s.execute("CREATE DATABASE IF NOT EXISTS "+database+" CHARACTER SET utf8mb4");}
        Database db=new Database(base+database,env("DRS_DB_USER","root"),password);db.initialize();
        DisasterService service=new DisasterService(db,new Crypto(env("DRS_DATA_KEY","")));service.bootstrap(env("DRS_ADMIN_PASSWORD",""));
        String pass=env("DRS_TLS_PASSWORD","");KeyStore ks=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(Path.of(env("DRS_KEYSTORE","config/server.p12")))){ks.load(in,pass.toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,pass.toCharArray());SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),null,null);
        HttpsServer server=HttpsServer.create(new InetSocketAddress(env("DRS_BIND","127.0.0.1"),Integer.parseInt(env("DRS_PORT","8443"))),64);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));ServerMain handler=new ServerMain(service);server.createContext("/api/",handler::handle);
        ThreadPoolExecutor pool=new ThreadPoolExecutor(8,16,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),new ThreadPoolExecutor.CallerRunsPolicy());server.setExecutor(pool);
        ScheduledExecutorService cleanup=Executors.newSingleThreadScheduledExecutor();cleanup.scheduleAtFixedRate(()->{Instant now=Instant.now();handler.sessions.entrySet().removeIf(e->e.getValue().expires().isBefore(now));handler.attempts.entrySet().removeIf(e->e.getValue().until().isBefore(now));},1,1,TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(1);pool.shutdown();cleanup.shutdown();}));server.start();System.out.println("DRS-Enhanced HTTPS server ready on "+server.getAddress());
    }
    private void handle(HttpExchange x)throws IOException{
        int status=200;Object response;
        try{
            if(!x.getRequestMethod().equals("POST")){send(x,405,Map.of("error","POST required"));return;}
            byte[] body=x.getRequestBody().readNBytes(1048577);if(body.length>1048576){send(x,413,Map.of("error","Request exceeds 1 MiB"));return;}
            JsonObject j=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();String action=x.getRequestURI().getPath().substring(5);
            if(action.equals("login")){
                String username=com.drs.model.Rules.text(j,"username",60);String remote=x.getRemoteAddress().getAddress().getHostAddress();String key=remote;
                Attempts attempt=attempts.get(key);if(attempt!=null&&attempt.until().isAfter(Instant.now())&&attempt.count()>=10){send(x,429,Map.of("error","Too many login attempts; wait 5 minutes"));return;}
                Actor actor;try{actor=service.login(username,com.drs.model.Rules.text(j,"password",128));}catch(SecurityException e){attempts.compute(key,(k,v)->new Attempts(v==null||v.until().isBefore(Instant.now())?1:v.count()+1,Instant.now().plusSeconds(300)));throw e;}
                attempts.remove(key);String token=Crypto.randomKey();sessions.put(token,new Session(actor,Instant.now().plusSeconds(3600)));response=Map.of("token",token,"username",actor.username(),"role",actor.role(),"department",actor.department());
            }else{
                String auth=x.getRequestHeaders().getFirst("Authorization");String token=auth!=null&&auth.startsWith("Bearer ")?auth.substring(7):"";Session s=sessions.get(token);
                if(s==null||s.expires().isBefore(Instant.now()))throw new SecurityException("Session expired; sign in again");
                if(action.equals("logout")){sessions.remove(token);response=Map.of("message","Signed out");}else response=service.handle(s.actor(),action,j);
            }
        }catch(SecurityException e){status=403;response=Map.of("error",e.getMessage());}
        catch(IllegalArgumentException|IllegalStateException e){status=400;response=Map.of("error",e.getMessage()==null?"Invalid request":e.getMessage());}
        catch(SQLException e){status=409;response=Map.of("error","Database operation could not complete. Check duplicates, refresh and retry.");System.err.println("Database failure: "+e.getSQLState());}
        catch(Exception e){status=500;response=Map.of("error","Request failed. Check server configuration or request format.");System.err.println("Request failed: "+e.getClass().getSimpleName());}
        send(x,status,response);
    }
    private void send(HttpExchange x,int code,Object response)throws IOException{byte[] bytes=gson.toJson(response).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.sendResponseHeaders(code,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}finally{x.close();}}
}
