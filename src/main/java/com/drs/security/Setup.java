package com.drs.security;
import java.nio.file.*;
import java.util.*;
import java.io.*;
/** Generates private local configuration and a localhost TLS certificate. Never bundled with secrets. */
public final class Setup {
    public static void main(String[] args)throws Exception{
        Path config=Path.of("config");Files.createDirectories(config);if(Files.exists(config.resolve("server.properties")))throw new IllegalStateException("Configuration already exists; preserve keys to keep database data readable");
        Properties p=new Properties();p.setProperty("DRS_DATA_KEY",Crypto.randomKey());p.setProperty("DRS_TLS_PASSWORD",Crypto.randomKey().replace("/","x").replace("+","y"));p.setProperty("DRS_ADMIN_PASSWORD",Crypto.randomKey().substring(0,20));p.setProperty("DRS_DB_USER","root");p.setProperty("DRS_DB_PASSWORD","");p.setProperty("DRS_DB_NAME","drs_enhanced");p.setProperty("DRS_DB_BASE","jdbc:mysql://localhost:3306/");
        String keytool=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();String pass=p.getProperty("DRS_TLS_PASSWORD");
        run(keytool,"-genkeypair","-alias","drs","-keyalg","RSA","-keysize","3072","-validity","365","-storetype","PKCS12","-keystore","config/server.p12","-storepass",pass,"-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1");
        run(keytool,"-exportcert","-alias","drs","-keystore","config/server.p12","-storepass",pass,"-file","config/server.cer");
        run(keytool,"-importcert","-noprompt","-alias","drs","-storetype","PKCS12","-keystore","config/client-trust.p12","-storepass",pass,"-file","config/server.cer");
        try(OutputStream out=Files.newOutputStream(config.resolve("server.properties"))){p.store(out,"PRIVATE configuration - do not commit or share");}
        Properties client=new Properties();client.setProperty("DRS_TLS_PASSWORD",pass);client.setProperty("DRS_SERVER_URL","https://localhost:8443");try(OutputStream out=Files.newOutputStream(config.resolve("client.properties"))){client.store(out,"Client trust settings; no database or encryption secrets");}
        System.out.println("Setup complete. Edit config/server.properties to set DRS_DB_PASSWORD. Your generated admin password is in DRS_ADMIN_PASSWORD. Keep this file and server.p12 private.");
    }
    private static void run(String...cmd)throws Exception{if(new ProcessBuilder(cmd).inheritIO().start().waitFor()!=0)throw new IOException("keytool failed; inspect output before retrying");}
}
