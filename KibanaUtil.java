import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.json.JSONArray;
import org.json.JSONObject;

@Log4j2
public class KibanaUtil {

  private static void postToKibana(File file, String Date) throws Exception {

    log.info(" post To Kibana ");

    ObjectMapper mapper = new ObjectMapper();
    String json = FileUtils.readFile(file);

    String index = "{\"index\":{\"_index\":\"automationreportrecent\",\"_type\":\"simple\"}}\n";
    StringBuilder header = new StringBuilder();

    String index1 = "{\"index\":{\"_index\":\"automationreport\",\"_type\":\"simple\"}}\n";
    StringBuilder header1 = new StringBuilder();

    String index2 = "{\"index\":{\"_index\":\"automationscenarioreport\",\"_type\":\"simple\"}}\n";
    StringBuilder header2 = new StringBuilder();

    String index3 = "{\"index\":{\"_index\":\"automationsuitereport\",\"_type\":\"simple\"}}\n";
    StringBuilder header3 = new StringBuilder();

    StringBuilder header4 = new StringBuilder();
    String index4 = "{\"index\":{\"_index\":\"cucumberjson\",\"_type\":\"simple\"}}\n";
    String data = "{\"data\":" + new JSONArray(json).toString() + "}";

    List<Map> features = JsonPath.parse(json).read("$[*]");
    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Date date = new Date();
    // String Date = dateFormat.format(date).toString();
    String ProjectName = file.getParentFile().getParentFile().getName();

    for (Map feature : features) {

      JSONObject feat = new JSONObject(feature);
      log.info(String.valueOf(feat));

      AnalysisObjectNew ao = new AnalysisObjectNew();
      AnalysisObject ao1 = new AnalysisObject();
      AnalysisObjectSuite suiteObject = new AnalysisObjectSuite();

      ao.setProjectName(ProjectName);
      ao1.setProjectName(ProjectName);
      suiteObject.setProjectName(ProjectName);

      ao.Date = Date;
      ao1.Date = Date;
      suiteObject.Date = Date;

      String featureName = JsonPath.parse(feat.toString()).read("$.name");
      ao.FeatureName = featureName;
      ao1.FeatureName = featureName;
      suiteObject.FeatureName = featureName;

      List<Map> Scenarios = JsonPath.parse(feat.toString()).read("$.elements[*]");
      for (Map Scenario : Scenarios) {
        JSONObject sce = new JSONObject(Scenario);
        String scenarioName = JsonPath.parse(sce.toString()).read("$.name");

        ao.setScenario(scenarioName);
        ao1.setScenario(scenarioName);
        suiteObject.setScenario(scenarioName);

        List status = JsonPath.parse(sce.toString()).read("steps[*].result.status");
        suiteObject.setPasscount(Collections.frequency(status, "passed"));
        suiteObject.setSkippedcount(Collections.frequency(status, "skipped"));
        suiteObject.setFailcount(Collections.frequency(status, "failed"));
        suiteObject.setTotalcount(status.size());

        ao1.count = 1;
        if (status.contains("failed")) {
          ao1.status = "failed";
        } else if (status.contains("skipped")) {
          ao1.status = "skipped";
        } else if (status.contains("passed")) {
          ao1.status = "passed";
        }

        List<String> Tags = JsonPath.parse(sce.toString()).read("$..tags[*].name");
        ArrayList<String> tags = new ArrayList<String>();
        for (String tag : Tags) {
          tags.add(tag.replace("@", "").toUpperCase());
        }
        ao1.setTags(tags);
        suiteObject.setTags(tags);

        List<Map> steps = JsonPath.parse(sce.toString()).read("$.steps[*]");
        //List<String> jiraSteps= new ArrayList<String>();
        for (Map Step : steps) {
          JSONObject step_s = new JSONObject(Step);
          String StepsName = JsonPath.parse(step_s.toString()).read("$.name");
          String Stepstatus = JsonPath.parse(step_s.toString()).read("$.result.status");
          ao.setStatus(Stepstatus);
          ao.setCount(1);
          ao.setSteps(StepsName);
          String error = "";
          try {
            error = JsonPath.parse(step_s.toString()).read("$.result.error_message");
            //    if(ao1.status.equals("failed"))
            //                {
            //                    JiraUtil.createIssue(ao1.getProjectName() + ":" + ao1.getScenario(),
            //                    jiraSteps.toString());
            //                }      ao.setError(error);
          } catch (Exception e) {
          }

          header1.append(index1 + mapper.writeValueAsString(ao) + "\n");
          //  jiraSteps.add(StepsName+" : "+Stepstatus+" : " +error);
        }
        //
        header2.append(index2 + mapper.writeValueAsString(ao1) + "\n");
        header.append(index + mapper.writeValueAsString(ao1) + "\n");
        header3.append(index3 + mapper.writeValueAsString(suiteObject) + "\n");
        //header4.append(index4 + data + "\n");
      }
    }
    header4.append(index4 + data + "\n");
    KibanaUtility.postindex(header.toString());
    KibanaUtility.postindex(header1.toString());
    KibanaUtility.postindex(header2.toString());
    KibanaUtility.postindex(header3.toString());
    KibanaUtility.postindex(header4.toString());
    log.info(" post To Kibana end ");
  }

  public static void postAllFile(String path, String Date) throws Exception {
    File directory = new File(path);
    log.info(directory.getAbsolutePath());
    File[] fList = directory.listFiles();
    if (fList != null) {
      for (File file : fList) {
        if (file.isFile()) {
          if (file.getName().equalsIgnoreCase("cucumber.json")) {
            try {
              postToKibana(file, Date);

            } catch (Exception ex) {
              log.error("Fail to post data to kibana: " + ex.getMessage());
            }
          }
        } else if (file.isDirectory()) {
          postAllFile(file.getAbsolutePath(), Date);
        }
      }
    }
  }
}
