package com.drs.server;

import com.drs.model.*;
import com.drs.security.Crypto;
import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.drs.server.Database.*;
import static com.drs.model.Rules.*;

/** Business layer: permission checks, validation, optimistic locking and atomic updates. */
public final class DisasterService {
    private final Database db;
    private final Crypto crypto;
    public DisasterService(Database db,Crypto crypto){this.db=db;this.crypto=crypto;}
    public void bootstrap(String password)throws Exception {
        if(password.length()<12)throw new IllegalArgumentException("Bootstrap password must contain at least 12 characters");
        try(Connection c=db.open()){
            if(rows(c,"SELECT id FROM users").isEmpty())execute(c,"INSERT INTO users(username,password_hash,role,department) VALUES(?,?,?,?)","admin",Crypto.hashPassword(password),"ADMIN","");
        }
    }
    public Actor login(String username,String password)throws Exception {
        try(Connection c=db.open()){
            var rs=rows(c,"SELECT * FROM users WHERE username=? AND active=TRUE",username);
            if(rs.isEmpty()||!Crypto.verifyPassword(password,rs.get(0).get("password_hash").toString()))throw new SecurityException("Invalid username or password");
            var r=rs.get(0);Actor actor=new Actor(((Number)r.get("id")).longValue(),username,r.get("role").toString(),r.get("department").toString());audit(c,actor,"LOGIN","Successful login");return actor;
        }
    }
    private void require(boolean condition){if(!condition)throw new SecurityException("You do not have permission for this action");}
    private void audit(Connection c,Actor a,String action,String details)throws SQLException{
        String time=Instant.now().toString();String signed=a.id()+"|"+action+"|"+details+"|"+time;
        execute(c,"INSERT INTO audit_log(actor,action,details,event_time,signature) VALUES(?,?,?,?,?)",a.id(),action,details,time,crypto.mac(signed));
    }
    private Map<String,Object> incident(Connection c,long id,Actor a,boolean lock)throws SQLException{
        var rs=rows(c,"SELECT * FROM incidents WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rs.isEmpty())throw new IllegalArgumentException("Incident not found");var r=rs.get(0);
        require(a.coordinator()||(a.role().equals("REPORTER")&&((Number)r.get("created_by")).longValue()==a.id())||(a.role().equals("RESPONDER")&&(a.department().equals(r.get("primary_department"))||a.department().equals(r.get("supporting_department")))));
        return r;
    }
    private void openIncident(Map<String,Object> r){if("Closed".equals(r.get("status")))throw new IllegalArgumentException("Closed incidents cannot be modified");}
    public Object handle(Actor a,String action,JsonObject j)throws Exception{
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try {Object result=dispatch(c,a,action,j);c.commit();return result;}
            catch(Exception e){c.rollback();throw e;}
        }
    }
    private Object dispatch(Connection c,Actor a,String action,JsonObject j)throws Exception {
        // Recheck account status on every request; disabling an account revokes active sessions.
        var account=rows(c,"SELECT active,role,department FROM users WHERE id=?",a.id());
        require(!account.isEmpty()&&Boolean.TRUE.equals(account.get(0).get("active"))&&a.role().equals(account.get(0).get("role"))&&a.department().equals(account.get(0).get("department")));
        switch(action){
            case "incidents": {
                List<Map<String,Object>> result;
                if(a.coordinator())result=rows(c,"SELECT * FROM incidents ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,id DESC");
                else if(a.role().equals("REPORTER"))result=rows(c,"SELECT * FROM incidents WHERE created_by=? ORDER BY id DESC",a.id());
                else result=rows(c,"SELECT * FROM incidents WHERE primary_department=? OR supporting_department=? ORDER BY id DESC",a.department(),a.department());
                for(var r:result)r.put("description",crypto.decrypt(r.get("description").toString()));return result;
            }
            case "report": return Map.of("id",report(c,a,j,null));
            case "assess": {
                require(a.coordinator());long id=number(j,"id",1,Integer.MAX_VALUE);var r=incident(c,id,a,true);openIncident(r);
                String severity=choice(j,"severity",SEVERITIES);int version=number(j,"version",0,Integer.MAX_VALUE);
                if(execute(c,"UPDATE incidents SET severity=?,population=?,priority=?,status=CASE WHEN status='Reported' THEN 'Assessed' ELSE status END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",severity,number(j,"population",0,100000000),priority(severity),id,version)!=1)throw new IllegalArgumentException("Incident changed. Refresh and retry.");
                audit(c,a,"ASSESS","Incident "+id);return Map.of("message","Assessment saved");
            }
            case "assign": {
                require(a.coordinator());long id=number(j,"id",1,Integer.MAX_VALUE);var r=incident(c,id,a,true);openIncident(r);
                if("Reported".equals(r.get("status")))throw new IllegalArgumentException("Assess the incident before allocation");
                String primary=choice(j,"primary_department",DEPARTMENTS),support=j.has("supporting_department")?j.get("supporting_department").getAsString().trim():"";
                if(!support.isEmpty()&&(!DEPARTMENTS.contains(support)||support.equals(primary)))throw new IllegalArgumentException("Supporting department must differ from primary department");
                int count=execute(c,"UPDATE incidents SET primary_department=?,supporting_department=?,resource=?,status='Assigned',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",primary,support,text(j,"resource",200),id,number(j,"version",0,Integer.MAX_VALUE));
                if(count!=1)throw new IllegalArgumentException("Incident changed. Refresh and retry.");
                audit(c,a,"ASSIGN","Incident "+id);return Map.of("message","Departments and resources allocated");
            }
            case "status": {
                require(a.coordinator()||a.role().equals("RESPONDER"));long id=number(j,"id",1,Integer.MAX_VALUE);var r=incident(c,id,a,true);openIncident(r);
                String next=choice(j,"status",List.of("In Progress","Resolved","Closed")),old=r.get("status").toString();
                if(next.equals("Closed")){require(a.coordinator());if(!old.equals("Resolved"))throw new IllegalArgumentException("Resolve the incident before closure");if(!rows(c,"SELECT id FROM evacuations WHERE incident_id=? AND active=TRUE",id).isEmpty())throw new IllegalArgumentException("Release active evacuations before closure");}
                else if(next.equals("Resolved")&&!old.equals("In Progress"))throw new IllegalArgumentException("Only in-progress incidents can be resolved");
                else if(next.equals("In Progress")&&!old.equals("Assigned"))throw new IllegalArgumentException("Only assigned incidents can enter progress");
                if(execute(c,"UPDATE incidents SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?",next,id,number(j,"version",0,Integer.MAX_VALUE))!=1)throw new IllegalArgumentException("Incident changed. Refresh and retry.");
                audit(c,a,"STATUS","Incident "+id+" -> "+next);return Map.of("message","Status updated");
            }
            case "shelters": require(a.coordinator()||a.role().equals("RESPONDER"));return rows(c,"SELECT * FROM shelters ORDER BY name");
            case "shelter-create": {
                require(a.coordinator());long id=insert(c,"INSERT INTO shelters(name,location,capacity) VALUES(?,?,?)",text(j,"name",100),text(j,"location",200),number(j,"capacity",1,1000000));audit(c,a,"SHELTER_CREATE","Shelter "+id);return Map.of("id",id);
            }
            case "evacuations": {
                require(a.coordinator()||a.role().equals("RESPONDER"));
                String sql="SELECT e.*,s.name AS shelter FROM evacuations e JOIN shelters s ON s.id=e.shelter_id JOIN incidents i ON i.id=e.incident_id";
                return a.coordinator()?rows(c,sql+" ORDER BY e.id DESC"):rows(c,sql+" WHERE i.primary_department=? OR i.supporting_department=? ORDER BY e.id DESC",a.department(),a.department());
            }
            case "evacuate": {
                require(a.coordinator());long id=number(j,"incident_id",1,Integer.MAX_VALUE),sid=number(j,"shelter_id",1,Integer.MAX_VALUE);int people=number(j,"people",1,1000000);
                openIncident(incident(c,id,a,true));
                if(execute(c,"UPDATE shelters SET occupied=occupied+? WHERE id=? AND occupied+?<=capacity",people,sid,people)!=1)throw new IllegalArgumentException("Shelter not found or insufficient capacity");
                long eid=insert(c,"INSERT INTO evacuations(incident_id,shelter_id,people,created_by) VALUES(?,?,?,?)",id,sid,people,a.id());audit(c,a,"EVACUATE","Allocation "+eid+", incident "+id+", people "+people);return Map.of("id",eid);
            }
            case "release": {
                require(a.coordinator());long id=number(j,"id",1,Integer.MAX_VALUE);var list=rows(c,"SELECT * FROM evacuations WHERE id=? FOR UPDATE",id);
                if(list.isEmpty()||!Boolean.TRUE.equals(list.get(0).get("active")))throw new IllegalArgumentException("Evacuation not found or already released");var e=list.get(0);
                execute(c,"UPDATE shelters SET occupied=occupied-? WHERE id=?",e.get("people"),e.get("shelter_id"));execute(c,"UPDATE evacuations SET active=FALSE WHERE id=?",id);audit(c,a,"RELEASE","Allocation "+id);return Map.of("message","People checked out; capacity restored");
            }
            case "alert-create": {
                require(a.coordinator());long id=number(j,"incident_id",1,Integer.MAX_VALUE);openIncident(incident(c,id,a,true));long alert=insert(c,"INSERT INTO alerts(incident_id,message,created_by) VALUES(?,?,?)",id,crypto.encrypt(text(j,"message",2000)),a.id());audit(c,a,"ALERT","Alert "+alert+", incident "+id);return Map.of("id",alert);
            }
            case "alerts": {
                String sql="SELECT al.*, (SELECT COUNT(*) FROM acknowledgements k WHERE k.alert_id=al.id) AS acknowledgements,(SELECT COUNT(*) FROM acknowledgements k WHERE k.alert_id=al.id AND k.user_id=?) AS acknowledged FROM alerts al JOIN incidents i ON i.id=al.incident_id";
                List<Map<String,Object>> result;
                if(a.coordinator())result=rows(c,sql+" ORDER BY al.id DESC",a.id());
                else if(a.role().equals("REPORTER"))result=rows(c,sql+" WHERE i.created_by=? ORDER BY al.id DESC",a.id(),a.id());
                else result=rows(c,sql+" WHERE i.primary_department=? OR i.supporting_department=? ORDER BY al.id DESC",a.id(),a.department(),a.department());
                for(var r:result)r.put("message",crypto.decrypt(r.get("message").toString()));return result;
            }
            case "acknowledge": {
                long id=number(j,"id",1,Integer.MAX_VALUE);var list=rows(c,"SELECT incident_id FROM alerts WHERE id=? FOR UPDATE",id);if(list.isEmpty())throw new IllegalArgumentException("Alert not found");incident(c,((Number)list.get(0).get("incident_id")).longValue(),a,false);
                if(rows(c,"SELECT alert_id FROM acknowledgements WHERE alert_id=? AND user_id=?",id,a.id()).isEmpty()){execute(c,"INSERT INTO acknowledgements(alert_id,user_id) VALUES(?,?)",id,a.id());audit(c,a,"ACKNOWLEDGE","Alert "+id);}return Map.of("message","Alert acknowledged");
            }
            case "users": require(a.role().equals("ADMIN"));return rows(c,"SELECT id,username,role,department,active FROM users ORDER BY id");
            case "user-create": {
                require(a.role().equals("ADMIN"));String username=text(j,"username",60);if(!username.matches("[A-Za-z0-9_.-]{3,60}"))throw new IllegalArgumentException("Username: 3-60 letters, digits, dots, dashes or underscores");String password=text(j,"password",128);if(password.length()<12)throw new IllegalArgumentException("Password must have at least 12 characters");String role=choice(j,"role",List.of("ADMIN","COORDINATOR","RESPONDER","REPORTER"));String department=role.equals("RESPONDER")?choice(j,"department",DEPARTMENTS):"";
                long id=insert(c,"INSERT INTO users(username,password_hash,role,department) VALUES(?,?,?,?)",username,Crypto.hashPassword(password),role,department);audit(c,a,"USER_CREATE","User "+id+", role "+role);return Map.of("id",id);
            }
            case "user-toggle": {
                require(a.role().equals("ADMIN"));long id=number(j,"id",1,Integer.MAX_VALUE);if(id==a.id())throw new IllegalArgumentException("You cannot disable your own account");if(execute(c,"UPDATE users SET active=NOT active WHERE id=?",id)!=1)throw new IllegalArgumentException("User not found");audit(c,a,"USER_TOGGLE","User "+id);return Map.of("message","Account access updated");
            }
            case "audit": {
                require(a.role().equals("ADMIN"));var rs=rows(c,"SELECT * FROM audit_log ORDER BY id DESC");for(var r:rs){String signed=r.get("actor")+"|"+r.get("action")+"|"+r.get("details")+"|"+r.get("event_time");r.put("integrity",crypto.mac(signed).equals(r.get("signature"))?"VALID":"TAMPERED");}return rs;
            }
            case "import": {
                require(a.coordinator());JsonArray array=j.getAsJsonArray("incidents");if(array==null||array.size()>1000)throw new IllegalArgumentException("Supply up to 1000 incidents per import");int added=0,skipped=0;
                for(JsonElement el:array){JsonObject row=el.getAsJsonObject();String legacy=text(row,"legacy_id",100);if(!rows(c,"SELECT id FROM incidents WHERE legacy_id=?",legacy).isEmpty()){skipped++;continue;}long imported=report(c,a,row,legacy);
                    String status=row.has("status")?choice(row,"status",List.of("Reported","Assessed","Assigned","In Progress","Resolved","Closed")):"Reported";
                    String primary=row.has("primary_department")?row.get("primary_department").getAsString().trim():"";
                    String support=row.has("supporting_department")?row.get("supporting_department").getAsString().trim():"";
                    String resource=row.has("resource")?row.get("resource").getAsString().trim():"";
                    if((!primary.isEmpty()&&!DEPARTMENTS.contains(primary))||(!support.isEmpty()&&!DEPARTMENTS.contains(support))||resource.length()>200)throw new IllegalArgumentException("Invalid imported department or resource");
                    String importedPriority=row.has("priority")?choice(row,"priority",List.of("LOW","MEDIUM","HIGH","CRITICAL")):priority(row.get("severity").getAsString());
                    execute(c,"UPDATE incidents SET status=?,primary_department=?,supporting_department=?,resource=?,priority=? WHERE id=?",status,primary,support,resource,importedPriority,imported);added++;}
                audit(c,a,"IMPORT","Imported "+added+", skipped "+skipped);return Map.of("message","Imported "+added+"; duplicates skipped "+skipped);
            }
            default: throw new IllegalArgumentException("Unknown action");
        }
    }
    private long report(Connection c,Actor a,JsonObject j,String legacy)throws Exception{
        require(a.coordinator()||a.role().equals("REPORTER"));String severity=choice(j,"severity",SEVERITIES);
        long id=insert(c,"INSERT INTO incidents(legacy_id,type,location,occurred_at,severity,description,population,priority,status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",legacy,choice(j,"type",TYPES),text(j,"location",200),date(j),severity,crypto.encrypt(text(j,"description",4000)),number(j,"population",0,100000000),priority(severity),"Reported",a.id());
        audit(c,a,"REPORT","Incident "+id+(legacy==null?"":", imported "+legacy));return id;
    }
}
