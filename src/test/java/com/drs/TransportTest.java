package com.drs;
import com.drs.server.*;
import com.drs.security.Crypto;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import javax.net.ssl.*;
import java.util.concurrent.*;

/** Real TLS socket round trip against the production request handler; H2 database. */
class TransportTest {
    static HttpsServer server;static ExecutorService pool;static HttpClient client;static String base;
    @BeforeAll static void start()throws Exception{
        Path dir=Files.createTempDirectory("drs-test-tls");Path store=dir.resolve("server.p12");String pass="TestOnlyPassword123";
        String suffix=System.getProperty("os.name").startsWith("Windows")?".exe":"";
        Process p=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool"+suffix).toString(),"-genkeypair","-alias","test","-keyalg","RSA","-keystore",store.toString(),"-storepass",pass,"-dname","CN=localhost","-ext","SAN=dns:localhost","-validity","1").redirectErrorStream(true).start();p.getInputStream().readAllBytes();assertEquals(0,p.waitFor());
        KeyStore ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){ks.load(in,pass.toCharArray());}KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,pass.toCharArray());TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        Database db=new Database("jdbc:h2:mem:transport;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");db.initialize();DisasterService svc=new DisasterService(db,new Crypto(Crypto.randomKey()));svc.bootstrap("TestingAdmin123!");ServerMain handler=new ServerMain(svc);var method=ServerMain.class.getDeclaredMethod("handle",HttpExchange.class);method.setAccessible(true);
        server=HttpsServer.create(new InetSocketAddress("localhost",0),0);server.setHttpsConfigurator(new HttpsConfigurator(ssl));server.createContext("/api/",x->{try{method.invoke(handler,x);}catch(Exception e){throw new java.io.IOException(e);}});pool=Executors.newFixedThreadPool(4);server.setExecutor(pool);server.start();base="https://localhost:"+server.getAddress().getPort()+"/api/";client=HttpClient.newBuilder().sslContext(ssl).build();Files.delete(store);Files.delete(dir);
    }
    @AfterAll static void stop(){if(server!=null)server.stop(0);if(pool!=null)pool.shutdownNow();}
    HttpResponse<String> request(String action,String body,String token)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base+action)).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void protectedEndpointRejectsNoSession()throws Exception{assertEquals(403,request("incidents","{}","").statusCode());}
    @Test void tlsLoginReadAndLogout()throws Exception{var login=request("login","{\"username\":\"admin\",\"password\":\"TestingAdmin123!\"}","");assertEquals(200,login.statusCode());String token=JsonParser.parseString(login.body()).getAsJsonObject().get("token").getAsString();assertEquals(200,request("incidents","{}",token).statusCode());assertEquals(200,request("logout","{}",token).statusCode());assertEquals(403,request("incidents","{}",token).statusCode());}
    @Test void nonPostRejected()throws Exception{assertEquals(405,client.send(HttpRequest.newBuilder(URI.create(base+"incidents")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());}
}
