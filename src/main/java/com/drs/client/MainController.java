package com.drs.client;

import com.drs.model.Rules;
import com.google.gson.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** MVC controller. Blocking I/O runs on a worker; only UI updates run on JavaFX. */
public final class MainController {
    @FXML private VBox loginPane;
    @FXML private TabPane tabs;
    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Label identity,message;
    @FXML private Button logout;
    @FXML private ProgressIndicator progress;
    private final ApiClient api=new ApiClient();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"drs-client-io");t.setDaemon(true);return t;});
    private final Map<String,TableView<JsonObject>> tables=new HashMap<>();
    private String role;
    private boolean busy;
    private Label summary;
    private String filterText="";
    private final List<JsonObject> allIncidents=new ArrayList<>();
    private boolean coordinator(){return "ADMIN".equals(role)||"COORDINATOR".equals(role);}
    private void task(Callable<JsonElement> job,Consumer<JsonElement> done){
        if(busy)return;busy=true;progress.setVisible(true);tabs.setDisable(true);loginPane.setDisable(true);logout.setDisable(true);message.setText("Working…");
        worker.submit(()->{try{JsonElement result=job.call();Platform.runLater(()->{finish();try{done.accept(result);}catch(Exception e){error(e);}});}catch(Exception e){Platform.runLater(()->{finish();error(e);});}});
    }
    private void finish(){busy=false;progress.setVisible(false);tabs.setDisable(false);loginPane.setDisable(false);logout.setDisable(false);message.setText("Ready");}
    private void error(Exception e){message.setText("Action failed: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
    @FXML private void signIn(){JsonObject body=new JsonObject();body.addProperty("username",username.getText());body.addProperty("password",password.getText());task(()->api.call("login",body),r->{role=r.getAsJsonObject().get("role").getAsString();identity.setText(username.getText()+" · "+role+" | 12313659");password.clear();loginPane.setVisible(false);loginPane.setManaged(false);tabs.setVisible(true);tabs.setManaged(true);logout.setVisible(true);logout.setManaged(true);buildTabs();refresh();});}
    @FXML private void signOut(){task(()->api.call("logout",new JsonObject()),r->reset());}
    private void reset(){tabs.getTabs().clear();tables.clear();tabs.setVisible(false);tabs.setManaged(false);loginPane.setVisible(true);loginPane.setManaged(true);logout.setVisible(false);logout.setManaged(false);identity.setText("Thirthala Thrinadh · 12313659");}
    public void close(){worker.shutdownNow();}
    private Button button(String label,Runnable action){Button b=new Button(label);b.setOnAction(e->{try{action.run();}catch(Exception ex){error(ex);}});return b;}
    private void tab(String title,Node node){ScrollPane scroll=new ScrollPane(node);scroll.setFitToWidth(true);tabs.getTabs().add(new Tab(title,scroll));}
    private VBox box(Node...nodes){VBox v=new VBox(12,nodes);v.setPadding(new Insets(20));return v;}
    private TableView<JsonObject> table(String key,String...columns){
        TableView<JsonObject> t=new TableView<>();t.setPrefHeight(340);t.setPlaceholder(new Label("No records to display"));
        for(String name:columns){TableColumn<JsonObject,String> col=new TableColumn<>(name.replace('_',' '));col.setCellValueFactory(c->new SimpleStringProperty(value(c.getValue(),name)));col.setPrefWidth(name.equals("description")||name.equals("message")?280:140);t.getColumns().add(col);}tables.put(key,t);return t;
    }
    private String value(JsonObject j,String key){return j.has(key)&&!j.get(key).isJsonNull()?j.get(key).getAsString():"";}
    private JsonObject selected(String key){JsonObject j=tables.get(key).getSelectionModel().getSelectedItem();if(j==null)throw new IllegalArgumentException("Select a row in the "+key+" table first");return j;}
    private JsonObject id(String key){JsonObject j=new JsonObject();j.add("id",selected(key).get("id"));if(selected(key).has("version"))j.add("version",selected(key).get("version"));return j;}
    private final class Form {
        final GridPane grid=new GridPane();final Map<String,Control> fields=new LinkedHashMap<>();
        Form(){grid.setHgap(16);grid.setVgap(10);}
        Form field(String key,String label,String initial){TextField f=key.equals("password")?new PasswordField():new TextField();f.setText(initial);add(key,label,f);return this;}
        Form choice(String key,String label,List<String> values){ComboBox<String> c=new ComboBox<>(FXCollections.observableArrayList(values));c.getSelectionModel().selectFirst();c.setMaxWidth(Double.MAX_VALUE);add(key,label,c);return this;}
        Form area(String key,String label){TextArea area=new TextArea();area.setPrefRowCount(3);area.setWrapText(true);add(key,label,area);return this;}
        void add(String key,String label,Control control){int n=fields.size();fields.put(key,control);grid.add(new Label(label),0,n);grid.add(control,1,n);GridPane.setHgrow(control,Priority.ALWAYS);}
        JsonObject json(){JsonObject j=new JsonObject();fields.forEach((k,v)->j.addProperty(k,v instanceof TextInputControl t?t.getText():String.valueOf(((ComboBox<?>)v).getValue())));return j;}
    }
    private void mutate(String action,JsonObject data){task(()->api.call(action,data),r->{String msg=r.isJsonObject()&&r.getAsJsonObject().has("message")?r.getAsJsonObject().get("message").getAsString():"Saved successfully";refresh(msg);});}
    private void refresh(){refresh("Records refreshed");}
    private void refresh(String success){task(()->{JsonObject result=new JsonObject();for(String key:tables.keySet())result.add(key,api.call(key,new JsonObject()));return result;},r->{tables.forEach((k,t)->{var old=t.getSelectionModel().getSelectedItem();String selectedId=old==null?"":value(old,"id");List<JsonObject> data=new ArrayList<>();for(JsonElement element:r.getAsJsonObject().getAsJsonArray(k))data.add(element.getAsJsonObject());if(k.equals("incidents")){allIncidents.clear();allIncidents.addAll(data);data=data.stream().filter(j->j.toString().toLowerCase(Locale.ROOT).contains(filterText)).toList();}t.setItems(FXCollections.observableArrayList(data));data.stream().filter(j->value(j,"id").equals(selectedId)).findFirst().ifPresent(j->t.getSelectionModel().select(j));});var rows=tables.get("incidents").getItems();summary.setText(rows.size()+" visible incidents  •  "+rows.stream().filter(j->!value(j,"status").equals("Closed")).count()+" active  •  "+rows.stream().filter(j->value(j,"priority").equals("CRITICAL")).count()+" critical");message.setText(success);});}
    private void buildTabs(){
        tables.clear();tabs.getTabs().clear();summary=new Label();summary.getStyleClass().add("section");
        TableView<JsonObject> incidents=table("incidents","id","type","location","occurred_at","severity","population","priority","status","primary_department","supporting_department","resource","description","version");
        TextField search=new TextField();search.setPromptText("Search currently loaded incidents by text");
        search.textProperty().addListener((o,old,s)->{filterText=s.toLowerCase(Locale.ROOT);incidents.setItems(FXCollections.observableArrayList(allIncidents.stream().filter(j->j.toString().toLowerCase(Locale.ROOT).contains(filterText)).toList()));});
        VBox incidentBox=box(summary,new HBox(10,button("Refresh all",this::refresh),button("Export selected report",this::exportReport),search),incidents,new Label("Select an incident above before assessment, allocation or status changes."));
        if(coordinator()){
            Form assess=new Form().choice("severity","Assessed severity",Rules.SEVERITIES).field("population","Affected population","0");
            Form assign=new Form().choice("primary_department","Primary department",Rules.DEPARTMENTS).choice("supporting_department","Supporting department",withBlank(Rules.DEPARTMENTS)).field("resource","Resources / personnel","");
            incidents.getSelectionModel().selectedItemProperty().addListener((o,old,row)->{if(row!=null){((ComboBox<String>)assess.fields.get("severity")).setValue(value(row,"severity"));((TextField)assess.fields.get("population")).setText(value(row,"population"));}});
            incidentBox.getChildren().addAll(new TitledPane("Assess selected incident",box(assess.grid,button("Save assessment",()->mutate("assess",merge(assess.json(),id("incidents")))))),new TitledPane("Coordinate departments and resources",box(assign.grid,button("Allocate response",()->mutate("assign",merge(assign.json(),id("incidents")))))));
        }
        if(coordinator()||"RESPONDER".equals(role)){
            Form status=new Form().choice("status","Next status",coordinator()?List.of("In Progress","Resolved","Closed"):List.of("In Progress","Resolved"));incidentBox.getChildren().add(new TitledPane("Response progress",box(status.grid,button("Update selected incident",()->mutate("status",merge(status.json(),id("incidents")))))));
        }
        tab("Incidents",incidentBox);
        if(coordinator()||"REPORTER".equals(role)){
            Form report=new Form().choice("type","Disaster type",Rules.TYPES).field("location","Location","").field("occurred_at","Date / time (YYYY-MM-DDTHH:MM)",LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString()).choice("severity","Observed severity",Rules.SEVERITIES).field("population","Affected population","0").area("description","Incident description");
            tab("Report disaster",box(new Label("Report a disaster"),report.grid,button("Submit incident",()->mutate("report",report.json()))));
        }
        if(coordinator()||"RESPONDER".equals(role)){
            TableView<JsonObject> shelters=table("shelters","id","name","location","capacity","occupied");TableView<JsonObject> evacuations=table("evacuations","id","incident_id","shelter_id","shelter","people","active","created_at");VBox shelterBox=box(new Label("Shelter availability and evacuation allocations"),shelters,evacuations);
            if(coordinator()){
                Form shelter=new Form().field("name","Shelter name","").field("location","Location","").field("capacity","Capacity","");
                Form evacuate=new Form().field("incident_id","Incident ID","").field("people","Number of people","");
                shelterBox.getChildren().addAll(new TitledPane("Create shelter",box(shelter.grid,button("Add shelter",()->mutate("shelter-create",shelter.json())))),new TitledPane("Allocate to selected shelter",box(evacuate.grid,button("Allocate evacuation",()->{JsonObject j=evacuate.json();j.add("shelter_id",selected("shelters").get("id"));mutate("evacuate",j);}))),button("Release selected evacuation",()->mutate("release",id("evacuations"))));
            }tab("Evacuation shelters",shelterBox);
        }
        TableView<JsonObject> alerts=table("alerts","id","incident_id","message","created_at","acknowledged","acknowledgements");VBox alertBox=box(new Label("Incident alerts · use Refresh all to receive new alerts"),alerts,button("Acknowledge selected alert",()->mutate("acknowledge",id("alerts"))));
        if(coordinator()){Form alert=new Form().field("incident_id","Incident ID","").area("message","Alert message");alertBox.getChildren().add(new TitledPane("Publish an incident alert",box(alert.grid,button("Publish alert",()->mutate("alert-create",alert.json())))));}tab("Alerts",alertBox);
        if("ADMIN".equals(role)){
            Form user=new Form().field("username","Username","").field("password","Password (12+ characters)","").choice("role","Role",List.of("REPORTER","RESPONDER","COORDINATOR","ADMIN")).choice("department","Department (responders only)",Rules.DEPARTMENTS);
            tab("User accounts",box(table("users","id","username","role","department","active"),button("Enable / disable selected user",()->mutate("user-toggle",id("users"))),user.grid,button("Create account",()->mutate("user-create",user.json()))));
            tab("Audit trail",box(new Label("Authenticated timestamps and integrity checks; refresh to revalidate records."),table("audit","id","actor","action","details","event_time","integrity")));
        }
        if(coordinator())tab("Import initial data",box(new Label("Import JSON through the server. The full batch is atomic; legacy IDs prevent duplicates."),new Label("The supplied starter has no persisted records. data/sample-incidents.json is demonstration data."),button("Choose JSON file and import",()->{FileChooser picker=new FileChooser();picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON data","*.json"));var file=picker.showOpenDialog(tabs.getScene().getWindow());if(file!=null)task(()->api.call("import",JsonParser.parseString(Files.readString(file.toPath())).getAsJsonObject()),r->refresh(r.getAsJsonObject().get("message").getAsString()));})));
    }
    private void exportReport(){
        JsonObject row=selected("incidents");FileChooser picker=new FileChooser();picker.setInitialFileName("Incident_"+value(row,"id")+"_Report.txt");var file=picker.showSaveDialog(tabs.getScene().getWindow());if(file==null)return;
        StringBuilder text=new StringBuilder("DISASTER RESPONSE SYSTEM - INCIDENT REPORT\n\n");row.entrySet().forEach(e->text.append(e.getKey()).append(": ").append(value(row,e.getKey())).append("\n"));
        task(()->{Files.writeString(file.toPath(),text.toString());return new JsonObject();},r->message.setText("Report exported to "+file.getName()));
    }
    private List<String> withBlank(List<String> source){List<String> list=new ArrayList<>();list.add("");list.addAll(source);return list;}
    private JsonObject merge(JsonObject a,JsonObject b){b.entrySet().forEach(e->a.add(e.getKey(),e.getValue()));return a;}
}
