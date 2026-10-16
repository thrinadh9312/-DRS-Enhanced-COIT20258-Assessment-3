package com.drs.server;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** JDBC repository. Each request owns its connection and transaction. */
public final class Database {
    private final String url, user, password;
    public Database(String url,String user,String password){this.url=url;this.user=user;this.password=password;}
    public Connection open() throws SQLException {return DriverManager.getConnection(url,user,password);}
    public void initialize() throws Exception {
        try(Connection c=open(); InputStream in=Database.class.getResourceAsStream("/db/schema.sql")) {
            for(String sql:new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8).split(";"))
                if(!sql.isBlank()) try(Statement s=c.createStatement()){s.execute(sql);}
        }
    }
    public static List<Map<String,Object>> rows(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement p=prepare(c,sql,args);ResultSet r=p.executeQuery()) {
            List<Map<String,Object>> out=new ArrayList<>();
            while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++){Object v=r.getObject(i);row.put(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),v instanceof Timestamp?v.toString():v);}out.add(row);}return out;
        }
    }
    public static int execute(Connection c,String sql,Object... args) throws SQLException {try(PreparedStatement p=prepare(c,sql,args)){return p.executeUpdate();}}
    public static long insert(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){bind(p,args);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("No generated ID");return r.getLong(1);}}
    }
    private static PreparedStatement prepare(Connection c,String sql,Object[] args)throws SQLException{PreparedStatement p=c.prepareStatement(sql);bind(p,args);return p;}
    private static void bind(PreparedStatement p,Object[] args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
}
