package com.drs;
import com.drs.server.*;
import com.drs.model.*;
import com.drs.security.Crypto;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Integration tests use H2 in MySQL compatibility mode, not a MySQL server. */
class ServiceTest {
    Database db;DisasterService service;Actor admin,reporter,responder;
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    @BeforeEach void setup()throws Exception{
        db=new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");db.initialize();service=new DisasterService(db,new Crypto(Crypto.randomKey()));service.bootstrap("TestingAdmin123!");admin=service.login("admin","TestingAdmin123!");
        call("user-create","{username:'reporter',password:'TestingReporter123!',role:'REPORTER'}");reporter=service.login("reporter","TestingReporter123!");
        call("user-create","{username:'responder',password:'TestingResponder123!',role:'RESPONDER',department:'Hospital'}");responder=service.login("responder","TestingResponder123!");
    }
    Object call(String action,String body)throws Exception{return service.handle(admin,action,json(body));}
    long report()throws Exception{return ((Number)((Map<?,?>)service.handle(reporter,"report",json("{type:'Fire',location:'Brisbane',occurred_at:'2026-01-01T12:00',severity:'High',population:20,description:'Test incident'}"))).get("id")).longValue();}
    long shelter(int capacity)throws Exception{return ((Number)((Map<?,?>)call("shelter-create","{name:'Town Hall',location:'Brisbane',capacity:"+capacity+"}")).get("id")).longValue();}
    void assess(long id)throws Exception{call("assess","{id:"+id+",version:0,severity:'Critical',population:100}");}
    void assign(long id)throws Exception{assess(id);call("assign","{id:"+id+",version:1,primary_department:'Hospital',supporting_department:'',resource:'2 ambulances'}");}
    @Test void validLogin()throws Exception{assertEquals("ADMIN",admin.role());}
    @Test void wrongPasswordRejected(){assertThrows(SecurityException.class,()->service.login("admin","wrong"));}
    @Test void reportHasGeneratedId()throws Exception{assertTrue(report()>0);}
    @Test void storedDescriptionEncrypted()throws Exception{long id=report();try(Connection c=db.open()){assertNotEquals("Test incident",Database.rows(c,"SELECT description FROM incidents WHERE id=?",id).get(0).get("description"));}}
    @Test void authorisedReadDecrypts()throws Exception{report();assertTrue(service.handle(reporter,"incidents",json("{}")).toString().contains("Test incident"));}
    @Test void missingLocationRejected(){assertThrows(IllegalArgumentException.class,()->call("report","{type:'Fire',location:'',occurred_at:'2026-01-01T12:00',severity:'High',population:0,description:'x'}"));}
    @Test void invalidSeverityRejected(){assertThrows(IllegalArgumentException.class,()->call("report","{severity:'Extreme'}"));}
    @Test void futureDateRejected(){assertThrows(IllegalArgumentException.class,()->call("report","{type:'Fire',location:'A',occurred_at:'2999-01-01T12:00',severity:'High',population:0,description:'x'}"));}
    @Test void reporterCannotAssess()throws Exception{long id=report();assertThrows(SecurityException.class,()->service.handle(reporter,"assess",json("{id:"+id+",version:0,severity:'Low',population:0}")));}
    @Test void assessmentSetsPriority()throws Exception{long id=report();assess(id);assertTrue(call("incidents","{}").toString().contains("CRITICAL"));}
    @Test void staleVersionRejected()throws Exception{long id=report();assess(id);assertThrows(IllegalArgumentException.class,()->assess(id));}
    @Test void assignmentRequiresAssessment()throws Exception{long id=report();assertThrows(IllegalArgumentException.class,()->call("assign","{id:"+id+",version:0,primary_department:'Hospital',resource:'Ambulance'}"));}
    @Test void responderSeesOnlyAssigned()throws Exception{long id=report();assertEquals(0,((List<?>)service.handle(responder,"incidents",json("{}"))).size());assign(id);assertEquals(1,((List<?>)service.handle(responder,"incidents",json("{}"))).size());}
    @Test void completeLifecycle()throws Exception{long id=report();assign(id);service.handle(responder,"status",json("{id:"+id+",version:2,status:'In Progress'}"));service.handle(responder,"status",json("{id:"+id+",version:3,status:'Resolved'}"));call("status","{id:"+id+",version:4,status:'Closed'}");assertTrue(call("incidents","{}").toString().contains("Closed"));}
    @Test void prematureClosureRejected()throws Exception{long id=report();assertThrows(IllegalArgumentException.class,()->call("status","{id:"+id+",version:0,status:'Closed'}"));}
    @Test void overCapacityRollsBack()throws Exception{long id=report(),sid=shelter(10);assertThrows(IllegalArgumentException.class,()->call("evacuate","{incident_id:"+id+",shelter_id:"+sid+",people:11}"));assertTrue(call("shelters","{}").toString().contains("occupied=0"));assertEquals(0,((List<?>)call("evacuations","{}")).size());}
    @Test void concurrentAllocationsDoNotOverbook()throws Exception{long id=report(),sid=shelter(10);ExecutorService pool=Executors.newFixedThreadPool(2);try{Callable<Boolean> request=()->{try{call("evacuate","{incident_id:"+id+",shelter_id:"+sid+",people:7}");return true;}catch(IllegalArgumentException e){return false;}};var results=pool.invokeAll(List.of(request,request));int count=0;for(var r:results)if(r.get())count++;assertEquals(1,count);assertTrue(call("shelters","{}").toString().contains("occupied=7"));}finally{pool.shutdownNow();}}
    @Test void releaseRestoresCapacityExactlyOnce()throws Exception{long id=report(),sid=shelter(10);var result=(Map<?,?>)call("evacuate","{incident_id:"+id+",shelter_id:"+sid+",people:7}");String body="{id:"+result.get("id")+"}";call("release",body);assertThrows(IllegalArgumentException.class,()->call("release",body));assertTrue(call("shelters","{}").toString().contains("occupied=0"));}
    @Test void alertAcknowledgementIdempotent()throws Exception{long id=report();var r=(Map<?,?>)call("alert-create","{incident_id:"+id+",message:'Evacuate now'}");JsonObject body=json("{id:"+r.get("id")+"}");service.handle(reporter,"acknowledge",body);service.handle(reporter,"acknowledge",body);try(Connection c=db.open()){assertEquals(1,Database.rows(c,"SELECT * FROM acknowledgements").size());}}
    @Test void unassignedResponderCannotReadAlert()throws Exception{long id=report();var r=(Map<?,?>)call("alert-create","{incident_id:"+id+",message:'Evacuate'}");assertThrows(SecurityException.class,()->service.handle(responder,"acknowledge",json("{id:"+r.get("id")+"}")));}
    @Test void importDeduplicates()throws Exception{String b="{incidents:[{legacy_id:'OLD1',type:'Flood',location:'A',occurred_at:'2026-01-01T10:00',severity:'Low',population:1,description:'Imported'}]}";call("import",b);call("import",b);assertEquals(1,((List<?>)call("incidents","{}")).size());}
    @Test void importBatchIsAtomic(){assertThrows(IllegalArgumentException.class,()->call("import","{incidents:[{legacy_id:'OLD1',type:'Flood',location:'A',occurred_at:'2026-01-01T10:00',severity:'Low',population:1,description:'Imported'},{legacy_id:'OLD2',severity:'Invalid'}]}"));assertDoesNotThrow(()->assertEquals(0,((List<?>)call("incidents","{}")).size()));}
    @Test void duplicateUserRejected(){assertThrows(SQLException.class,()->call("user-create","{username:'reporter',password:'TestingReporter123!',role:'REPORTER'}"));}
    @Test void disabledAccountRevokesSession()throws Exception{call("user-toggle","{id:"+reporter.id()+"}");assertThrows(SecurityException.class,()->service.handle(reporter,"incidents",json("{}")));}
    @Test void auditTamperingDetected()throws Exception{report();assertFalse(call("audit","{}").toString().contains("TAMPERED"));try(Connection c=db.open()){Database.execute(c,"UPDATE audit_log SET details='Changed' WHERE id=1");}assertTrue(call("audit","{}").toString().contains("TAMPERED"));}
    @Test void sqlInjectionCannotLogin(){assertThrows(SecurityException.class,()->service.login("admin' OR 1=1 --","TestingAdmin123!"));}
    @Test void decimalPopulationRejected(){assertThrows(IllegalArgumentException.class,()->Rules.number(json("{population:1.5}"),"population",0,100));}
    @Test void encryptionRoundTripAndTamperCheck(){Crypto crypto=new Crypto(Crypto.randomKey());String cipher=crypto.encrypt("private");assertEquals("private",crypto.decrypt(cipher));byte[] bytes=Base64.getDecoder().decode(cipher);bytes[20]^=1;assertThrows(IllegalStateException.class,()->crypto.decrypt(Base64.getEncoder().encodeToString(bytes)));}
}
