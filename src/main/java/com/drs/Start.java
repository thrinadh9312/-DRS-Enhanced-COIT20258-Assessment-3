package com.drs;
import java.util.*;
import java.nio.file.*;
import java.io.*;
/** Cross-platform process launcher loads properties without putting secrets in shell commands. */
public final class Start {
    public static void main(String[] args)throws Exception{
        String mode=args.length==0?"client":args[0];if(!List.of("client","server","setup").contains(mode))throw new IllegalArgumentException("Use setup, server or client");
        boolean win=System.getProperty("os.name").startsWith("Windows");List<String> cmd=new ArrayList<>();cmd.add(Path.of(System.getProperty("java.home"),"bin",win?"java.exe":"java").toString());
        Properties props=new Properties();if(!mode.equals("setup"))try(InputStream in=Files.newInputStream(Path.of("config",mode.equals("server")?"server.properties":"client.properties"))){props.load(in);}
        if(mode.equals("client")){cmd.add("-Djavax.net.ssl.trustStore=config/client-trust.p12");cmd.add("-Djavax.net.ssl.trustStorePassword="+props.getProperty("DRS_TLS_PASSWORD"));}
        String platform=win?"win":System.getProperty("os.name").contains("Mac")?"mac":"linux";
        if(mode.equals("client")) {cmd.add("--module-path");cmd.add("dist/javafx-"+platform);cmd.add("--add-modules");cmd.add("javafx.controls,javafx.fxml");}
        cmd.add("-cp");cmd.add("dist/drs-enhanced-1.0.0.jar"+File.pathSeparator+"dist/lib/*");cmd.add(mode.equals("server")?"com.drs.server.ServerMain":mode.equals("setup")?"com.drs.security.Setup":"com.drs.client.Launcher");
        ProcessBuilder builder=new ProcessBuilder(cmd).inheritIO();props.forEach((k,v)->builder.environment().put(k.toString(),v.toString()));System.exit(builder.start().waitFor());
    }
}
